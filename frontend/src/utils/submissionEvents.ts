import type { SubmissionStatus, Verdict } from '../types'

export interface SubmissionUpdateEvent {
  submissionId: number
  userId?: number
  problemId?: number
  contestId?: number | null
  status: SubmissionStatus
  verdict?: Verdict | null
}

export function parseSubmissionUpdate(raw: string): SubmissionUpdateEvent | null {
  if (!raw) return null
  try {
    const parsed = JSON.parse(raw)
    const submissionId = Number(parsed.submissionId ?? parsed.id)
    if (!Number.isFinite(submissionId) || !parsed.status) return null
    return {
      submissionId,
      userId: parsed.userId == null ? undefined : Number(parsed.userId),
      problemId: parsed.problemId == null ? undefined : Number(parsed.problemId),
      contestId: parsed.contestId == null ? null : Number(parsed.contestId),
      status: parsed.status,
      verdict: parsed.verdict || null
    }
  } catch {
    const parts = raw.split(',')
    if (parts.length < 2) return null
    const submissionId = Number(parts[0])
    if (!Number.isFinite(submissionId)) return null
    return {
      submissionId,
      status: parts[1] as SubmissionStatus,
      verdict: (parts[2] || null) as Verdict | null
    }
  }
}
