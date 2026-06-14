<script setup lang="ts">
defineOptions({ inheritAttrs: false })

defineProps<{ modelValue: string; placeholder?: string; prefix?: string }>()
const emit = defineEmits<{ 'update:modelValue': [string] }>()

function onInput(e: Event) {
  emit('update:modelValue', (e.target as HTMLInputElement).value)
}
</script>

<template>
  <div v-if="prefix" class="inp-wrap">
    <span class="inp-prefix">{{ prefix }}</span>
    <input class="inp" :value="modelValue" :placeholder="placeholder" v-bind="$attrs" @input="onInput" />
  </div>
  <input v-else class="inp" :value="modelValue" :placeholder="placeholder" v-bind="$attrs" @input="onInput" />
</template>
