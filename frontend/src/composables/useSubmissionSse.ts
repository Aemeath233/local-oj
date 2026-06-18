import { onUnmounted } from 'vue'
import { requestSseTicket } from '../api/submission'
import { parseSubmissionUpdate } from '../utils/submissionEvents'

export interface SseUpdateEvent {
  submissionId: number
  problemId?: number
  contestId?: number | null
  status: string
  verdict?: string | null
}

export function useSubmissionSse(
  onUpdate: (update: SseUpdateEvent) => void,
  getToken: () => string | null | undefined
) {
  let eventSource: EventSource | null = null
  let reconnectTimeout: any = null
  let isConnecting = false

  async function connect() {
    if (eventSource || isConnecting) return
    const token = getToken()
    if (!token) return

    isConnecting = true
    try {
      const ticket = await requestSseTicket()
      if (!isConnecting || eventSource) return

      const sseUrl = `/api/submissions/live?ticket=${encodeURIComponent(ticket)}`
      eventSource = new EventSource(sseUrl)

      eventSource.addEventListener('update', (event) => {
        try {
          const update = parseSubmissionUpdate(event.data)
          if (update) {
            onUpdate(update as SseUpdateEvent)
          }
        } catch (err) {
          console.error('Failed to parse SSE message', err)
        }
      })

      eventSource.onerror = (err) => {
        console.error('SSE connection error, scheduled reconnect in 1s:', err)
        disconnect()
        scheduleReconnect()
      }
    } catch (error) {
      console.error('Failed to fetch SSE ticket, scheduled reconnect in 1s:', error)
      scheduleReconnect()
    } finally {
      isConnecting = false
    }
  }

  function scheduleReconnect() {
    if (reconnectTimeout) clearTimeout(reconnectTimeout)
    reconnectTimeout = setTimeout(() => {
      connect()
    }, 1000)
  }

  function disconnect() {
    isConnecting = false
    if (reconnectTimeout) {
      clearTimeout(reconnectTimeout)
      reconnectTimeout = null
    }
    if (eventSource) {
      eventSource.close()
      eventSource = null
    }
  }

  onUnmounted(() => {
    disconnect()
  })

  return {
    connect,
    disconnect
  }
}
