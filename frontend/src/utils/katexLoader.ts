let katexPromise: Promise<any> | null = null

const base = (import.meta.env.BASE_URL || '/').replace(/\/$/, '')

const CDN_URLS = [
  `${base}/libs/katex/katex.min.js` // Exclusively local server path
]

const CSS_CDN_URLS = [
  `${base}/libs/katex/katex.min.css` // Exclusively local server path
]

export function loadKaTeX(): Promise<any> {
  if (katexPromise) return katexPromise

  katexPromise = new Promise((resolve, reject) => {
    let index = 0

    function loadScript(url: string, timeout = 4000): Promise<void> {
      return new Promise((res, rej) => {
        // Monaco's global AMD loader defines "window.define".
        // This causes KaTeX (a UMD module) to define itself as an AMD module instead of window.katex.
        // We temporarily override "window.define" during script execution to force UMD global registration.
        const existingDefine = (window as any).define
        let defineOverridden = false
        if (existingDefine && existingDefine.amd) {
          (window as any).define = undefined
          defineOverridden = true
        }

        const script = document.createElement('script')
        script.src = url
        script.async = true
        
        const timer = setTimeout(() => {
          if (defineOverridden && existingDefine) {
            (window as any).define = existingDefine
          }
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
          if (defineOverridden && existingDefine) {
            (window as any).define = existingDefine
          }
          cleanup()
          res()
        }
        script.onerror = () => {
          if (defineOverridden && existingDefine) {
            (window as any).define = existingDefine
          }
          cleanup()
          rej(new Error(`Failed to load script: ${url}`))
        }
        document.head.appendChild(script)
      })
    }

    function loadCSS(url: string) {
      const link = document.createElement('link')
      link.rel = 'stylesheet'
      link.href = url
      document.head.appendChild(link)
    }

    function tryNext() {
      if (index >= CDN_URLS.length) {
        katexPromise = null // Allow retrying on future renders if everything failed
        reject(new Error('All KaTeX sources failed to load'))
        return
      }

      const jsUrl = CDN_URLS[index]
      const cssUrl = CSS_CDN_URLS[index]

      console.log(`[KaTeX Loader] Attempting to load from: ${jsUrl}`)

      // Inject the stylesheet
      loadCSS(cssUrl)

      loadScript(jsUrl, 4000)
        .then(() => {
          const katex = (window as any).katex
          if (katex) {
            console.log(`[KaTeX Loader] Successfully loaded KaTeX from: ${jsUrl}`)
            resolve(katex)
          } else {
            throw new Error('window.katex is not defined')
          }
        })
        .catch(err => {
          console.warn(`[KaTeX Loader] Failed to load from: ${jsUrl}`, err)
          index++
          tryNext()
        })
    }

    tryNext()
  })

  return katexPromise
}
