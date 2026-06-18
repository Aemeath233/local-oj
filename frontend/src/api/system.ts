import type { ApiEnvelope, Language } from '../types'
import { http } from './base'

export async function formatCode(language: Language, sourceCode: string) {
  const response = await http.post<ApiEnvelope<string>>('/code/format', { language, sourceCode })
  return response.data.data
}


export async function fetchSystemVersions() {
  const response = await http.get<ApiEnvelope<Record<string, number>>>('/system/versions')
  return response.data.data
}
