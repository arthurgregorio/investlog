import { describe, expect, it } from 'vitest'
import { mount, type VueWrapper } from '@vue/test-utils'
import TotpCodeInput from './TotpCodeInput.vue'

function mountInput(props: { modelValue?: string; invalid?: boolean; disabled?: boolean } = {}) {
  const wrapper: VueWrapper = mount(TotpCodeInput, {
    props: {
      modelValue: '',
      'onUpdate:modelValue': (value: string) => wrapper.setProps({ modelValue: value }),
      ...props,
    },
    attachTo: document.body,
  })
  return wrapper
}

function boxTexts(wrapper: VueWrapper) {
  return wrapper.findAll('[data-testid="totp-code-box"]').map((box) => box.text())
}

function activeBoxIndex(wrapper: VueWrapper) {
  return wrapper
    .findAll('[data-testid="totp-code-box"]')
    .findIndex((box) => box.classes('is-active'))
}

function typeDigit(input: HTMLInputElement, digit: string) {
  const beforeInput = new InputEvent('beforeinput', {
    data: digit,
    inputType: 'insertText',
    cancelable: true,
    bubbles: true,
  })
  input.dispatchEvent(beforeInput)
  return beforeInput
}

describe('TotpCodeInput', () => {
  it('exposes one labelled numeric one-time-code field over six hidden boxes', () => {
    const wrapper = mountInput()

    const input = wrapper.find('input')
    expect(wrapper.findAll('input')).toHaveLength(1)
    expect(input.attributes()).toMatchObject({
      type: 'text',
      inputmode: 'numeric',
      pattern: '[0-9]*',
      autocomplete: 'one-time-code',
      maxlength: '6',
    })
    const label = wrapper.find('label')
    expect(label.text()).toBe('Código de 6 dígitos')
    expect(label.attributes('for')).toBe(input.attributes('id'))
    const boxes = wrapper.findAll('[data-testid="totp-code-box"]')
    expect(boxes).toHaveLength(6)
    expect(boxes.every((box) => box.attributes('aria-hidden') === 'true')).toBe(true)
  })

  it('focuses the field when it appears and marks the first box as active', async () => {
    const wrapper = mountInput()
    await wrapper.vm.$nextTick()

    expect(document.activeElement).toBe(wrapper.find('input').element)
    expect(activeBoxIndex(wrapper)).toBe(0)
  })

  it('renders the typed digits into the boxes in order and moves the active box along', async () => {
    const wrapper = mountInput()

    await wrapper.find('input').setValue('482')

    expect(boxTexts(wrapper)).toEqual(['4', '8', '2', '', '', ''])
    expect(activeBoxIndex(wrapper)).toBe(3)
    expect(wrapper.emitted('update:modelValue')?.slice(-1)[0]).toEqual(['482'])
  })

  it('removes the last digit on Backspace', async () => {
    const wrapper = mountInput({ modelValue: '4829' })

    await wrapper.find('input').setValue('482')

    expect(boxTexts(wrapper)).toEqual(['4', '8', '2', '', '', ''])
  })

  it('fills every box from a pasted code and strips non-digits', async () => {
    const wrapper = mountInput()

    const input = wrapper.find('input')
    await input.setValue('48 2-9a13')

    expect(boxTexts(wrapper)).toEqual(['4', '8', '2', '9', '1', '3'])
    expect((input.element as HTMLInputElement).value).toBe('482913')
    expect(activeBoxIndex(wrapper)).toBe(-1)
  })

  it('emits complete once when the sixth digit is entered', async () => {
    const wrapper = mountInput()
    const input = wrapper.find('input')

    await input.setValue('48291')
    expect(wrapper.emitted('complete')).toBeUndefined()

    await input.setValue('482913')
    await input.setValue('482913')

    expect(wrapper.emitted('complete')).toEqual([['482913']])
  })

  it('shows the invalid state on every box while keeping the digits', () => {
    const wrapper = mountInput({ modelValue: '482913', invalid: true })

    expect(wrapper.find('.totp-code').classes()).toContain('is-invalid')
    expect(wrapper.find('input').attributes('aria-invalid')).toBe('true')
    expect(boxTexts(wrapper)).toEqual(['4', '8', '2', '9', '1', '3'])
  })

  it('starts over from the next digit typed after an invalid code', async () => {
    const wrapper = mountInput({ modelValue: '482913', invalid: true })
    const input = wrapper.find('input')

    const beforeInput = typeDigit(input.element as HTMLInputElement, '7')
    await wrapper.vm.$nextTick()

    expect(beforeInput.defaultPrevented).toBe(true)
    expect(boxTexts(wrapper)).toEqual(['7', '', '', '', '', ''])
    expect(wrapper.find('.totp-code').classes()).not.toContain('is-invalid')
  })

  it('lets typing through untouched while the code is not invalid', () => {
    const wrapper = mountInput({ modelValue: '48' })

    const beforeInput = typeDigit(wrapper.find('input').element as HTMLInputElement, '2')

    expect(beforeInput.defaultPrevented).toBe(false)
  })

  it('disables the field and dims the boxes while submitting', () => {
    const wrapper = mountInput({ modelValue: '482913', disabled: true })

    expect(wrapper.find('input').attributes('disabled')).toBeDefined()
    expect(wrapper.find('.totp-code').classes()).toContain('is-disabled')
  })

  it('refocuses the field and selects the whole code when a submit comes back invalid', async () => {
    const wrapper = mountInput({ modelValue: '482913', disabled: true })
    const input = wrapper.find('input').element as HTMLInputElement

    await wrapper.setProps({ invalid: true, disabled: false })
    await wrapper.vm.$nextTick()

    expect(document.activeElement).toBe(input)
    expect([input.selectionStart, input.selectionEnd]).toEqual([0, 6])
  })
})
