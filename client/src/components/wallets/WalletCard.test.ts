import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import WalletCard from './WalletCard.vue'
import { useCurrencyStore } from '@/stores/currency'
import type { WalletResponse } from '@/types'

function walletOf(overrides: Partial<WalletResponse> = {}): WalletResponse {
  return {
    id: 'wallet-1',
    name: 'Principal',
    kind: 'CRYPTO',
    currency: 'USD',
    holdingCount: 3,
    totalInvested: 1000,
    currentValue: 1200,
    gain: 200,
    gainPct: 20,
    createdAt: '2026-01-01T00:00:00Z',
    ...overrides,
  }
}

function mountCard(wallet: WalletResponse) {
  const pinia = createTestingPinia()
  const currencyStore = useCurrencyStore()
  currencyStore.displayCurrency = 'BRL'
  vi.mocked(currencyStore.convert).mockImplementation((amount) => amount * 5)
  return mount(WalletCard, { props: { wallet }, global: { plugins: [pinia] } })
}

describe('WalletCard', () => {
  it('shows the wallet identity with its kind tag and currency chip', () => {
    const wrapper = mountCard(walletOf())

    expect(wrapper.get('.entity-name').text()).toBe('Principal')
    expect(wrapper.find('.tag.tt-crypto').exists()).toBe(true)
    expect(wrapper.get('.cur-chip').text()).toBe('USD')
    expect(wrapper.find('.wallet-stripe').exists()).toBe(true)
  })

  it('shows the invested and current values converted to the display currency', () => {
    const wrapper = mountCard(walletOf())

    expect(wrapper.get('.wallet-invested').text()).toContain('5.000,00')
    expect(wrapper.get('.result-value').text()).toContain('6.000,00')
    expect(wrapper.get('.wallet-count').text()).toBe('3 ativos')
  })

  it('shows a dash for an unpriced wallet and the singular asset count', () => {
    const wrapper = mountCard(walletOf({ currentValue: null, gain: null, holdingCount: 1 }))

    expect(wrapper.get('.result-value').text()).toBe('—')
    expect(wrapper.get('.wallet-count').text()).toBe('1 ativo')
  })

  it('emits open from the details button and show-investments from the foot link', async () => {
    const wrapper = mountCard(walletOf())

    await wrapper.get('[aria-label="Detalhes da carteira"]').trigger('click')
    await wrapper
      .findAll('button')
      .find((button) => button.text() === 'Ver investimentos')!
      .trigger('click')

    expect(wrapper.emitted('open')).toHaveLength(1)
    expect(wrapper.emitted('show-investments')).toHaveLength(1)
  })
})
