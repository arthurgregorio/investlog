import { describe, expect, it } from 'vitest'
import { nextTick } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import WalletDetailTabs from './WalletDetailTabs.vue'
import WalletLedgerTable from './WalletLedgerTable.vue'
import { useWalletLedgerStore } from '@/stores/walletLedger'

async function mountTabs() {
  const pinia = createTestingPinia()
  const walletLedgerStore = useWalletLedgerStore()
  walletLedgerStore.totalElements = 3
  walletLedgerStore.loaded = true
  const wrapper = mount(WalletDetailTabs, {
    props: { walletId: 'wallet-1', currency: 'USD', holdingsCount: 7 },
    slots: { default: '<p class="holdings-slot">holdings</p>' },
    global: { plugins: [pinia] },
  })
  await flushPromises()
  return wrapper
}

describe('WalletDetailTabs', () => {
  it('labels the investments tab and the ledger tab with their counts', async () => {
    const wrapper = await mountTabs()

    const tabs = wrapper.findAll('.tabs li')
    expect(tabs).toHaveLength(2)
    expect(tabs[0].text()).toMatch(/^Investimentos\s*7$/)
    expect(tabs[1].text()).toMatch(/^Movimentações\s*3$/)
  })

  it('opens on the slotted investments and holds the wallet ledger in the second tab', async () => {
    const wrapper = await mountTabs()

    expect(wrapper.findAll('.tabs li')[0].classes()).toContain('is-active')
    expect(wrapper.get('.holdings-slot').isVisible()).toBe(true)

    await wrapper.findAll('.tabs li a')[1].trigger('click')
    await nextTick()

    expect(wrapper.findAll('.tabs li')[1].classes()).toContain('is-active')
    expect(wrapper.get('[data-testid="wallet-ledger-empty"]').isVisible()).toBe(true)
    expect(wrapper.findComponent(WalletLedgerTable).props()).toEqual({
      walletId: 'wallet-1',
      currency: 'USD',
    })
  })
})
