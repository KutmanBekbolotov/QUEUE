import { ApiProperty } from '@nestjs/swagger';
import { IsNotEmpty, IsString } from 'class-validator';
import { UserStatus } from './auth.types';

const USER_STATUSES: UserStatus[] = ['ACTIVE', 'BLOCKED', 'DISABLED'];

export class LoginRequestDto {
  @ApiProperty({ example: 'admin' })
  @IsString()
  @IsNotEmpty()
  username!: string;

  @ApiProperty({ example: 'ZAQ!@#$%tgb*', format: 'password' })
  @IsString()
  @IsNotEmpty()
  password!: string;
}

export class RefreshRequestDto {
  @ApiProperty({ example: 'refresh-token' })
  @IsString()
  @IsNotEmpty()
  refreshToken!: string;
}

export class LogoutRequestDto {
  @ApiProperty({ example: 'refresh-token' })
  @IsString()
  @IsNotEmpty()
  refreshToken!: string;
}

export class AuthResponseDto {
  @ApiProperty({ example: 'jwt-access-token' })
  accessToken!: string;

  @ApiProperty({ example: 'refresh-token' })
  refreshToken!: string;

  @ApiProperty({ example: 'Bearer' })
  tokenType!: string;

  @ApiProperty({ format: 'date-time', example: '2026-07-06T10:15:30Z' })
  expiresAt!: string;

  @ApiProperty({ type: [String], example: ['SUPER_ADMIN'] })
  roles!: string[];

  @ApiProperty({ type: [String], example: ['USER_READ', 'ROLE_READ'] })
  permissions!: string[];
}

export class MeResponseDto {
  @ApiProperty({ format: 'uuid', example: '0d259eb7-2505-4aa1-9d4d-fc8f418f6111' })
  id!: string;

  @ApiProperty({ example: 'admin' })
  username!: string;

  @ApiProperty({ example: 'Bootstrap Admin' })
  fullName!: string | null;

  @ApiProperty({ enum: USER_STATUSES, example: 'ACTIVE' })
  status!: UserStatus;

  @ApiProperty({ type: [String], example: ['SUPER_ADMIN'] })
  roles!: string[];

  @ApiProperty({ type: [String], example: ['USER_READ', 'ROLE_READ'] })
  permissions!: string[];
}
