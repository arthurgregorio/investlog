import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import FieldLabel from './FieldLabel.vue'

describe('FieldLabel', () => {
  it('shows the label text with its help text behind an info icon', () => {
    const wrapper = mount(FieldLabel, {
      props: { text: 'Preço atual (opcional)', tip: 'Preencha para acompanhar lucro/prejuízo.' },
    })

    expect(wrapper.text()).toContain('Preço atual (opcional)')
    expect(wrapper.find('.tooltip-content').text()).toBe('Preencha para acompanhar lucro/prejuízo.')
    expect(wrapper.find('.mdi-information-outline').exists()).toBe(true)
  })
})
