import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import FrozenBadge from './FrozenBadge.vue'

describe('FrozenBadge', () => {
  it('renders a snowflake with the Congelado title and accessible name and no text', () => {
    const wrapper = mount(FrozenBadge)

    expect(wrapper.attributes('title')).toBe('Congelado')
    expect(wrapper.attributes('aria-label')).toBe('Congelado')
    expect(wrapper.attributes('role')).toBe('img')
    expect(wrapper.attributes('data-testid')).toBe('frozen-tag')
    expect(wrapper.find('.mdi-snowflake').exists()).toBe(true)
    expect(wrapper.text()).toBe('')
  })

  it('fills the default 36px square', () => {
    const wrapper = mount(FrozenBadge)

    expect(wrapper.classes()).not.toContain('is-compact')
    expect(wrapper.attributes('style')).toContain('width: 36px')
    expect(wrapper.attributes('style')).toContain('height: 36px')
  })

  it('takes the size it is given', () => {
    const wrapper = mount(FrozenBadge, { props: { size: 48 } })

    expect(wrapper.attributes('style')).toContain('width: 48px')
    expect(wrapper.attributes('style')).toContain('height: 48px')
  })

  it('renders a bare icon without a fixed size when compact', () => {
    const wrapper = mount(FrozenBadge, { props: { compact: true } })

    expect(wrapper.classes()).toContain('is-compact')
    expect(wrapper.attributes('style')).toBeUndefined()
    expect(wrapper.attributes('title')).toBe('Congelado')
  })
})
