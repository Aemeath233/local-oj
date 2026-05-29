let monacoPromise: Promise<any> | null = null

const CDN_URLS = [
  'https://cdn.staticfile.net/monaco-editor/0.55.0/min/vs',
  'https://lib.baomitu.com/monaco-editor/0.55.0/min/vs',
  'https://cdn.bootcdn.net/ajax/libs/monaco-editor/0.55.0/min/vs',
  '/libs/monaco-editor/vs' // Local fallback
]

export function loadMonaco(): Promise<any> {
  if (monacoPromise) return monacoPromise

  monacoPromise = new Promise((resolve, reject) => {
    let index = 0

    function loadScript(url: string, timeout = 4000): Promise<void> {
      return new Promise((res, rej) => {
        const script = document.createElement('script')
        script.src = url
        script.async = true
        
        const timer = setTimeout(() => {
          cleanup()
          rej(new Error(`Timeout loading script: ${url}`))
        }, timeout)

        function cleanup() {
          clearTimeout(timer)
          script.onerror = null
          script.onload = null
          if (script.parentNode) {
            script.parentNode.removeChild(script)
          }
        }

        script.onload = () => {
          cleanup()
          res()
        }
        script.onerror = () => {
          cleanup()
          rej(new Error(`Failed to load script: ${url}`))
        }
        document.head.appendChild(script)
      })
    }

    function tryNext() {
      if (index >= CDN_URLS.length) {
        monacoPromise = null // Reset so it can be retried on next page load/render if everything failed
        reject(new Error('All Monaco Editor sources failed to load'))
        return
      }

      const vsPath = CDN_URLS[index]
      const loaderUrl = `${vsPath}/loader.js`

      console.log(`[Monaco Loader] Attempting to load from: ${vsPath}`)

      // Create CSS link for the editor themes and basic layout styling
      const cssLink = document.createElement('link')
      cssLink.rel = 'stylesheet'
      cssLink.href = `${vsPath}/editor/editor.main.css`
      document.head.appendChild(cssLink)

      loadScript(loaderUrl, 4000)
        .then(() => {
          const req = (window as any).require
          if (!req) {
            throw new Error('window.require is not defined')
          }
          
          req.config({ paths: { vs: vsPath } })
          
          req(['vs/editor/editor.main'], (monaco: any) => {
            console.log(`[Monaco Loader] Successfully loaded Monaco from: ${vsPath}`)
            resolve(monaco)
          }, (err: any) => {
            console.warn(`[Monaco Loader] AMD require failed for: ${vsPath}`, err)
            // Cleanup failed CSS link
            if (cssLink.parentNode) {
              cssLink.parentNode.removeChild(cssLink)
            }
            // Clean AMD variables to prevent namespace pollution for the next loader
            delete (window as any).require
            delete (window as any).define
            index++
            tryNext()
          })
        })
        .catch(err => {
          console.warn(`[Monaco Loader] Failed to load loader from: ${loaderUrl}`, err)
          if (cssLink.parentNode) {
            cssLink.parentNode.removeChild(cssLink)
          }
          delete (window as any).require
          delete (window as any).define
          index++
          tryNext()
        })
    }

    tryNext()
  })

  return monacoPromise
}
