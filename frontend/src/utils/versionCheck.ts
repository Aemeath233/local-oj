import { fetchSystemVersions } from '../api/http'

/**
 * In-memory dedup cache for system version checks.
 * Prevents multiple simultaneous calls from creating multiple HTTP requests.
 * Cached result is reused for 10 seconds.
 */
let cachedVersions: Record<string, number> | null = null
let cachedAt = 0
let pendingRequest: Promise<Record<string, number>> | null = null
const CACHE_TTL_MS = 10_000

async function getVersions(): Promise<Record<string, number>> {
  const now = Date.now()
  if (cachedVersions && now - cachedAt < CACHE_TTL_MS) {
    return cachedVersions
  }
  if (pendingRequest) {
    return pendingRequest
  }
  pendingRequest = fetchSystemVersions().then(versions => {
    cachedVersions = versions
    cachedAt = Date.now()
    pendingRequest = null
    return versions
  }).catch(err => {
    pendingRequest = null
    throw err
  })
  return pendingRequest
}

/**
 * Checks if a specific section's data has been updated on the server.
 * Uses sessionStorage to persist the latest loaded version timestamp for the session.
 */
export async function shouldRefreshSection(key: string): Promise<boolean> {
  try {
    const versions = await getVersions()
    if (!versions || !versions[key]) return true

    const serverVersion = versions[key]
    const localVersionStr = sessionStorage.getItem(`coderushoj:version:${key}`)
    const localVersion = localVersionStr ? parseInt(localVersionStr, 10) : 0

    if (serverVersion > localVersion) {
      sessionStorage.setItem(`coderushoj:version:${key}`, serverVersion.toString())
      return true
    }
    return false
  } catch (err) {
    console.error('Failed to check system versions', err)
    return true
  }
}

/**
 * Syncs the local session timestamp with the server when a manual refresh is performed.
 */
export function forceUpdateSectionVersion(key: string) {
  getVersions().then(versions => {
    if (versions && versions[key]) {
      sessionStorage.setItem(`coderushoj:version:${key}`, versions[key].toString())
    }
  }).catch(() => {})
}
