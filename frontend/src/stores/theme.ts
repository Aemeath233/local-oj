import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useThemeStore = defineStore('theme', () => {
  const isDark = ref(localStorage.getItem('localoj.theme') === 'dark')

  function toggle() {
    isDark.value = !isDark.value
    localStorage.setItem('localoj.theme', isDark.value ? 'dark' : 'light')
    applyTheme()
  }

  function applyTheme() {
    if (isDark.value) {
      document.documentElement.classList.add('dark')
    } else {
      document.documentElement.classList.remove('dark')
    }
  }

  // Apply on load
  applyTheme()

  return { isDark, toggle }
})
