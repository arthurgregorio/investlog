import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import EntityCard from './EntityCard.vue'

describe('EntityCard', () => {
  it('renders the name inside an entity card with no optional areas', () => {
    const wrapper = mount(EntityCard, { props: { name: 'Carteira principal' } })

    expect(wrapper.classes()).toContain('entity-card')
    expect(wrapper.get('.entity-name').text()).toBe('Carteira principal')
    expect(wrapper.find('.entity-tags').exists()).toBe(false)
    expect(wrapper.find('.entity-actions').exists()).toBe(false)
    expect(wrapper.find('.entity-foot').exists()).toBe(false)
  })

  it('places every slot in its own area of the card', () => {
    const wrapper = mount(EntityCard, {
      props: { name: 'Ana' },
      slots: {
        decoration: '<div class="stripe" />',
        leading: '<span class="leading-icon" />',
        tags: '<span class="tag">ADMIN</span>',
        actions: '<button type="button">Detalhes</button>',
        default: '<p class="body-text">Investido</p>',
        foot: '<span class="foot-text">ana@example.com</span>',
      },
    })

    expect(wrapper.find('.stripe').exists()).toBe(true)
    expect(wrapper.find('.entity-head > .leading-icon').exists()).toBe(true)
    expect(wrapper.get('.entity-tags .tag').text()).toBe('ADMIN')
    expect(wrapper.get('.entity-actions button').text()).toBe('Detalhes')
    expect(wrapper.get('.card-body .body-text').text()).toBe('Investido')
    expect(wrapper.get('.entity-foot .foot-text').text()).toBe('ana@example.com')
  })
})
