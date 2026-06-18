import type { ApiEnvelope, Language, ProblemDetail, Submission, SubmissionSummary, Contest, ContestProblemDetail, ContestRegistrationStatus, ContestStandingsRow } from '../types'
import { http } from './base'

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
