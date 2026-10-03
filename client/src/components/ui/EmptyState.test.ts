import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import EmptyState from './EmptyState.vue'

describe('EmptyState', () => {
  it('renders only the title when nothing else is given', () => {
    const wrapper = mount(EmptyState, { props: { title: 'Nada por aqui' } })

    expect(wrapper.get('.empty-title').text()).toBe('Nada por aqui')
    expect(wrapper.find('.empty-icon').exists()).toBe(false)
    expect(wrapper.find('.empty-text').exists()).toBe(false)
  })

  it('renders the icon and explanatory text when given', () => {
    const wrapper = mount(EmptyState, {
      props: { title: 'Sem dados', icon: 'chart-line', text: 'Volte amanhã.' },
    })

    expect(wrapper.find('.empty-icon .mdi-chart-line').exists()).toBe(true)
    expect(wrapper.get('.empty-text').text()).toBe('Volte amanhã.')
  })

  it('renders the action slot', () => {
    const wrapper = mount(EmptyState, {
      props: { title: 'Sem dados' },
      slots: { action: '<button type="button">Criar</button>' },
    })

    expect(wrapper.get('button').text()).toBe('Criar')
  })
})
