import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import PageHeader from './PageHeader.vue'

describe('PageHeader', () => {
  it('renders the title as the page heading and the description below it', () => {
    const wrapper = mount(PageHeader, {
      props: { title: 'Carteiras', description: 'Carteiras podem ter tipos distintos' },
    })

    expect(wrapper.get('h1.page-title').text()).toBe('Carteiras')
    expect(wrapper.get('.page-desc').text()).toBe('Carteiras podem ter tipos distintos')
  })

  it('omits the description and the actions area when neither is given', () => {
    const wrapper = mount(PageHeader, { props: { title: 'Tipos' } })

    expect(wrapper.find('.page-desc').exists()).toBe(false)
    expect(wrapper.find('.head-actions').exists()).toBe(false)
  })

  it('renders the default slot inside the actions area', () => {
    const wrapper = mount(PageHeader, {
      props: { title: 'Visão geral' },
      slots: { default: '<button type="button">Carteiras</button>' },
    })

    expect(wrapper.get('.head-actions button').text()).toBe('Carteiras')
  })
})
