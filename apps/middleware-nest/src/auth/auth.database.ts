import { Injectable, OnModuleDestroy } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { Pool, PoolClient, QueryResult } from 'pg';
import { AuthUser, StoredRefreshToken, UserStatus } from './auth.types';

type Queryable = Pick<Pool | PoolClient, 'query'>;

interface UserRow {
  id: string;
  username: string;
  password_hash: string;
  full_name: string | null;
  status: UserStatus;
  token_version: number;
}

interface AuthorityRow {
  role_code: string | null;
  permission_code: string | null;
}

interface RefreshTokenRow {
  user_id: string;
  expires_at: Date;
  revoked_at: Date | null;
}

@Injectable()
export class AuthDatabaseService implements OnModuleDestroy {
  private readonly pool: Pool;

  constructor(configService: ConfigService) {
    this.pool = new Pool({
      host: configService.get<string>('DB_HOST') ?? 'localhost',
      port: Number(configService.get<string | number>('DB_PORT') ?? 5432),
      database: configService.get<string>('DB_NAME') ?? 'equeue',
      user: configService.get<string>('DB_USER') ?? 'equeue',
      password: configService.get<string>('DB_PASSWORD') ?? 'equeue',
    });
  }

  async onModuleDestroy(): Promise<void> {
    await this.pool.end();
  }

  findUserByUsername(username: string, queryable: Queryable = this.pool): Promise<AuthUser | null> {
    return this.findUser(
      queryable,
      'lower(u.username) = lower($1)',
      [username],
    );
  }

  findUserById(id: string, queryable: Queryable = this.pool): Promise<AuthUser | null> {
    return this.findUser(queryable, 'u.id = $1', [id]);
  }

  async insertRefreshToken(
    userId: string,
    tokenHash: string,
    expiresAt: Date,
    createdByIp: string | null,
    queryable: Queryable = this.pool,
  ): Promise<void> {
    await queryable.query(
      `
      INSERT INTO refresh_tokens (user_id, token_hash, expires_at, created_by_ip)
      VALUES ($1, $2, $3, $4)
      `,
      [userId, tokenHash, expiresAt, createdByIp],
    );
  }

  async findRefreshTokenForUpdate(tokenHash: string, client: PoolClient): Promise<StoredRefreshToken | null> {
    const result = await client.query<RefreshTokenRow>(
      `
      SELECT user_id, expires_at, revoked_at
      FROM refresh_tokens
      WHERE token_hash = $1
      FOR UPDATE
      `,
      [tokenHash],
    );
    const row = result.rows[0];
    return row
      ? { userId: row.user_id, expiresAt: row.expires_at, revokedAt: row.revoked_at }
      : null;
  }

  async replaceRefreshToken(
    oldTokenHash: string,
    replacementHash: string,
    client: PoolClient,
  ): Promise<void> {
    await client.query(
      `
      UPDATE refresh_tokens
      SET revoked_at = now(), replaced_by_hash = $2
      WHERE token_hash = $1
      `,
      [oldTokenHash, replacementHash],
    );
  }

  async revokeRefreshToken(tokenHash: string): Promise<void> {
    await this.pool.query(
      `
      UPDATE refresh_tokens
      SET revoked_at = COALESCE(revoked_at, now())
      WHERE token_hash = $1
      `,
      [tokenHash],
    );
  }

  async writeLoginAudit(
    username: string,
    userId: string | null,
    success: boolean,
    reason: string | null,
    ip: string | null,
    userAgent: string | null,
  ): Promise<void> {
    await this.pool.query(
      `
      INSERT INTO login_audit_logs (username, user_id, success, reason, ip, user_agent)
      VALUES ($1, $2, $3, $4, $5, $6)
      `,
      [username, userId, success, reason, ip, userAgent],
    );
  }

  async withTransaction<T>(callback: (client: PoolClient) => Promise<T>): Promise<T> {
    const client = await this.pool.connect();
    try {
      await client.query('BEGIN');
      const result = await callback(client);
      await client.query('COMMIT');
      return result;
    } catch (error) {
      await client.query('ROLLBACK');
      throw error;
    } finally {
      client.release();
    }
  }

  private async findUser(
    queryable: Queryable,
    where: string,
    values: unknown[],
  ): Promise<AuthUser | null> {
    const result = await queryable.query<UserRow>(
      `
      SELECT
        u.id::text,
        u.username,
        u.password_hash,
        u.full_name,
        u.status,
        u.token_version
      FROM users u
      WHERE ${where}
      `,
      values,
    );
    const row = result.rows[0];
    if (!row) {
      return null;
    }
    const authorities = await this.authorities(queryable, row.id);
    return {
      id: row.id,
      username: row.username,
      passwordHash: row.password_hash,
      fullName: row.full_name,
      status: row.status,
      tokenVersion: row.token_version,
      roles: authorities.roles,
      permissions: authorities.permissions,
    };
  }

  private async authorities(
    queryable: Queryable,
    userId: string,
  ): Promise<{ roles: string[]; permissions: string[] }> {
    const result: QueryResult<AuthorityRow> = await queryable.query(
      `
      SELECT r.code AS role_code, p.code AS permission_code
      FROM user_roles ur
      JOIN roles r ON r.id = ur.role_id
      LEFT JOIN role_permissions rp ON rp.role_id = r.id
      LEFT JOIN permissions p ON p.id = rp.permission_id
      WHERE ur.user_id = $1
      ORDER BY r.code, p.code
      `,
      [userId],
    );
    return {
      roles: [...new Set(result.rows.map((row) => row.role_code).filter(isPresent))],
      permissions: [...new Set(result.rows.map((row) => row.permission_code).filter(isPresent))],
    };
  }
}

function isPresent(value: string | null): value is string {
  return value !== null;
}
