import type { ApiEnvelope, ProblemDetail, ProblemStatus, ProblemSummary, ProblemSolutionSummary, ProblemSolutionDetail } from '../types'
import { http } from './base'

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


export async function fetchProblem(id: number) {
  const response = await http.get<ApiEnvelope<ProblemDetail>>(`/problems/${id}`)
  return response.data.data
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
