import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import Card from './Card.vue'

describe('Card', () => {
  it('renders its slot inside the card surface', () => {
    const wrapper = mount(Card, { slots: { default: '<p>Conteúdo</p>' } })

    expect(wrapper.classes()).toContain('card')
    expect(wrapper.get('p').text()).toBe('Conteúdo')
  })

  it('passes extra classes through to the root', () => {
    const wrapper = mount(Card, { attrs: { class: 'table-card' } })

    expect(wrapper.classes()).toEqual(expect.arrayContaining(['card', 'table-card']))
  })
})
