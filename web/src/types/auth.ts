export const Permission = {
  DATA_QUERY: 'DATA_QUERY',
  SMART_REPORT: 'SMART_REPORT',
  AI_TRAINING: 'AI_TRAINING',
} as const

export type PermissionCode = (typeof Permission)[keyof typeof Permission]

export interface UserProfile {
  id: number
  username: string
  displayName: string
  roles: string[]
  permissions: PermissionCode[]
}

export interface LoginResponse {
  accessToken: string
  tokenType: 'Bearer'
  expiresAt: string
  user: UserProfile
}
