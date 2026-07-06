import { Body, Controller, Get, HttpCode, Post, Req } from '@nestjs/common';
import { ApiBearerAuth, ApiBody, ApiOperation, ApiResponse, ApiTags } from '@nestjs/swagger';
import { Request } from 'express';
import {
  AuthResponseDto,
  LoginRequestDto,
  LogoutRequestDto,
  MeResponseDto,
  RefreshRequestDto,
} from './auth.dto';
import { AuthService } from './auth.service';

@Controller('/auth')
@ApiTags('Auth')
export class AuthController {
  constructor(private readonly authService: AuthService) {}

  @Post('/login')
  @HttpCode(200)
  @ApiOperation({ summary: 'Authenticate user and issue access and refresh tokens' })
  @ApiBody({ type: LoginRequestDto })
  @ApiResponse({ status: 200, type: AuthResponseDto })
  login(@Body() body: LoginRequestDto, @Req() request: Request): Promise<AuthResponseDto> {
    return this.authService.login(body, request);
  }

  @Post('/refresh')
  @HttpCode(200)
  @ApiOperation({ summary: 'Refresh access token' })
  @ApiBody({ type: RefreshRequestDto })
  @ApiResponse({ status: 200, type: AuthResponseDto })
  refresh(@Body() body: RefreshRequestDto, @Req() request: Request): Promise<AuthResponseDto> {
    return this.authService.refresh(body, request);
  }

  @Post('/logout')
  @HttpCode(204)
  @ApiOperation({ summary: 'Revoke refresh token' })
  @ApiBody({ type: LogoutRequestDto })
  @ApiResponse({ status: 204, description: 'Refresh token revoked' })
  logout(@Body() body: LogoutRequestDto): Promise<void> {
    return this.authService.logout(body);
  }

  @Get('/me')
  @ApiBearerAuth('bearer')
  @ApiOperation({ summary: 'Get current authenticated user' })
  @ApiResponse({ status: 200, type: MeResponseDto })
  me(@Req() request: Request): Promise<MeResponseDto> {
    return this.authService.me(request);
  }
}
