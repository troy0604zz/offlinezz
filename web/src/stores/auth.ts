import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { authApi } from '../services/auth-api'
import type { PermissionCode, UserProfile } from '../types/auth'
import { clearAuthStorage, getAccessToken, getStoredUser, saveAuth, saveUser } from '../utils/auth-storage'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(getAccessToken())
  const user = ref<UserProfile | null>(getStoredUser())
  const initialized = ref(false)
  const authenticated = computed(() => Boolean(token.value && user.value))

  function hasPermission(permission?: PermissionCode): boolean {
    return !permission || Boolean(user.value?.permissions.includes(permission))
  }

  async function login(username: string, password: string): Promise<void> {
    const response = await authApi.login(username, password)
    token.value = response.data.accessToken
    user.value = response.data.user
    saveAuth(token.value, user.value)
    initialized.value = true
  }

  async function restoreSession(): Promise<void> {
    if (initialized.value) return
    initialized.value = true
    if (!token.value) return
    try {
      const response = await authApi.me()
      user.value = response.data
      saveUser(user.value)
    } catch {
      reset()
    }
  }

  async function logout(): Promise<void> {
    try {
      if (token.value) await authApi.logout()
    } finally {
      reset()
    }
  }

  function reset(): void {
    token.value = null
    user.value = null
    clearAuthStorage()
  }

  return { token, user, authenticated, initialized, hasPermission, login, restoreSession, logout, reset }
})
