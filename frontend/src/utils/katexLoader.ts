let katexPromise: Promise<any> | null = null

const CDN_URLS = [
  '/libs/katex/katex.min.js' // Exclusively local server path
]

const CSS_CDN_URLS = [
  '/libs/katex/katex.min.css' // Exclusively local server path
]

export function loadKaTeX(): Promise<any> {
  if (katexPromise) return katexPromise

  katexPromise = new Promise((resolve, reject) => {
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
