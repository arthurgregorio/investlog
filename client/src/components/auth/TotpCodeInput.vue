<script setup lang="ts">
import { computed, nextTick, onMounted, ref, useId, watch } from 'vue'

const CODE_LENGTH = 6
const GROUP_SIZE = 3

const props = withDefaults(
  defineProps<{
    invalid?: boolean
    disabled?: boolean
  }>(),
  { invalid: false, disabled: false },
)

const emit = defineEmits<{
  complete: [totpCode: string]
}>()

const totpCode = defineModel<string>({ default: '' })

const inputId = useId()
const input = ref<HTMLInputElement>()
const focused = ref(false)
const invalidDismissed = ref(false)

const showsInvalid = computed(() => props.invalid && !invalidDismissed.value)

const boxes = computed(() =>
  Array.from({ length: CODE_LENGTH }, (_, index) => {
    const digit = totpCode.value[index] ?? ''
    const active =
      focused.value && !props.disabled && !showsInvalid.value && index === totpCode.value.length
    return { digit, active }
  }),
)

function onlyDigits(value: string) {
  return value.replace(/\D/g, '').slice(0, CODE_LENGTH)
}

function updateCode(digits: string) {
  const wasComplete = totpCode.value.length === CODE_LENGTH
  invalidDismissed.value = true
  totpCode.value = digits
  if (digits.length === CODE_LENGTH && !wasComplete) {
    emit('complete', digits)
  }
}

function onInput(event: Event) {
  const target = event.target as HTMLInputElement
  const digits = onlyDigits(target.value)
  target.value = digits
  updateCode(digits)
}

function onBeforeInput(event: InputEvent) {
  if (!showsInvalid.value || event.inputType !== 'insertText' || !event.data) {
    return
  }
  const digits = onlyDigits(event.data)
  event.preventDefault()
  if (input.value) {
    input.value.value = digits
  }
  updateCode(digits)
}

function focusInput() {
  input.value?.focus()
}

function selectWholeCode() {
  input.value?.setSelectionRange(0, totpCode.value.length)
}

watch(
  () => props.invalid,
  (invalid) => {
    if (invalid) {
      invalidDismissed.value = false
    }
  },
)

watch(
  () => props.disabled,
  async (disabled) => {
    if (disabled) {
      return
    }
    await nextTick()
    focusInput()
    if (showsInvalid.value) {
      selectWholeCode()
    }
  },
)

onMounted(focusInput)
</script>

<template>
  <b-field label="Código de 6 dígitos" :label-for="inputId">
    <div
      class="totp-code is-relative is-flex is-align-items-center is-justify-content-space-between"
      :class="{ 'is-invalid': showsInvalid, 'is-disabled': disabled }"
    >
      <template v-for="(box, index) in boxes" :key="index">
        <span v-if="index === GROUP_SIZE" class="totp-code-separator" aria-hidden="true" />
        <div
          class="totp-code-box is-flex is-align-items-center is-justify-content-center has-text-weight-bold"
          :class="{ 'is-filled': box.digit, 'is-active': box.active }"
          aria-hidden="true"
          data-testid="totp-code-box"
        >
          {{ box.digit }}<span v-if="box.active" class="totp-code-caret" />
        </div>
      </template>
      <input
        :id="inputId"
        ref="input"
        class="totp-code-input is-overlay"
        type="text"
        inputmode="numeric"
        pattern="[0-9]*"
        autocomplete="one-time-code"
        maxlength="6"
        required
        :value="totpCode"
        :disabled="disabled"
        :aria-invalid="showsInvalid"
        @beforeinput="onBeforeInput"
        @input="onInput"
        @focus="focused = true"
        @blur="focused = false"
      />
    </div>
  </b-field>
</template>
