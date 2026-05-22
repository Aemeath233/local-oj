import { defineStore } from 'pinia'
import { login as loginRequest, register as registerRequest } from '../api/http'
import type { User } from '../types'

interface AuthState {
  token: string
  user: User | null
}

export const useAuthStore = defineStore('auth', {
  state: (): AuthState => ({
    token: localStorage.getItem('localoj.token') ?? '',
    user: readUser()
  }),
  getters: {
    isLoggedIn: (state) => Boolean(state.token),
    isAdmin: (state) => state.user?.role === 'SUPER_ADMIN' || state.user?.role === 'ADMIN',
    isSuperAdmin: (state) => state.user?.role === 'SUPER_ADMIN'
  },
  actions: {
    async login(username: string, password: string) {
      const result = await loginRequest(username, password)
      this.applySession(result.token, result.user)
    },
    async register(payload: { username: string; email: string; displayName?: string; password: string; code: string }) {
      const result = await registerRequest(payload)
      this.applySession(result.token, result.user)
    },
    applySession(token: string, user: User) {
      this.token = token
      this.user = user
      localStorage.setItem('localoj.token', token)
      localStorage.setItem('localoj.user', JSON.stringify(user))
    },
    setUser(user: User) {
      this.user = user
      localStorage.setItem('localoj.user', JSON.stringify(user))
    },
    logout() {
      this.token = ''
      this.user = null
      localStorage.removeItem('localoj.token')
      localStorage.removeItem('localoj.user')
    }
  }
})

function readUser(): User | null {
  const raw = localStorage.getItem('localoj.user')
  if (!raw) {
    return null
  }
  try {
    return JSON.parse(raw) as User
  } catch {
    return null
  }
}
