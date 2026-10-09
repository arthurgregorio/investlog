import { readonly, ref } from 'vue'

export function useExpandedRow() {
  const expandedId = ref<string | null>(null)

  function isExpanded(id: string): boolean {
    return expandedId.value === id
  }

  function toggle(id: string) {
    expandedId.value = isExpanded(id) ? null : id
  }

  function collapse() {
    expandedId.value = null
  }

  return { expandedId: readonly(expandedId), isExpanded, toggle, collapse }
}
