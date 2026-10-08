import { describe, expect, it } from 'vitest'
import { nextTick } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import WalletDetailTabs from './WalletDetailTabs.vue'
import { useWalletMovesStore } from '@/stores/walletMoves'

async function mountTabs() {
  const pinia = createTestingPinia()
  const walletMovesStore = useWalletMovesStore()
  walletMovesStore.totalElements = 3
  walletMovesStore.loaded = true
  const wrapper = mount(WalletDetailTabs, {
    props: { walletId: 'wallet-1', holdingsCount: 7 },
    slots: { default: '<p class="holdings-slot">holdings</p>' },
    global: { plugins: [pinia] },
  })
  await flushPromises()
  return wrapper
}

describe('WalletDetailTabs', () => {
  it('labels the investments tab and the move history tab with their counts', async () => {
    const wrapper = await mountTabs()

    const tabs = wrapper.findAll('.tabs li')
    expect(tabs).toHaveLength(2)
    expect(tabs[0].text()).toMatch(/^Investimentos\s*7$/)
    expect(tabs[1].text()).toMatch(/^Movimentações\s*3$/)
  })

  it('opens on the slotted investments and holds the move history in the second tab', async () => {
    const wrapper = await mountTabs()

    expect(wrapper.findAll('.tabs li')[0].classes()).toContain('is-active')
    expect(wrapper.get('.holdings-slot').isVisible()).toBe(true)

    await wrapper.findAll('.tabs li a')[1].trigger('click')
    await nextTick()

    expect(wrapper.findAll('.tabs li')[1].classes()).toContain('is-active')
    expect(wrapper.get('[data-testid="move-history-empty"]').isVisible()).toBe(true)
  })
})
