const beautifulColors = [
  '#409EFF', '#67C23A', '#E6A23C', '#F56C6C', 
  '#8E44AD', '#1ABC9C', '#2ECC71', '#3498DB', 
  '#E67E22', '#E74C3C', '#E056FD', '#686DE0', 
  '#FC5C65', '#FD9644', '#2BCBBA', '#26DE81'
]

/**
 * Generates a deterministic beautiful color based on a string name.
 */
export function getTagColor(tagName: string): string {
  if (!tagName) return '#909399'
  
  let hash = 0
  for (let i = 0; i < tagName.length; i++) {
    hash = tagName.charCodeAt(i) + ((hash << 5) - hash)
  }
  
  const index = Math.abs(hash) % beautifulColors.length
  return beautifulColors[index]
}
