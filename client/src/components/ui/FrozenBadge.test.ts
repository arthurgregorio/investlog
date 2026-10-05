import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import FrozenBadge from './FrozenBadge.vue'

describe('FrozenBadge', () => {
  it('renders a snowflake in the ice colour with the Congelado title and accessible name and no text', () => {
    const wrapper = mount(FrozenBadge)

    expect(wrapper.attributes('title')).toBe('Congelado')
    expect(wrapper.attributes('aria-label')).toBe('Congelado')
    expect(wrapper.attributes('role')).toBe('img')
    expect(wrapper.attributes('data-testid')).toBe('frozen-tag')
    expect(wrapper.classes()).toContain('has-text-info-on-scheme')
    expect(wrapper.find('.mdi-snowflake').exists()).toBe(true)
    expect(wrapper.text()).toBe('')
  })

  it('fills a 36px square in the ticker badge slot', () => {
    const wrapper = mount(FrozenBadge)

    expect(wrapper.classes()).toContain('ticker-badge')
    expect(wrapper.attributes('style')).toContain('width: 36px')
    expect(wrapper.attributes('style')).toContain('height: 36px')
  })

  it('renders a small bare icon without the square when compact', () => {
    const wrapper = mount(FrozenBadge, { props: { compact: true } })

    expect(wrapper.classes()).not.toContain('ticker-badge')
    expect(wrapper.classes()).toContain('is-small')
    expect(wrapper.attributes('style')).toBeUndefined()
    expect(wrapper.attributes('title')).toBe('Congelado')
    expect(wrapper.find('.mdi-snowflake').exists()).toBe(true)
  })
})
