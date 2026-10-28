import { afterEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import WalletLedgerTable from './WalletLedgerTable.vue'
import GainChip from '@/components/ui/GainChip.vue'
import TablePagination from '@/components/ui/TablePagination.vue'
import { cashApi } from '@/api/cash'
import { useWalletDetailStore } from '@/stores/walletDetail'
import { useWalletLedgerStore } from '@/stores/walletLedger'
import { useWalletsStore } from '@/stores/wallets'
import type { WalletLedgerEntry } from '@/types'

afterEach(() => {
  vi.restoreAllMocks()
})

function dialogButton(label: string) {
  return Array.from(document.body.querySelectorAll<HTMLButtonElement>('.dialog button')).find(
    (button) => button.textContent?.trim() === label,
  )
}

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
    originalAmount: null,
    originalCurrency: null,
    rate: null,
    cashUsed: null,
    newMoney: null,
    cashBalance: 0,
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

  it('adds the profit of a withdrawal and of a reinvestment, and names where each one went', () => {
    const { wrapper } = mountTable([withdrawal, reinvestment])

    const [withdrawalRow, reinvestmentRow] = rowsOf(wrapper)
    expect(withdrawalRow.get('[data-testid="ledger-amount"]').text()).toContain('200,00')
    expect(withdrawalRow.findComponent(GainChip).props()).toMatchObject({ value: 80, cur: 'BRL' })
    expect(withdrawalRow.get('[data-testid="ledger-detail"]').text()).toBe('→ caixa')
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
      'Compras, aportes, resgates, transferências e saques desta carteira aparecem aqui.',
    )
  })

  describe('cash', () => {
    const transferIn = entryOf({
      id: 'transfer-1',
      type: 'TRANSFER_IN',
      holdingId: null,
      holdingName: null,
      ticker: null,
      quantity: null,
      amount: 4256,
      counterpartName: 'Ações EUA',
      originalAmount: 800,
      originalCurrency: 'USD',
      rate: 5.32,
      cashBalance: 4256,
    })
    const transferOut = entryOf({
      id: 'transfer-2',
      type: 'TRANSFER_OUT',
      holdingId: null,
      holdingName: null,
      ticker: null,
      quantity: null,
      amount: 500,
      counterpartName: 'Reserva',
      cashBalance: 3756,
    })
    const bankWithdrawal = entryOf({
      id: 'cash-withdrawal-1',
      type: 'CASH_WITHDRAWAL',
      holdingId: null,
      holdingName: null,
      ticker: null,
      quantity: null,
      amount: 1000,
      counterpartName: 'Itaú',
      cashBalance: 2756,
    })

    it('labels transfers and bank withdrawals as cash events with their counterpart', () => {
      const { wrapper } = mountTable([transferIn, transferOut, bankWithdrawal])

      const rows = rowsOf(wrapper)
      expect(rows.map((row) => row.get('.wallet-ledger-event').text())).toEqual([
        'Transferência recebida',
        'Transferência enviada',
        'Saque para o banco',
      ])
      expect(rows.map((row) => row.get('.t-ticker').text())).toEqual(['Caixa', 'Caixa', 'Caixa'])
      expect(rows.map((row) => row.get('[data-testid="ledger-detail"]').text())).toEqual([
        '← Ações EUA · US$ 800,00 a R$ 5,32',
        '→ Reserva',
        '→ Itaú',
      ])
    })

    it('leaves the rate out of a transfer in the same currency and the bank out when none was given', () => {
      const { wrapper } = mountTable([
        { ...transferIn, originalCurrency: 'BRL' },
        { ...transferIn, id: 'transfer-3', originalCurrency: null },
        { ...bankWithdrawal, counterpartName: null },
      ])

      expect(rowsOf(wrapper).map((row) => row.get('[data-testid="ledger-detail"]').text())).toEqual(
        ['← Ações EUA', '← Ações EUA', ''],
      )
    })

    it('leaves out the destination of a reinvestment without one and the conversion of a transfer without a rate', () => {
      const { wrapper } = mountTable([
        { ...reinvestment, counterpartName: null },
        { ...transferIn, originalAmount: null, rate: null },
      ])

      expect(rowsOf(wrapper).map((row) => row.get('[data-testid="ledger-detail"]').text())).toEqual(
        ['', '← Ações EUA'],
      )
    })

    it('says how a purchase was funded', () => {
      const { wrapper } = mountTable([
        { ...purchase, id: 'lot-1', cashUsed: 200, newMoney: 430 },
        { ...purchase, id: 'lot-2', cashUsed: 630, newMoney: 0 },
        { ...purchase, id: 'lot-3', cashUsed: 0, newMoney: 630 },
      ])

      expect(rowsOf(wrapper).map((row) => row.get('[data-testid="ledger-detail"]').text())).toEqual(
        ['Do caixa R$ 200,00 · novo R$ 430,00', 'Do caixa R$ 630,00', 'Dinheiro novo'],
      )
    })

    it('shows the cash balance after every event', () => {
      const { wrapper } = mountTable([transferIn, bankWithdrawal])

      expect(
        rowsOf(wrapper).map((row) => row.get('[data-testid="ledger-cash-balance"]').text()),
      ).toEqual(['R$ 4.256,00', 'R$ 2.756,00'])
    })

    it('offers a delete button on cash events only', () => {
      const { wrapper } = mountTable([purchase, withdrawal, transferIn, bankWithdrawal])

      expect(
        rowsOf(wrapper).map((row) => row.find('[data-testid="ledger-delete-cash-event"]').exists()),
      ).toEqual([false, false, true, true])
    })

    it.each([
      ['a transfer', transferIn, 'deleteTransfer', 'transfer-1'],
      ['a bank withdrawal', bankWithdrawal, 'deleteBankWithdrawal', 'cash-withdrawal-1'],
    ] as const)(
      'deletes %s after confirming and reloads the wallet',
      async (_label, entry, method, entryId) => {
        const deletion = vi.spyOn(cashApi, method).mockResolvedValue()
        const { wrapper, walletLedgerStore } = mountTable([entry])

        await wrapper.get('[data-testid="ledger-delete-cash-event"]').trigger('click')
        await flushPromises()
        expect(document.body.querySelector('.dialog .modal-card-title')?.textContent).toContain(
          'Excluir movimentação',
        )
        dialogButton('Excluir')!.click()
        await flushPromises()

        expect(deletion).toHaveBeenCalledWith('wallet-1', entryId)
        expect(walletLedgerStore.refresh).toHaveBeenCalled()
        expect(useWalletDetailStore().refresh).toHaveBeenCalled()
        expect(useWalletsStore().refresh).toHaveBeenCalled()
      },
    )
  })

  it('renders nothing before the first load', () => {
    const { wrapper } = mountTable([], false)

    expect(wrapper.find('[data-testid="wallet-ledger-empty"]').exists()).toBe(false)
    expect(wrapper.find('table').exists()).toBe(false)
  })
})
