import type { AdminDashboard, AdminProblemSummary, AdminSubmissionSummary, ApiEnvelope, ProblemTag, Submission, AdminProblemDetail, SmtpSettings, SandboxSettings, SystemSettings, User, Contest, AdminContestSummary, AdminContestDetail, ProblemPackagePreview } from '../types'
import { http } from './base'

export interface CreateProblemPayload {
  slug: string
  title: string
  description: string
  timeLimitMs: number
  memoryLimitKb: number
  difficulty: string
  tags?: string
  visible: boolean
  testCases: Array<{
    uploadToken?: string
    name?: string
    caseName?: string
    inputFile: string
    outputFile: string
    inputSize?: number
    outputSize?: number
    score: number
  }>
}


export async function createProblem(payload: CreateProblemPayload, autolinkTrainingId?: number) {
  const url = autolinkTrainingId ? `/admin/problems?autolinkTrainingId=${autolinkTrainingId}` : '/admin/problems'
  const response = await http.post<ApiEnvelope<AdminProblemDetail>>(url, payload)
  return response.data.data
}


export async function fetchAdminDashboard() {
  const response = await http.get<ApiEnvelope<AdminDashboard>>('/admin/dashboard')
  return response.data.data
}


export async function fetchAdminProblems() {
  const response = await http.get<ApiEnvelope<AdminProblemSummary[]>>('/admin/problems')
  return response.data.data
}


export async function setProblemVisibility(id: number, visible: boolean) {
  const response = await http.patch<ApiEnvelope<AdminProblemSummary>>(`/admin/problems/${id}/visibility`, { visible })
  return response.data.data
}


export async function fetchAdminSubmissions(limit = 100) {
  const response = await http.get<ApiEnvelope<AdminSubmissionSummary[]>>('/admin/submissions', { params: { limit } })
  return response.data.data
}


export async function rejudgeSubmission(id: number) {
  const response = await http.post<ApiEnvelope<Submission>>(`/admin/submissions/${id}/rejudge`)
  return response.data.data
}


export async function requeueUnfinishedSubmissions() {
  const response = await http.post<ApiEnvelope<{ queued: number }>>('/admin/submissions/requeue-unfinished')
  return response.data.data
}


export async function fetchAdminProblem(id: number) {
  const response = await http.get<ApiEnvelope<AdminProblemDetail>>(`/admin/problems/${id}`)
  return response.data.data
}


export async function fetchTestCaseFileContent(problemId: number, filename: string): Promise<string> {
  const response = await http.get<string>(`/admin/problems/${problemId}/cases/${filename}`, {
    responseType: 'text' as any
  })
  return response.data
}


export async function updateProblem(id: number, payload: CreateProblemPayload) {
  const response = await http.put<ApiEnvelope<AdminProblemDetail>>(`/admin/problems/${id}`, payload)
  return response.data.data
}


export async function deleteProblem(id: number) {
  await http.delete<ApiEnvelope<null>>(`/admin/problems/${id}`)
}


export async function importTestCaseFiles(files: File[]) {
  const formData = new FormData()
  files.forEach((file) => formData.append('files', file))
  const response = await http.post<ApiEnvelope<Array<{
    uploadToken: string
    name?: string
    inputFile: string
    outputFile: string
    inputSize: number
    outputSize: number
    score: number
  }>>>(
    '/admin/problems/import-files',
    formData,
    {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    }
  )
  return response.data.data
}


