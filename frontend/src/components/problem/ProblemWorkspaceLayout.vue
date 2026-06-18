<template>
  <section class="problem-layout" ref="problemLayoutRef">
    <!-- Left Panel -->
    <article class="statement panel" :style="leftStyle">
      <el-tooltip v-if="!isMobile" :content="isMaximized ? '还原布局' : '放大题面'" placement="top">
        <el-button
          class="maximize-btn"
          circle
          :icon="isMaximized ? ScaleToOriginal : FullScreen"
          @click="toggleMaximize"
          size="small"
        />
      </el-tooltip>
      <slot name="left"></slot>
    </article>

    <!-- Drag Resizable Divider -->
    <div v-if="!isMobile && !isMaximized" class="resize-divider" @mousedown="startDrag">
      <div class="resize-divider-line"></div>
    </div>

    <!-- Right Panel -->
    <div v-if="!isMobile" v-show="!isMaximized" class="right-panel-wrapper" :style="rightStyle">
      <slot name="right"></slot>
    </div>
  </section>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, type CSSProperties } from 'vue'
import { FullScreen, ScaleToOriginal } from '@element-plus/icons-vue'

const problemLayoutRef = ref<HTMLElement | null>(null)
const leftWidthPercent = ref(50)
let startX = 0
let startWidthPercent = 0

const isWideScreen = ref(window.innerWidth >= 1041)
const isMobile = ref(window.innerWidth <= 768)
const isMaximized = ref(false)

function handleResize() {
  isWideScreen.value = window.innerWidth >= 1041
  isMobile.value = window.innerWidth <= 768
}

function toggleMaximize() {
  isMaximized.value = !isMaximized.value
  setTimeout(() => {
    window.dispatchEvent(new Event('resize'))
  }, 100)
}

const leftStyle = computed<CSSProperties>(() => {
  if (isMaximized.value) {
    return {
      width: '100%',
      flex: '0 0 100%',
      display: 'flex',
      flexDirection: 'column',
      position: 'relative'
    }
  }
  if (!isWideScreen.value || isMobile.value) {
    return {
      display: 'flex',
      flexDirection: 'column',
      position: 'relative'
    }
  }
  return {
    width: `${leftWidthPercent.value}%`,
    flex: `0 0 ${leftWidthPercent.value}%`,
    display: 'flex',
    flexDirection: 'column',
    position: 'relative'
  }
})

const rightStyle = computed<CSSProperties>(() => {
  if (isMaximized.value) {
    return { display: 'none' }
  }
  if (!isWideScreen.value || isMobile.value) return {}
  return {
    width: `${100 - leftWidthPercent.value}%`,
    flex: `0 0 ${100 - leftWidthPercent.value}%`,
    display: 'flex',
    flexDirection: 'column'
  }
})

function startDrag(event: MouseEvent) {
  event.preventDefault()
  startX = event.clientX
  startWidthPercent = leftWidthPercent.value
  
  document.body.style.cursor = 'col-resize'
  document.body.style.userSelect = 'none'
  
  window.addEventListener('mousemove', doDrag)
  window.addEventListener('mouseup', stopDrag)
}

function doDrag(event: MouseEvent) {
  if (!problemLayoutRef.value) return
  const containerWidth = problemLayoutRef.value.getBoundingClientRect().width
  if (containerWidth === 0) return
  
  const deltaX = event.clientX - startX
  const deltaPercent = (deltaX / containerWidth) * 100
  let newPercent = startWidthPercent + deltaPercent
  
  if (newPercent < 20) newPercent = 20
  if (newPercent > 80) newPercent = 80
  
  leftWidthPercent.value = newPercent
}

function stopDrag() {
  document.body.style.cursor = ''
  document.body.style.userSelect = ''
  
  window.removeEventListener('mousemove', doDrag)
  window.removeEventListener('mouseup', stopDrag)
  
  window.dispatchEvent(new Event('resize'))
}

onMounted(() => {
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
})

defineExpose({
  isMobile
})
</script>

<style scoped>
.right-panel-wrapper {
  min-width: 0;
}
</style>
