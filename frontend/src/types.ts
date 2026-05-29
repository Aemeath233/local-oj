export type Role = 'SUPER_ADMIN' | 'ADMIN' | 'STUDENT'
export type Language = 'C' | 'CPP' | 'PYTHON' | 'JAVA' | 'PYPY3' | 'CPP_O3'
export type SubmissionStatus = 'PENDING' | 'RUNNING' | 'FINISHED'
export type Verdict = 'AC' | 'WA' | 'TLE' | 'MLE' | 'OLE' | 'RE' | 'CE' | 'IE'
export type ProblemStatus = 'UNATTEMPTED' | 'ATTEMPTED' | 'ACCEPTED'
export type SystemLogLevel = 'INFO' | 'WARN' | 'ERROR'

export interface ApiEnvelope<T> {
  data: T
}

export interface User {
  id: number
  username: string
  email?: string
  displayName?: string
  avatarUrl?: string
  studentNo?: string
  major?: string
  role: Role
  enabled?: boolean
  createdAt?: string
  updatedAt?: string
  usernameChangeCountCurrentMonth?: number
  lastUsernameChangedAt?: string
}

export interface PublicProfile {
  id: number
  username: string
  displayName?: string
  avatarUrl?: string
  major?: string
  role: Role
  createdAt: string
  lastActiveAt?: string
  stats: UserStats
  recentSubmissions?: SubmissionSummary[]
}

export interface LoginResult {
  token: string
  user: User
}

export interface ProblemSummary {
  id: number
  slug: string
  title: string
  difficulty: string
  tags?: string
  timeLimitMs: number
  memoryLimitKb: number
  solveStatus?: ProblemStatus
  acceptedCount?: number
  submitCount?: number
}

export interface AdminProblemSummary extends ProblemSummary {
  visible: boolean
  testCaseCount: number
  submissionCount: number
}

export interface SampleCase {
  inputText: string
  expectedOutput: string
}

export interface ProblemDetail extends ProblemSummary {
  description: string
  samples: SampleCase[]
}

export interface Submission {
  id: number
  userId: number
  problemId: number
  language: Language
  sourceCode?: string
  status: SubmissionStatus
  verdict?: Verdict
  score: number
  timeMs?: number
  memoryKb?: number
  errorMessage?: string
  createdAt: string
  judgedAt?: string
}

export interface SubmissionSummary {
  id: number
  userId: number
  username?: string
  displayName?: string
  avatarUrl?: string
  problemId: number
  problemTitle?: string
  language: Language
  status: SubmissionStatus
  verdict?: Verdict
  score: number
  timeMs?: number
  memoryKb?: number
  createdAt: string
  judgedAt?: string
}

export interface SubmissionCaseResult {
  id: number
  submissionId: number
  testCaseId?: number
  caseIndex: number
  verdict: Verdict
  timeMs?: number
  memoryKb?: number
  stdoutText?: string
  stderrText?: string
  message?: string
}

export interface SubmissionProblem {
  id: number
  slug: string
  title: string
  timeLimitMs: number
  memoryLimitKb: number
}

export interface SubmissionDetail {
  submission: Submission
  problem?: SubmissionProblem
  cases: SubmissionCaseResult[]
}

export interface SelfTestResult {
  verdict: Verdict
  timeMs: number
  memoryKb: number
  stdout?: string
  stderr?: string
  message?: string
}

export interface AdminSubmissionSummary {
  id: number
  userId: number
  username?: string
  displayName?: string
  problemId: number
  problemTitle?: string
  language: Language
  status: SubmissionStatus
  verdict?: Verdict
  score: number
  timeMs?: number
  memoryKb?: number
  createdAt: string
  judgedAt?: string
}

export interface AdminDashboard {
  problemCount: number
  visibleProblemCount: number
  userCount: number
  submissionCount: number
  acceptedSubmissionCount: number
  pendingSubmissionCount: number
  runningSubmissionCount: number
  verdictCounts: Record<Verdict, number>
  recentSubmissions: AdminSubmissionSummary[]
}

