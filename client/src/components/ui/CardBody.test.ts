import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import CardBody from './CardBody.vue'

describe('CardBody', () => {
  it('renders its slot inside the padded body region', () => {
    const wrapper = mount(CardBody, { slots: { default: '<span>Corpo</span>' } })

    expect(wrapper.classes()).toContain('card-body')
    expect(wrapper.get('span').text()).toBe('Corpo')
  })
})