export async function importProblemPackage(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  const response = await http.post<ApiEnvelope<AdminProblemDetail>>('/admin/problems/import-package', formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
  return response.data.data
}


export async function previewProblemPackage(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  const response = await http.post<ApiEnvelope<ProblemPackagePreview>>('/admin/problems/import-package/preview', formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
  return response.data.data
}


export const problemPackageExampleUrl = '/api/admin/problems/example-package'


export async function fetchSmtpSettings() {
  const response = await http.get<ApiEnvelope<SmtpSettings>>('/admin/settings/smtp')
  return response.data.data
}


export async function updateSmtpSettings(payload: Partial<SmtpSettings> & { password?: string }) {
  const response = await http.put<ApiEnvelope<SmtpSettings>>('/admin/settings/smtp', payload)
  return response.data.data
}


export async function fetchSandboxSettings() {
  const response = await http.get<ApiEnvelope<SandboxSettings>>('/admin/settings/sandbox')
  return response.data.data
}


export async function updateSandboxSettings(payload: Partial<SandboxSettings>) {
  const response = await http.put<ApiEnvelope<SandboxSettings>>('/admin/settings/sandbox', payload)
  return response.data.data
}


export async function fetchSystemSettings() {
  const response = await http.get<ApiEnvelope<SystemSettings>>('/admin/settings/system')
  return response.data.data
}


export async function updateSystemSettings(payload: Partial<SystemSettings>) {
  const response = await http.put<ApiEnvelope<SystemSettings>>('/admin/settings/system', payload)
  return response.data.data
}


export async function fetchProblemTags() {
  const response = await http.get<ApiEnvelope<ProblemTag[]>>('/admin/tags')
  return response.data.data
}


export async function createProblemTag(payload: { name: string; color?: string }) {
  const response = await http.post<ApiEnvelope<ProblemTag>>('/admin/tags', payload)
  return response.data.data
}


export async function updateProblemTag(id: number, payload: { name: string; color?: string }) {
  const response = await http.put<ApiEnvelope<ProblemTag>>(`/admin/tags/${id}`, payload)
  return response.data.data
}


export async function deleteProblemTag(id: number) {
  await http.delete<ApiEnvelope<null>>(`/admin/tags/${id}`)
}

// ================= CONTEST APIS =================


export async function fetchAdminContests() {
  const response = await http.get<ApiEnvelope<AdminContestSummary[]>>('/admin/contests')
  return response.data.data
}


export interface CreateContestPayload {
  title: string
  description?: string
  startTime: string
  endTime: string
  visible: boolean
  type?: 'ACM' | 'OI'
  freezeDurationMinutes?: number
  problemIds: number[]
}


export async function createContest(payload: CreateContestPayload) {
  const response = await http.post<ApiEnvelope<Contest>>('/admin/contests', payload)
  return response.data.data
}


export async function fetchAdminContest(id: number) {
  const response = await http.get<ApiEnvelope<AdminContestDetail>>(`/admin/contests/${id}`)
  return response.data.data
}


export async function updateContest(id: number, payload: CreateContestPayload) {
  const response = await http.put<ApiEnvelope<Contest>>(`/admin/contests/${id}`, payload)
  return response.data.data
}


export async function setContestVisibility(id: number, visible: boolean) {
  const response = await http.patch<ApiEnvelope<Contest>>(`/admin/contests/${id}/visibility`, { visible })
  return response.data.data
}


export async function deleteContest(id: number) {
  await http.delete<ApiEnvelope<null>>(`/admin/contests/${id}`)
}


export async function fetchAdminUsers(search?: string) {
  const response = await http.get<ApiEnvelope<User[]>>('/admin/users', {
    params: { search }
  })
  return response.data.data
}


export async function updateAdminUser(userId: number, payload: Partial<User> & { password?: string }) {
  const response = await http.put<ApiEnvelope<User>>(`/admin/users/${userId}`, payload)
  return response.data.data
}


export async function fetchLogToggle() {
  const response = await http.get<ApiEnvelope<{ enabled: boolean }>>('/admin/logs/toggle')
  return response.data.data
}


export async function updateLogToggle(enabled: boolean) {
  const response = await http.post<ApiEnvelope<void>>('/admin/logs/toggle', { enabled })
  return response.data.data
}


export async function downloadAdminLog(type: 'backend' | 'worker') {
  const response = await http.get(`/admin/logs/download?type=${type}`, {
    responseType: 'blob'
  })
  const blob = new Blob([response.data], { type: 'text/plain;charset=utf-8' })
  const url = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.setAttribute('download', `${type === 'worker' ? 'judge-worker' : 'backend'}.log`)
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  window.URL.revokeObjectURL(url)
}


export async function clearAdminLog(type: 'backend' | 'worker') {
  const response = await http.delete<ApiEnvelope<void>>(`/admin/logs/clear?type=${type}`)
  return response.data.data
}

// ================= DATA MANAGEMENT APIS =================


export async function importUsersBulk(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  const response = await http.post<ApiEnvelope<any>>('/admin/data/users/import', formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
  return response.data.data
}


export async function cleanupSubmissions(payload: {
  confirmationPhrase: string
  beforeDate?: string
  problemId?: number
  userId?: number
  contestId?: number
  onlyPractice?: boolean
  onlyContest?: boolean
  verdicts?: string[]
}) {
  const response = await http.post<ApiEnvelope<{ count: number }>>('/admin/data/submissions/cleanup', payload)
  return response.data.data
}


export async function fetchStorageStats() {
  const response = await http.get<ApiEnvelope<any>>('/admin/data/test-cases/stats')
  return response.data.data
}


export async function cleanupOrphanedCases() {
  const response = await http.delete<ApiEnvelope<{ count: number }>>('/admin/data/test-cases/orphaned')
  return response.data.data
}


export async function fetchBackupInfo() {
  const response = await http.get<ApiEnvelope<any>>('/admin/data/backup/info')
  return response.data.data
}


export async function fetchAdminDlq() {
  const response = await http.get<ApiEnvelope<{ size: number; items: string[] }>>('/admin/dlq')
  return response.data.data
}


export async function clearAdminDlq() {
  await http.post<ApiEnvelope<null>>('/admin/dlq/clear')
}


export async function requeueAdminDlq() {
  const response = await http.post<ApiEnvelope<{ requeued: number }>>('/admin/dlq/requeue')
  return response.data.data
}
