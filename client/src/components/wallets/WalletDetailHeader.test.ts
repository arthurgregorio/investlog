import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import WalletDetailHeader from './WalletDetailHeader.vue'
import { useHoldingsListStore } from '@/stores/holdingsList'
import { walletDetailOf } from '@/test/walletDetailFixture'
import type { HoldingRow, WalletDetail } from '@/types'

function mountHeader(detail: WalletDetail, isAdmin = true, rows: Partial<HoldingRow>[] = []) {
  const pinia = createTestingPinia()
  useHoldingsListStore().rows = rows as HoldingRow[]
  return mount(WalletDetailHeader, {
    props: { detail, isAdmin },
    global: { plugins: [pinia] },
  })
}

function actionItem(wrapper: ReturnType<typeof mountHeader>, label: string) {
  return wrapper
    .findAll('[data-testid="wallet-actions"] .dropdown-item')
    .find((item) => item.text() === label)!
}

describe('WalletDetailHeader', () => {
  it('shows the wallet identity, its figures and its activity', () => {
    const wrapper = mountHeader(
      walletDetailOf({
        activity: {
          lastTransactionDate: null,
          lastTransactionName: null,
          lastTransactionAmount: null,
          transactionCount: 1,
          walletAgeInDays: 12,
          investmentCount: 1,
        },
      }),
    )

    expect(wrapper.get('h1').text()).toBe('Detail Wallet')
    expect(wrapper.get('.type-tag').classes()).toContain('tt-stocks')
    expect(wrapper.get('.wd-currency').text()).toBe('BRL')
    expect(wrapper.get('.wd-activity').text()).toBe('1 investimento · 1 lançamento · 12 dias')
    expect(wrapper.text()).toContain('R$ 4.500,00')
    expect(wrapper.text()).toContain('R$ 4.750,00')
  })

  it('pluralises the activity and leaves the age out when it is unknown', () => {
    const wrapper = mountHeader(walletDetailOf())

    expect(wrapper.get('.wd-activity').text()).toBe('2 investimentos · 0 lançamentos')
  })

  it('colours the result by its direction and shows its percentage', () => {
    const gain = mountHeader(walletDetailOf()).get('[data-testid="wallet-result"]')
    expect(gain.classes()).toContain('gl-up')
    expect(gain.text()).toBe('+R$ 250,00 +5,56%')

    const loss = mountHeader(walletDetailOf({ gain: -40, gainPct: null })).get(
      '[data-testid="wallet-result"]',
    )
    expect(loss.classes()).toContain('gl-down')
    expect(loss.find('.wd-figure-pct').exists()).toBe(false)

    const flat = mountHeader(walletDetailOf({ gain: 0 })).get('[data-testid="wallet-result"]')
    expect(flat.classes()).toContain('gl-flat')
  })

  it('emits rename from the pencil beside the name', async () => {
    const wrapper = mountHeader(walletDetailOf())

    await wrapper.get('button[aria-label="Renomear carteira"]').trigger('click')

    expect(wrapper.emitted('rename')).toHaveLength(1)
  })

  it('emits one event per wallet action, Remover included for an admin', async () => {
    const wrapper = mountHeader(walletDetailOf())

    await actionItem(wrapper, 'Ver investimentos').trigger('click')
    await wrapper.get('[data-testid="wallet-move"]').trigger('click')
    await wrapper.get('[data-testid="wallet-reinvest"]').trigger('click')
    await wrapper.get('[data-testid="wallet-remove"]').trigger('click')

    expect(wrapper.emitted('view-holdings')).toHaveLength(1)
    expect(wrapper.emitted('move')).toHaveLength(1)
    expect(wrapper.emitted('reinvest')).toHaveLength(1)
    expect(wrapper.emitted('remove')).toHaveLength(1)
  })

  it('hides Remover from a non-admin', () => {
    const wrapper = mountHeader(walletDetailOf(), false)

    expect(wrapper.find('[data-testid="wallet-remove"]').exists()).toBe(false)
  })

  it('fills the best and worst cells with the performer ticker and its coloured gain', () => {
    const wrapper = mountHeader(
      walletDetailOf({
        bestPerformer: {
          id: 'h1',
          name: 'Itau',
          ticker: null,
          kind: 'STOCKS',
          gain: 0,
          gainPct: 0,
        },
        worstPerformer: {
          id: 'h2',
          name: 'TRX',
          ticker: 'TRXF11',
          kind: 'FUNDS',
          gain: -110,
          gainPct: -11.22,
        },
      }),
    )

    const best = wrapper.get('[data-testid="highlight-best"]')
    expect(best.text()).toContain('Itau')
    expect(best.text()).toContain('+0,00%')
    expect(best.find('.has-text-success-on-scheme').exists()).toBe(false)
    const worst = wrapper.get('[data-testid="highlight-worst"]')
    expect(worst.text()).toContain('TRXF11')
    expect(worst.get('.has-text-danger-on-scheme').text()).toBe('−11,22%')
  })

  it('names the largest position by the ticker of its loaded row', () => {
    const wrapper = mountHeader(
      walletDetailOf({ largestHoldingName: 'Petrobras', largestHoldingShare: 41.1 }),
      true,
      [{ name: 'Petrobras', ticker: 'PETR4' }],
    )

    const largest = wrapper.get('[data-testid="highlight-largest"]')
    expect(largest.text()).toContain('PETR4')
    expect(largest.text()).toContain('41,10%')
  })

  it('falls back to the largest position name when its row is not on the loaded page', () => {
    const wrapper = mountHeader(
      walletDetailOf({ largestHoldingName: 'Petrobras', largestHoldingShare: null }),
    )

    expect(wrapper.get('[data-testid="highlight-largest"]').text()).toContain('Petrobras')
  })

  it('shows a dash in every highlight cell without data', () => {
    const wrapper = mountHeader(walletDetailOf())

    const cells = wrapper.findAll('.wd-highlight-strip > *')
    expect(cells).toHaveLength(3)
    expect(cells.every((cell) => cell.text().includes('—'))).toBe(true)
  })
})
