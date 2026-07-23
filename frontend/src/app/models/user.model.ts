export enum UserRole {
  ADMIN = 'ADMIN',
  MANAGER = 'MANAGER',
  WAREHOUSE_STAFF = 'WAREHOUSE_STAFF',
  VIEWER = 'VIEWER'
}

export enum UserStatus {
  ACTIVE = 'ACTIVE',
  INACTIVE = 'INACTIVE'
}

export interface User {
  id: string;
  tenantId: string;
  email: string;
  firstName: string;
  lastName: string;
  role: UserRole;
  status: UserStatus;
  createdAt: string;
  updatedAt: string;
}

export interface CreateUserRequest {
  email: string;
  password: string;
  firstName: string;
  lastName: string;
  role: UserRole;
}

export interface UpdateUserRequest {
  firstName?: string;
  lastName?: string;
  role?: UserRole;
}

export interface UpdateUserStatusRequest {
  status: UserStatus;
}

export interface JwtPayload {
  sub: string;
  tenantId: string;
  tenantSlug: string;
  roles: UserRole[];
  iat: number;
  exp: number;
}
