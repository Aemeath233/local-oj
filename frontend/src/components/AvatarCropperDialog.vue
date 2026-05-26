<template>
  <el-dialog
    :model-value="modelValue"
    title="更换头像 - 裁剪区域"
    width="480px"
    destroy-on-close
    align-center
    custom-class="cropper-dialog"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="cropper-dialog-content">
      <p class="cropper-tip">拖拽图片移动位置，使用下方滑块缩放，虚线内为最终展示区域</p>
      
      <!-- Interactive Bounded Cropper Area -->
      <div 
        class="cropper-area"
        ref="containerRef"
        @mousedown="handleMouseDown"
        @touchstart="handleTouchStart"
      >
        <img
          v-if="imageUrl"
          :src="imageUrl"
          ref="imgRef"
          alt="Avatar Preview"
          class="cropper-img"
          :style="imageStyle"
          @load="onImageLoaded"
          draggable="false"
        />
        
        <!-- Premium Box-Shadow Mask Overlay with Circle Guide -->
        <div class="crop-frame">
          <div class="crop-circle-helper"></div>
        </div>
      </div>
      
      <!-- Slider Zoom Control -->
      <div class="cropper-zoom-controls">
        <el-icon class="zoom-icon"><ZoomOut /></el-icon>
        <el-slider
          v-model="zoom"
          :min="1.0"
          :max="4.0"
          :step="0.01"
          :show-tooltip="false"
          class="zoom-slider"
          @input="clampCoordinates"
        />
        <el-icon class="zoom-icon"><ZoomIn /></el-icon>
      </div>
    </div>
    
    <template #footer>
      <div class="cropper-footer">
        <el-button @click="emit('update:modelValue', false)">取消</el-button>
        <el-button type="primary" :loading="loading" @click="confirmCrop">确定</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { ZoomIn, ZoomOut } from '@element-plus/icons-vue'

const props = defineProps<{
  modelValue: boolean
  imageFile: File | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  'crop': [file: File]
}>()

const imageUrl = ref('')
const zoom = ref(1.0)
const translateX = ref(0)
const translateY = ref(0)
const loading = ref(false)

const containerRef = ref<HTMLDivElement | null>(null)
const imgRef = ref<HTMLImageElement | null>(null)

const renderedWidth = ref(400)
const renderedHeight = ref(400)

// Standard Dimensions
const containerSize = 400
const cropSize = 260

// Dynamic transform styles for the image element
const imageStyle = computed(() => {
  return {
    width: `${renderedWidth.value}px`,
    height: `${renderedHeight.value}px`,
    transform: `translate(${translateX.value}px, ${translateY.value}px) scale(${zoom.value})`,
    transformOrigin: 'center center'
  }
})

// Convert File prop to Object URL on open
watch(() => props.imageFile, (newFile) => {
  if (imageUrl.value) {
    URL.revokeObjectURL(imageUrl.value)
    imageUrl.value = ''
  }
  if (newFile) {
    imageUrl.value = URL.createObjectURL(newFile)
    zoom.value = 1.0
    translateX.value = 0
    translateY.value = 0
  }
}, { immediate: true })

// Clean up object URL when component is destroyed
watch(() => props.modelValue, (isOpen) => {
  if (!isOpen && imageUrl.value) {
    URL.revokeObjectURL(imageUrl.value)
    imageUrl.value = ''
  }
})

// Set initial sizing so the image fills the cover limits
function onImageLoaded() {
  if (!imgRef.value) return
  const natW = imgRef.value.naturalWidth
  const natH = imgRef.value.naturalHeight
  
  if (natW > natH) {
    renderedHeight.value = cropSize
    renderedWidth.value = Math.round((natW / natH) * cropSize)
  } else {
    renderedWidth.value = cropSize
    renderedHeight.value = Math.round((natH / natW) * cropSize)
  }
  
  translateX.value = 0
  translateY.value = 0
  clampCoordinates()
}

// Ensure the image boundaries cover the crop frame entirely
function clampCoordinates() {
  const w = renderedWidth.value * zoom.value
  const h = renderedHeight.value * zoom.value
  
  const limitX = Math.max(0, w / 2 - cropSize / 2)
  const limitY = Math.max(0, h / 2 - cropSize / 2)
  
  translateX.value = Math.max(-limitX, Math.min(limitX, translateX.value))
  translateY.value = Math.max(-limitY, Math.min(limitY, translateY.value))
}

// Mouse/Touch Drag Event Handlers
let isDragging = false
let startX = 0
let startY = 0
let startTranslateX = 0
let startTranslateY = 0

function handleMouseDown(e: MouseEvent) {
  isDragging = true
  startX = e.clientX
  startY = e.clientY
  startTranslateX = translateX.value
  startTranslateY = translateY.value
  
  document.addEventListener('mousemove', handleMouseMove)
  document.addEventListener('mouseup', handleMouseUp)
}

