<template>
  <el-tag :type="tagType" effect="dark" round>
    {{ label }}
  </el-tag>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { SubmissionStatus, Verdict } from '../types'

const props = defineProps<{
  status?: SubmissionStatus
  verdict?: Verdict
}>()

const label = computed(() => {
  if (props.status === 'PENDING') return 'PENDING'
  if (props.status === 'RUNNING') return 'RUNNING'
  return props.verdict ?? 'WAITING'
})

const tagType = computed(() => {
  if (props.verdict === 'AC') return 'success'
  if (props.verdict === 'WA') return 'danger'
  if (props.verdict === 'CE') return 'warning'
  if (props.status && props.status !== 'FINISHED') return 'info'
  return 'danger'
})
</script>
