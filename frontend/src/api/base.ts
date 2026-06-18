
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
