import type { ApiEnvelope, LoginResult } from '../types'
import { http } from './base'

export async function login(username: string, password: string) {
  const response = await http.post<ApiEnvelope<LoginResult>>('/auth/login', { username, password })
  return response.data.data
}


export async function requestRegisterCode(email: string) {
  await http.post<ApiEnvelope<null>>('/auth/register-code', { email })
}


export async function register(payload: {
  username: string
  email: string
  displayName?: string
  password: string
  code: string
}) {
  const response = await http.post<ApiEnvelope<LoginResult>>('/auth/register', payload)
  return response.data.data
}


export async function requestResetPasswordCode(email: string) {
  await http.post<ApiEnvelope<null>>('/auth/reset-password-code', { email })
}


export async function resetPassword(payload: {
  email: string
  code: string
  newPassword: string
}) {
  await http.post<ApiEnvelope<null>>('/auth/reset-password', payload)
}
