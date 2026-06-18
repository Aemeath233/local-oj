import type { ApiEnvelope, LeaderboardRow } from '../types'
import { http } from './base'

export async function fetchLeaderboard(limit = 100) {
  const response = await http.get<ApiEnvelope<LeaderboardRow[]>>('/leaderboard', { params: { limit } })
  return response.data.data
}


export async function fetchMyRank() {
  const response = await http.get<ApiEnvelope<LeaderboardRow>>('/leaderboard/my-rank')
  return response.data.data
}