export interface TestCase {
  id?: number
  problemId?: number
  uploadToken?: string
  name?: string
  caseName?: string
  inputFile: string
  outputFile: string
  inputSize: number
  outputSize: number
  inputText?: string
  expectedOutput?: string
  score: number
  sortOrder?: number
  sample?: boolean
}

export interface SmtpSettings {
  enabled: boolean
  host?: string
  port: number
  username?: string
  fromAddress?: string
  fromName?: string
  authEnabled: boolean
  useSsl: boolean
  useStarttls: boolean
  passwordSet: boolean
}

export interface SystemSettings {
  allowedOrigins?: string
}

export interface ProblemTag {
  id: number
  name: string
  color?: string
}

export interface SandboxSettings {
  workerThreads: number
  maxConcurrentRuns: number
  compileTimeoutMs: number
  defaultOutputLimitKb: number
  maxProcessCount: number
}

export interface LeaderboardRow {
  rank: number
  userId: number
  username: string
  displayName?: string
  avatarUrl?: string
  studentNo?: string
  major?: string
  acceptedCount: number
  submissionCount: number
  lastAcceptedAt?: string
}

export interface AdminProblemDetail {
  problem: {
    id: number
    slug: string
    title: string
    description: string
    timeLimitMs: number
    memoryLimitKb: number
    difficulty: string
    tags?: string
    visible: boolean
  }
  testCases: TestCase[]
}

export interface ProblemPackageCasePreview {
  name: string
  inputFile: string
  outputFile: string
  inputSize: number
  outputSize: number
  score: number
  sample: boolean
}

export interface ProblemPackagePreview {
  filename: string
  configFile: string
  statementFile: string
  statementChars: number
  title: string
  slug: string
  slugGenerated: boolean
  difficulty: string
  tags?: string
  timeLimitMs: number
  memoryLimitKb: number
  visible: boolean
  cases: ProblemPackageCasePreview[]
  warnings: string[]
}

export interface Contest {
  id: number
  title: string
  description?: string
  startTime: string
  endTime: string
  visible: boolean
  type: 'ACM' | 'OI'
  freezeDurationMinutes?: number
  createdAt: string
  updatedAt: string
  participantCount?: number
  problemCount?: number
}

export interface AdminContestSummary extends Contest {
  status: 'UPCOMING' | 'RUNNING' | 'FINISHED'
  problemCount: number
  registrationCount: number
  submissionCount: number
}

export interface ContestProblemDetail {
  id: number
  slug: string
  title: string
  difficulty: string
  sequenceCode: string
  submissionCount: number
  acceptedCount: number
}

export interface ContestRegistrationStatus {
  registered: boolean
  canRegister: boolean
  registrationCount: number
  registeredAt?: string
}

export interface ContestStandingsRow {
  rank: number
  userId: number
  username: string
  displayName?: string
  avatarUrl?: string
  acceptedCount: number
  totalPenaltyMinutes: number
  totalScore?: number
  problemDetails: Record<number, {
    accepted: boolean
    failedAttempts: number
    acElapsedMinutes?: number
    firstToSolve: boolean
    score?: number
  }>
}

export interface AdminContestDetail {
  contest: Contest
  problemIds: number[]
}

export interface ProblemSolutionSummary {
  id: number
  problemId: number
  userId: number
  username?: string
  displayName?: string
  avatarUrl?: string
  title: string
  createdAt: string
  updatedAt: string
}

export interface ProblemSolutionDetail {
  id: number
  problemId: number
  userId: number
  username?: string
  displayName?: string
  avatarUrl?: string
  title: string
  content: string
  createdAt: string
  updatedAt: string
}

export interface SystemLog {
  id: number
  level: SystemLogLevel
  service: string
  module: string
  event: string
  message: string
  submissionId?: number
  problemId?: number
  userId?: number
  details?: string
  createdAt: string
}

export interface SubmissionDailyStats {
  totalCount: number
  acCount: number
}

export interface DifficultyDistribution {
  easySolved: number
  easyTotal: number
  mediumSolved: number
  mediumTotal: number
  hardSolved: number
  hardTotal: number
}

export interface UserStats {
  heatmap: Record<string, SubmissionDailyStats>
  difficultyDistribution: DifficultyDistribution
}