function handleMouseMove(e: MouseEvent) {
  if (!isDragging) return
  const dx = e.clientX - startX
  const dy = e.clientY - startY
  translateX.value = startTranslateX + dx
  translateY.value = startTranslateY + dy
  clampCoordinates()
}

function handleMouseUp() {
  isDragging = false
  document.removeEventListener('mousemove', handleMouseMove)
  document.removeEventListener('mouseup', handleMouseUp)
}

function handleTouchStart(e: TouchEvent) {
  if (e.touches.length !== 1) return
  isDragging = true
  startX = e.touches[0].clientX
  startY = e.touches[0].clientY
  startTranslateX = translateX.value
  startTranslateY = translateY.value
  
  document.addEventListener('touchmove', handleTouchMove, { passive: false })
  document.addEventListener('touchend', handleTouchEnd)
}

function handleTouchMove(e: TouchEvent) {
  if (!isDragging || e.touches.length !== 1) return
  e.preventDefault() // Prevents page scrolling while zooming/dragging
  const dx = e.touches[0].clientX - startX
  const dy = e.touches[0].clientY - startY
  translateX.value = startTranslateX + dx
  translateY.value = startTranslateY + dy
  clampCoordinates()
}

function handleTouchEnd() {
  isDragging = false
  document.removeEventListener('touchmove', handleTouchMove)
  document.removeEventListener('touchend', handleTouchEnd)
}

// Crop mathematical calculation and export
function confirmCrop() {
  if (!imgRef.value || !props.imageFile) return
  
  loading.value = true
  
  const img = imgRef.value
  const natW = img.naturalWidth
  const natH = img.naturalHeight
  const rendW = renderedWidth.value
  const rendH = renderedHeight.value
  
  const ratio = natW / rendW
  const z = zoom.value
  
  // 1. Calculate rendered top-left offset inside the zoom container
  const xOnRendered = (rendW * z) / 2 - cropSize / 2 - translateX.value
  const yOnRendered = (rendH * z) / 2 - cropSize / 2 - translateY.value
  
  // 2. Map coordinates to the original high-resolution image size
  const sx = (xOnRendered / z) * ratio
  const sy = (yOnRendered / z) * ratio
  const sSize = (cropSize / z) * ratio
  
  // 3. Render onto high-fidelity 512x512 Canvas
  const canvas = document.createElement('canvas')
  canvas.width = 512
  canvas.height = 512
  
  const ctx = canvas.getContext('2d')
  if (!ctx) {
    ElMessage.error('无法创建图片渲染层')
    loading.value = false
    return
  }
  
  // Draw scaled slice onto standard square size
  ctx.drawImage(img, sx, sy, sSize, sSize, 0, 0, 512, 512)
  
  // 4. Output as compact modern WebP
  canvas.toBlob(async (blob) => {
    if (!blob) {
      ElMessage.error('头像剪裁格式化失败')
      loading.value = false
      return
    }
    
    // Check maximum 2MB constraint
    if (blob.size > 2 * 1024 * 1024) {
      ElMessage.error('裁剪后头像大小仍超过 2MB，请换用较小的图片或再次调整')
      loading.value = false
      return
    }
    
    const base = props.imageFile!.name.replace(/\.[^.]+$/, '') || 'avatar'
    const croppedFile = new File([blob], `${base}.webp`, { type: 'image/webp' })
    
    loading.value = false
    emit('crop', croppedFile)
  }, 'image/webp', 0.88)
}
</script>

<style scoped>
.cropper-dialog-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
}

.cropper-tip {
  color: #64748b;
  font-size: 13px;
  text-align: center;
  margin: 0;
  line-height: 1.4;
}

.cropper-area {
  position: relative;
  width: 400px;
  height: 400px;
  overflow: hidden;
  background-color: #0f172a;
  border-radius: 12px;
  user-select: none;
  cursor: grab;
  display: flex;
  align-items: center;
  justify-content: center;
}

.cropper-area:active {
  cursor: grabbing;
}

.cropper-img {
  position: absolute;
  max-width: none;
  max-height: none;
  pointer-events: none;
}

.crop-frame {
  position: absolute;
  top: 70px; /* (400 - 260) / 2 */
  left: 70px;
  width: 260px;
  height: 260px;
  border: 2px solid #ffffff;
  border-radius: 8px;
  box-shadow: 0 0 0 9999px rgba(15, 23, 42, 0.7);
  pointer-events: none;
  box-sizing: border-box;
  z-index: 10;
}

.crop-circle-helper {
  width: 100%;
  height: 100%;
  border: 1px dashed rgba(255, 255, 255, 0.65);
  border-radius: 50%;
  box-sizing: border-box;
}

.cropper-zoom-controls {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  max-width: 360px;
  margin-top: 4px;
}

.zoom-icon {
  font-size: 18px;
  color: #64748b;
}

.zoom-slider {
  flex: 1;
}

.cropper-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  width: 100%;
}
</style>
