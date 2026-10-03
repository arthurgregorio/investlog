import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import Avatar from './Avatar.vue'

describe('Avatar', () => {
  it('renders the given initials', () => {
    const wrapper = mount(Avatar, { props: { initials: 'AG' } })

    expect(wrapper.text()).toBe('AG')
  })

  it('falls back to default initials and the primary color', () => {
    const wrapper = mount(Avatar)

    expect(wrapper.text()).toBe('RT')
    expect(wrapper.attributes('style')).toContain('var(--primary)')
  })

  it('applies a custom background color', () => {
    const wrapper = mount(Avatar, { props: { initials: 'AG', color: 'rgb(10, 20, 30)' } })

    expect(wrapper.attributes('style')).toContain('background: rgb(10, 20, 30)')
  })
})
