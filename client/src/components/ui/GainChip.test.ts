import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import GainChip from './GainChip.vue'

describe('GainChip', () => {
  it('renders a positive value with the gain class and a plus sign', () => {
    const wrapper = mount(GainChip, { props: { value: 150 } })

    expect(wrapper.classes()).toContain('gl-up')
    expect(wrapper.text()).toContain('+R$ 150,00')
  })

  it('renders a negative value with the loss class and a minus sign', () => {
    const wrapper = mount(GainChip, { props: { value: -42.5 } })

    expect(wrapper.classes()).toContain('gl-down')
    expect(wrapper.text()).toContain('−R$ 42,50')
  })

  it('treats a value too small to matter as flat', () => {
    const wrapper = mount(GainChip, { props: { value: 0 } })

    expect(wrapper.classes()).toContain('gl-flat')
    expect(wrapper.classes()).not.toContain('gl-up')
    expect(wrapper.classes()).not.toContain('gl-down')
  })

  it('renders a dash and no chip when the value is null', () => {
    const wrapper = mount(GainChip, { props: { value: null } })

    expect(wrapper.text()).toBe('—')
    expect(wrapper.classes()).toContain('gl-empty')
    expect(wrapper.find('.gl').exists()).toBe(false)
  })

  it('shows the signed percentage only when one is given', () => {
    const withPercentage = mount(GainChip, { props: { value: 10, pct: 5.5 } })
    const withoutPercentage = mount(GainChip, { props: { value: 10 } })

    expect(withPercentage.get('.gl-pct').text()).toBe('+5,50%')
    expect(withoutPercentage.find('.gl-pct').exists()).toBe(false)
  })

  it('formats in the given currency and compacts large amounts on request', () => {
    const wrapper = mount(GainChip, { props: { value: 2500, cur: 'USD', compact: true } })

    expect(wrapper.text()).toContain('+US$ 2,5k')
  })

  it('adds the stacked layout class when asked to stack', () => {
    const wrapper = mount(GainChip, { props: { value: 10, stacked: true } })

    expect(wrapper.classes()).toContain('gl-stacked')
  })
})
