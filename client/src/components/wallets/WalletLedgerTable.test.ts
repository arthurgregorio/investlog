import { describe, expect, it } from 'vitest'
import { nextTick } from 'vue'
import { mount } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import WalletLedgerTable from './WalletLedgerTable.vue'
import GainChip from '@/components/ui/GainChip.vue'
import TablePagination from '@/components/ui/TablePagination.vue'
import { useWalletLedgerStore } from '@/stores/walletLedger'
import type { WalletLedgerEntry } from '@/types'

function entryOf(overrides: Partial<WalletLedgerEntry>): WalletLedgerEntry {
  return {
    id: 'entry-1',
    date: '2026-09-20',
    type: 'PURCHASE',
    kind: 'STOCKS',
    holdingId: 'holding-1',
    holdingName: 'Vale',
    ticker: 'VALE3',
    quantity: 10.5,
    amount: 630,
    profit: null,
    counterpartName: null,
    ...overrides,
  }
}

const purchase = entryOf({ id: 'lot-1' })
const contribution = entryOf({
  id: 'contribution-1',
  type: 'CONTRIBUTION',
  kind: 'FUNDS',
  holdingName: 'Tesouro Selic',
  ticker: null,
  quantity: null,
  amount: 1000,
})
const withdrawal = entryOf({
  id: 'result-1',
  type: 'WITHDRAWAL',
  quantity: 4,
  amount: 200,
  profit: 80,
})
const reinvestment = entryOf({
  id: 'result-2',
  type: 'REINVESTMENT',
  quantity: 2,
  amount: 150,
  profit: -12,
  counterpartName: 'CDB Liquidez',
})
const moveIn = entryOf({
  id: 'move-1',
  type: 'MOVE_IN',
  quantity: null,
  amount: null,
  counterpartName: 'Reserva',
})
const moveOut = entryOf({
  id: 'move-2',
  type: 'MOVE_OUT',
  quantity: 3,
  amount: null,
  counterpartName: 'Outra carteira',
})

function mountTable(rows: WalletLedgerEntry[], loaded = true) {
  const pinia = createTestingPinia()
  const walletLedgerStore = useWalletLedgerStore()
  walletLedgerStore.rows = rows
  walletLedgerStore.loaded = loaded
  const wrapper = mount(WalletLedgerTable, {
    props: { walletId: 'wallet-1', currency: 'BRL' },
    global: { plugins: [pinia] },
  })
  return { wrapper, walletLedgerStore }
}

function rowsOf(wrapper: ReturnType<typeof mountTable>['wrapper']) {
  return wrapper.findAll('[data-testid="ledger-row"]')
}

describe('WalletLedgerTable', () => {
  it('labels each event type with its own tone', () => {
    const { wrapper } = mountTable([
      purchase,
      contribution,
      withdrawal,
      reinvestment,
      moveIn,
      moveOut,
    ])

    const events = rowsOf(wrapper).map((row) => row.get('.wallet-ledger-event'))
    expect(events.map((event) => event.text())).toEqual([
      'Compra',
      'Aporte',
      'Resgate',
      'Reinvestimento',
      'Entrada',
      'Saída',
    ])
    expect(events.map((event) => event.classes().includes('is-in'))).toEqual([
      true,
      true,
      false,
      false,
      true,
      false,
    ])
  })

  it('shows the date, the ticker or else the holding name, the quantity and the amount', () => {
    const { wrapper } = mountTable([purchase, contribution])

    const [purchaseRow, contributionRow] = rowsOf(wrapper)
    expect(purchaseRow.text()).toContain('20 set 2026')
    expect(purchaseRow.text()).toContain('VALE3')
    expect(purchaseRow.get('[data-testid="ledger-quantity"]').text()).toBe('10,5')
    expect(purchaseRow.get('[data-testid="ledger-amount"]').text()).toContain('630,00')
    expect(purchaseRow.get('[data-testid="ledger-detail"]').text()).toBe('')
    expect(purchaseRow.findComponent(GainChip).exists()).toBe(false)
    expect(contributionRow.text()).toContain('Tesouro Selic')
    expect(contributionRow.get('[data-testid="ledger-quantity"]').text()).toBe('—')
    expect(contributionRow.get('[data-testid="ledger-amount"]').text()).toContain('1.000,00')
  })

  it('adds the profit of a withdrawal and of a reinvestment, and names where a reinvestment went', () => {
    const { wrapper } = mountTable([withdrawal, reinvestment])

    const [withdrawalRow, reinvestmentRow] = rowsOf(wrapper)
    expect(withdrawalRow.get('[data-testid="ledger-amount"]').text()).toContain('200,00')
    expect(withdrawalRow.findComponent(GainChip).props()).toMatchObject({ value: 80, cur: 'BRL' })
    expect(withdrawalRow.get('[data-testid="ledger-detail"]').text()).toBe('')
    expect(reinvestmentRow.findComponent(GainChip).props('value')).toBe(-12)
    expect(reinvestmentRow.get('[data-testid="ledger-detail"]').text()).toBe('→ CDB Liquidez')
  })

  it('shows a move with its counterpart wallet, a dash for the amount and Tudo for a whole move', () => {
    const { wrapper } = mountTable([moveIn, moveOut])

    const [moveInRow, moveOutRow] = rowsOf(wrapper)
    expect(moveInRow.get('[data-testid="ledger-detail"]').text()).toBe('Reserva')
    expect(moveInRow.get('[data-testid="ledger-quantity"]').text()).toBe('Tudo')
    expect(moveInRow.get('[data-testid="ledger-amount"]').text()).toBe('—')
    expect(moveOutRow.get('[data-testid="ledger-detail"]').text()).toBe('Outra carteira')
    expect(moveOutRow.get('[data-testid="ledger-quantity"]').text()).toBe('3')
  })

  it('names a removed counterpart wallet', () => {
    const { wrapper } = mountTable([{ ...moveOut, counterpartName: null }])

    expect(wrapper.get('[data-testid="ledger-detail"]').text()).toBe('Carteira removida')
  })

  it('loads the requested page of this wallet', () => {
    const { wrapper, walletLedgerStore } = mountTable([purchase])

    wrapper.findComponent(TablePagination).vm.$emit('page-change', 2)

    expect(walletLedgerStore.load).toHaveBeenCalledWith('wallet-1', 2)
  })

  it('overlays the table while the ledger is loading', async () => {
    const { wrapper, walletLedgerStore } = mountTable([purchase])
    expect(wrapper.find('.loading-overlay').exists()).toBe(false)

    walletLedgerStore.loading = true
    await nextTick()

    expect(wrapper.find('.loading-overlay').exists()).toBe(true)
  })

  it('shows an empty state once loaded with nothing recorded', () => {
    const { wrapper } = mountTable([])

    const empty = wrapper.get('[data-testid="wallet-ledger-empty"]')
    expect(empty.text()).toContain('Nenhuma movimentação')
    expect(empty.text()).toContain(
      'Compras, aportes, resgates e movimentações desta carteira aparecem aqui.',
    )
  })

  it('renders nothing before the first load', () => {
    const { wrapper } = mountTable([], false)

    expect(wrapper.find('[data-testid="wallet-ledger-empty"]').exists()).toBe(false)
    expect(wrapper.find('table').exists()).toBe(false)
  })
})
