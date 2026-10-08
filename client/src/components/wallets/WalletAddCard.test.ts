import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import WalletAddCard from './WalletAddCard.vue'

describe('WalletAddCard', () => {
  it('asks to create a wallet when clicked', async () => {
    const wrapper = mount(WalletAddCard)

    expect(wrapper.text()).toContain('Nova carteira')

    await wrapper.trigger('click')

    expect(wrapper.emitted('create')).toHaveLength(1)
  })
})
