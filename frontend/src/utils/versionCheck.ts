import { fetchSystemVersions } from '../api/http'

/**
 * Checks if a specific section's data has been updated on the server.
 * Uses sessionStorage to persist the latest loaded version timestamp for the session.
 */
export async function shouldRefreshSection(key: string): Promise<boolean> {
  try {
    const versions = await fetchSystemVersions()
    if (!versions || !versions[key]) return true

    const serverVersion = versions[key]
    const localVersionStr = sessionStorage.getItem(`localoj:version:${key}`)
    const localVersion = localVersionStr ? parseInt(localVersionStr, 10) : 0

    if (serverVersion > localVersion) {
      // Server is newer, update our local session timestamp and allow refresh
      sessionStorage.setItem(`localoj:version:${key}`, serverVersion.toString())
      return true
    }
    // Server is up to date, no need to refresh!
    return false
  } catch (err) {
    console.error('Failed to check system versions', err)
    return true // Fallback to refresh if check fails
  }
}

/**
 * Syncs the local session timestamp with the server when a manual refresh is performed.
 */
export function forceUpdateSectionVersion(key: string) {
  fetchSystemVersions().then(versions => {
    if (versions && versions[key]) {
      sessionStorage.setItem(`localoj:version:${key}`, versions[key].toString())
    }
  }).catch(() => {})
}
