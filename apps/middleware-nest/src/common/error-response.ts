export interface ErrorResponse {
  timestamp: string;
  requestId: string;
  code: string;
  message: string;
  details: Record<string, unknown>;
}

