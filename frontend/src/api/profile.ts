import type { ApiEnvelope, User, UserStats, PublicProfile } from '../types'
import { http } from './base'

export async function fetchProfile() {
  const response = await http.get<ApiEnvelope<User>>('/profile')
  return response.data.data
}


export async function fetchUserStats() {
  const response = await http.get<ApiEnvelope<UserStats>>('/profile/stats')
  return response.data.data
}


export async function updateProfile(payload: {
  username?: string
  displayName: string
  studentNo?: string
  major?: string
}) {
  const response = await http.put<ApiEnvelope<User>>('/profile', payload)
  return response.data.data
}


export async function fetchPublicProfile(userId: number) {
  const response = await http.get<ApiEnvelope<PublicProfile>>(`/profile/${userId}/public`)
  return response.data.data
}


export async function requestEmailChangeCode(newEmail: string) {
  await http.post<ApiEnvelope<null>>('/profile/email-change-code', { newEmail })
}


export async function changeEmail(payload: { newEmail: string; code: string }) {
  const response = await http.put<ApiEnvelope<User>>('/profile/email', payload)
  return response.data.data
}


export async function uploadAvatar(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  const response = await http.post<ApiEnvelope<User>>('/profile/avatar', formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
  return response.data.data
}


export async function requestPasswordChangeCode() {
  await http.post<ApiEnvelope<null>>('/profile/password-code')
}


export async function changePassword(payload: { code: string; newPassword: string }) {
  await http.put<ApiEnvelope<null>>('/profile/password', payload)
}
