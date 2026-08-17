import axios, { AxiosError } from 'axios'
import { clearAuthStorage, getAccessToken } from '../utils/auth-storage'

export interface ApiErrorBody {
  code?: string
  message?: string
  traceId?: string
}

export const http = axios.create({
  baseURL: '/api/v1',
  timeout: 120_000,
})

http.interceptors.request.use((config) => {
  const token = getAccessToken()
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

http.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ApiErrorBody>) => {
    if (error.response?.status === 401 && !error.config?.url?.endsWith('/auth/login')) {
      clearAuthStorage()
      const redirect = encodeURIComponent(`${location.pathname}${location.search}`)
      location.assign(`/login?redirect=${redirect}`)
    }
    return Promise.reject(error)
  },
)

export function apiErrorMessage(error: unknown, fallback = '请求失败，请稍后重试'): string {
  if (axios.isAxiosError<ApiErrorBody>(error)) {
    return error.response?.data?.message || error.message || fallback
  }
  return error instanceof Error ? error.message : fallback
}
