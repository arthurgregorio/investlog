import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import WalletHighlightCard from './WalletHighlightCard.vue'

describe('WalletHighlightCard', () => {
  it('shows its label beside a rail coloured by its kind', () => {
    const wrapper = mount(WalletHighlightCard, {
      props: { label: 'Pior desempenho', rail: 'down' },
    })

    expect(wrapper.text()).toContain('Pior desempenho')
    expect(wrapper.get('.wd-rail').classes()).toContain('wd-rail-down')
  })

  it('renders the slotted body under the label', () => {
    const wrapper = mount(WalletHighlightCard, {
      props: { label: 'Maior posição', rail: 'largest' },
      slots: { default: '<span class="body">PETR4 41,10%</span>' },
    })

    expect(wrapper.get('.wd-rail').classes()).toContain('wd-rail-largest')
    expect(wrapper.get('.body').text()).toBe('PETR4 41,10%')
    expect(wrapper.text()).not.toContain('—')
  })

  it('falls back to a dash when the slot renders nothing', () => {
    const wrapper = mount(WalletHighlightCard, {
      props: { label: 'Melhor desempenho', rail: 'up' },
      slots: { default: '<span v-if="false">never</span>' },
    })

    expect(wrapper.get('.wd-rail').classes()).toContain('wd-rail-up')
    expect(wrapper.text()).toContain('—')
  })
})
