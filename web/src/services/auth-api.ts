import type { LoginResponse, UserProfile } from '../types/auth'
import { http } from './http'

export const authApi = {
  login: (username: string, password: string) =>
    http.post<LoginResponse>('/auth/login', { username, password }),
  me: () => http.get<UserProfile>('/auth/me'),
  logout: () => http.post<{ success: boolean }>('/auth/logout'),
}
