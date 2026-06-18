import type { ApiEnvelope, Language, Submission, SubmissionDetail, SubmissionSummary, SelfTestResult } from '../types'
import { http } from './base'

export async function requestSseTicket() {
  const response = await http.post<ApiEnvelope<{ ticket: string }>>('/submissions/sse-ticket')
  return response.data.data.ticket
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
