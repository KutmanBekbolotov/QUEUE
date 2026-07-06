export type UserStatus = 'ACTIVE' | 'BLOCKED' | 'DISABLED';

export interface AuthUser {
  id: string;
  username: string;
  passwordHash: string;
  fullName: string | null;
  status: UserStatus;
  tokenVersion: number;
  roles: string[];
  permissions: string[];
}

export interface StoredRefreshToken {
  userId: string;
  expiresAt: Date;
  revokedAt: Date | null;
}
