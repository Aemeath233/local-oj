import axios from 'axios'
import type {
  AdminDashboard,
  AdminProblemSummary,
  AdminSubmissionSummary,
  ApiEnvelope,
  Language,
  LeaderboardRow,
  LoginResult,
  ProblemDetail,
  ProblemStatus,
  ProblemSummary,
  ProblemTag,
  Submission,
  SubmissionDetail,
  SubmissionSummary,
  SelfTestResult,
  AdminProblemDetail,
  TestCase,
  SmtpSettings,
  SandboxSettings,
  SystemSettings,
  User,
  Contest,
  AdminContestSummary,
  ContestProblemDetail,
  ContestRegistrationStatus,
  ContestStandingsRow,
  AdminContestDetail,
  ProblemSolutionSummary,
  ProblemSolutionDetail,
  SystemLog,
  SystemLogLevel,
  ProblemPackagePreview,
  UserStats,
  PublicProfile
} from '../types'


export const http = axios.create({
  baseURL: '/api',
  timeout: 15000
})

export const SESSION_EXPIRED_EVENT = 'coderushoj:session-expired'

const publicAuthPaths = [
  '/auth/login',
  '/auth/register',
  '/auth/register-code',
  '/auth/reset-password-code',
  '/auth/reset-password'
]

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('coderushoj.token')
  if (token && !publicAuthPaths.includes(config.url || '')) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error.response?.status
    const url = error.config?.url || ''
    if (status === 401 && !publicAuthPaths.includes(url)) {
      localStorage.removeItem('coderushoj.token')
      localStorage.removeItem('coderushoj.user')
      window.dispatchEvent(new CustomEvent(SESSION_EXPIRED_EVENT, {
        detail: error.response?.data?.message || '登录已过期，请重新登录'
      }))
    }
    return Promise.reject(error)
  }
)

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

export async function requestSseTicket() {
  const response = await http.post<ApiEnvelope<{ ticket: string }>>('/submissions/sse-ticket')
  return response.data.data.ticket
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

export async function fetchProblems(params?: {
  q?: string
  status?: ProblemStatus | ''
  tags?: string
  page?: number
  pageSize?: number
  sortBy?: string
}) {
  const response = await http.get<ApiEnvelope<any>>('/problems', { params })
  return response.data.data
}

export async function fetchDailyProblem() {
  const response = await http.get<ApiEnvelope<ProblemSummary>>('/problems/daily')
  return response.data.data
}

export async function fetchLeaderboard(limit = 100) {
  const response = await http.get<ApiEnvelope<LeaderboardRow[]>>('/leaderboard', { params: { limit } })
  return response.data.data
}

export async function fetchMyRank() {
  const response = await http.get<ApiEnvelope<LeaderboardRow>>('/leaderboard/my-rank')
  return response.data.data
}

export async function fetchProblem(id: number) {
  const response = await http.get<ApiEnvelope<ProblemDetail>>(`/problems/${id}`)
  return response.data.data
}

export async function submitSolution(problemId: number, language: Language, sourceCode: string) {
  const response = await http.post<ApiEnvelope<Submission>>('/submissions', { problemId, language, sourceCode })
  return response.data.data
}

export async function runSelfTest(problemId: number, language: Language, sourceCode: string, stdin: string, contestId?: number) {
  const response = await http.post<ApiEnvelope<SelfTestResult>>('/self-tests', {
    problemId,
    contestId,
    language,
    sourceCode,
    stdin
  })
  return response.data.data
}

export async function fetchSubmissions(params?: {
  problemId?: number
  mine?: boolean
}) {
  const response = await http.get<ApiEnvelope<SubmissionSummary[]>>('/submissions', { params })
  return response.data.data
}

export async function fetchSubmission(id: number) {
  const response = await http.get<ApiEnvelope<SubmissionDetail>>(`/submissions/${id}`)
  return response.data.data
}

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

export async function fetchContests() {
  const response = await http.get<ApiEnvelope<Contest[]>>('/contests')
  return response.data.data
}

export async function fetchContest(id: number) {
  const response = await http.get<ApiEnvelope<Contest>>(`/contests/${id}`)
  return response.data.data
}

export async function fetchContestRegistration(id: number) {
  const response = await http.get<ApiEnvelope<ContestRegistrationStatus>>(`/contests/${id}/registration`)
  return response.data.data
}

export async function registerContest(id: number) {
  const response = await http.post<ApiEnvelope<ContestRegistrationStatus>>(`/contests/${id}/register`)
  return response.data.data
}

export async function fetchContestProblems(id: number) {
  const response = await http.get<ApiEnvelope<ContestProblemDetail[]>>(`/contests/${id}/problems`)
  return response.data.data
}

export async function fetchContestProblem(contestId: number, problemId: number) {
  const response = await http.get<ApiEnvelope<ProblemDetail>>(`/contests/${contestId}/problems/${problemId}`)
  return response.data.data
}

export async function fetchContestSubmissions(id: number) {
  const response = await http.get<ApiEnvelope<SubmissionSummary[]>>(`/contests/${id}/submissions`)
  return response.data.data
}

export async function fetchContestLeaderboard(id: number) {
  const response = await http.get<ApiEnvelope<ContestStandingsRow[]>>(`/contests/${id}/leaderboard`)
  return response.data.data
}

export async function downloadContestStandings(id: number): Promise<Blob> {
  const response = await http.get(`/contests/${id}/leaderboard/export`, {
    responseType: 'blob'
  })
  return response.data
}

export async function submitContestSolution(contestId: number, problemId: number, language: Language, sourceCode: string) {
  const response = await http.post<ApiEnvelope<Submission>>('/submissions', { problemId, language, sourceCode, contestId })
  return response.data.data
}

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

export async function fetchProblemSolutions(problemId: number) {
  const response = await http.get<ApiEnvelope<ProblemSolutionSummary[]>>(`/problems/${problemId}/solutions`)
  return response.data.data
}

export async function fetchProblemSolutionDetail(problemId: number, solutionId: number) {
  const response = await http.get<ApiEnvelope<ProblemSolutionDetail>>(`/problems/${problemId}/solutions/${solutionId}`)
  return response.data.data
}

export async function saveProblemSolution(problemId: number, title: string, content: string) {
  const response = await http.post<ApiEnvelope<ProblemSolutionDetail>>(`/problems/${problemId}/solutions`, { title, content })
  return response.data.data
}

export async function deleteProblemSolution(problemId: number, solutionId: number) {
  await http.delete<ApiEnvelope<null>>(`/problems/${problemId}/solutions/${solutionId}`)
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

export async function formatCode(language: Language, sourceCode: string) {
  const response = await http.post<ApiEnvelope<string>>('/code/format', { language, sourceCode })
  return response.data.data
}

export async function fetchSystemVersions() {
  const response = await http.get<ApiEnvelope<Record<string, number>>>('/system/versions')
  return response.data.data
}
