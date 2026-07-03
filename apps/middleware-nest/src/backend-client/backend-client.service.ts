import { HttpService } from '@nestjs/axios';
import { HttpException, Injectable } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { AxiosError, AxiosRequestConfig, AxiosResponse } from 'axios';
import { Request } from 'express';
import { firstValueFrom } from 'rxjs';
import { correlationId, headerValue, requestId } from '../common/request-context';

@Injectable()
export class BackendClientService {
  private readonly baseUrl: string;
  private readonly backendIntegrationKey: string;

  constructor(
    private readonly httpService: HttpService,
    configService: ConfigService,
  ) {
    this.baseUrl = configService.get<string>('BACKEND_BASE_URL') ?? 'http://localhost:8080';
    this.backendIntegrationKey = configService.get<string>('BACKEND_INTEGRATION_KEY') ?? 'dev-backend-integration-key-change-me';
  }

  get<T>(path: string, request: Request, integrationClient: string, params?: Record<string, unknown>): Promise<T> {
    return this.send<T>({ method: 'GET', url: path, params }, request, integrationClient);
  }

  post<T>(path: string, body: unknown, request: Request, integrationClient: string): Promise<T> {
    return this.send<T>({ method: 'POST', url: path, data: body }, request, integrationClient);
  }

  async stream(path: string, request: Request, integrationClient: string, params?: Record<string, unknown>): Promise<AxiosResponse<NodeJS.ReadableStream>> {
    try {
      return await this.requestWithOptionalRetry<NodeJS.ReadableStream>(
        { method: 'GET', url: path, params, responseType: 'stream' },
        request,
        integrationClient,
      );
    } catch (error) {
      throw this.toHttpException(error);
    }
  }

  delete<T>(path: string, request: Request, integrationClient: string): Promise<T> {
    return this.send<T>({ method: 'DELETE', url: path }, request, integrationClient);
  }

  private async send<T>(config: AxiosRequestConfig, request: Request, integrationClient: string): Promise<T> {
    try {
      const response = await this.requestWithOptionalRetry<T>(config, request, integrationClient);
      return response.data;
    } catch (error) {
      throw this.toHttpException(error);
    }
  }

  private async requestWithOptionalRetry<T>(config: AxiosRequestConfig, request: Request, integrationClient: string) {
    const requestConfig: AxiosRequestConfig = {
      ...config,
      baseURL: this.baseUrl,
      headers: this.headers(request, integrationClient),
      timeout: 10_000,
    };
    try {
      return await firstValueFrom(this.httpService.request<T>(requestConfig));
    } catch (error) {
      if (!this.canRetry(config, request, error)) {
        throw error;
      }
      return firstValueFrom(this.httpService.request<T>(requestConfig));
    }
  }

  private headers(request: Request, integrationClient: string): Record<string, string> {
    const headers: Record<string, string> = {
      'X-Request-Id': requestId(request),
      'X-Correlation-Id': correlationId(request),
      'X-Integration-Client': integrationClient,
      'X-Backend-Integration-Key': this.backendIntegrationKey,
    };

    const idempotencyKey = headerValue(request, 'idempotency-key');
    const externalRequestId = headerValue(request, 'x-external-request-id');
    if (idempotencyKey) {
      headers['Idempotency-Key'] = idempotencyKey;
    }
    if (externalRequestId) {
      headers['X-External-Request-Id'] = externalRequestId;
    }
    return headers;
  }

  private canRetry(config: AxiosRequestConfig, request: Request, error: unknown): boolean {
    const method = String(config.method ?? 'GET').toUpperCase();
    const axiosError = error as AxiosError;
    const status = axiosError.response?.status;
    const transient = !status || status === 502 || status === 503 || status === 504;
    if (!transient) {
      return false;
    }
    if (method === 'GET' || method === 'HEAD') {
      return true;
    }
    return Boolean(headerValue(request, 'idempotency-key') || headerValue(request, 'x-external-request-id'));
  }

  private toHttpException(error: unknown): HttpException {
    const axiosError = error as AxiosError;
    if (axiosError.response) {
      return new HttpException(axiosError.response.data ?? 'Backend request failed', axiosError.response.status);
    }
    return new HttpException(
      {
        code: 'BACKEND_UNAVAILABLE',
        message: 'Backend service is unavailable',
      },
      503,
    );
  }
}
