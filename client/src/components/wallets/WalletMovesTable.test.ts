import { describe, expect, it } from 'vitest'
import { nextTick } from 'vue'
import { mount } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import WalletMovesTable from './WalletMovesTable.vue'
import TablePagination from '@/components/ui/TablePagination.vue'
import { useWalletMovesStore } from '@/stores/walletMoves'
import type { WalletMoveRow } from '@/types'

const moveOut: WalletMoveRow = {
  id: 'move-out',
  movedAt: '2026-09-20',
  direction: 'OUT',
  kind: 'STOCKS',
  holdingName: 'Vale',
  ticker: 'VALE3',
  quantity: 10.5,
  originWalletId: 'wallet-1',
  originWalletName: 'Detail Wallet',
  destinationWalletId: 'wallet-2',
  destinationWalletName: 'Outra carteira',
}

const moveIn: WalletMoveRow = {
  id: 'move-in',
  movedAt: '2026-09-21',
  direction: 'IN',
  kind: 'FUNDS',
  holdingName: 'Tesouro Selic',
  ticker: null,
  quantity: null,
  originWalletId: 'wallet-3',
  originWalletName: 'Reserva',
  destinationWalletId: 'wallet-1',
  destinationWalletName: 'Detail Wallet',
}

function mountTable(rows: WalletMoveRow[], loaded = true) {
  const pinia = createTestingPinia()
  const walletMovesStore = useWalletMovesStore()
  walletMovesStore.rows = rows
  walletMovesStore.loaded = loaded
  const wrapper = mount(WalletMovesTable, {
    props: { walletId: 'wallet-1' },
    global: { plugins: [pinia] },
  })
  return { wrapper, walletMovesStore }
}

describe('WalletMovesTable', () => {
  it('lists each move with its direction, counterpart wallet and quantity', () => {
    const { wrapper } = mountTable([moveOut, moveIn])

    const rows = wrapper.findAll('[data-testid="move-row"]')
    expect(rows).toHaveLength(2)
    expect(rows[0].text()).toContain('20 set 2026')
    expect(rows[0].text()).toContain('VALE3')
    expect(rows[0].get('.move-direction').classes()).toContain('is-out')
    expect(rows[0].text()).toContain('Saída')
    expect(rows[0].text()).toContain('Outra carteira')
    expect(rows[0].text()).toContain('10,5')
    expect(rows[1].text()).toContain('Tesouro Selic')
    expect(rows[1].get('.move-direction').classes()).toContain('is-in')
    expect(rows[1].text()).toContain('Entrada')
    expect(rows[1].text()).toContain('Reserva')
    expect(rows[1].text()).toContain('Tudo')
  })

  it('names a removed counterpart wallet', () => {
    const { wrapper } = mountTable([{ ...moveOut, destinationWalletName: null }])

    expect(wrapper.get('[data-testid="move-row"]').text()).toContain('Carteira removida')
  })

  it('loads the requested page of this wallet', () => {
    const { wrapper, walletMovesStore } = mountTable([moveOut])

    wrapper.findComponent(TablePagination).vm.$emit('page-change', 2)

    expect(walletMovesStore.load).toHaveBeenCalledWith('wallet-1', 2)
  })

  it('overlays the table while the moves are loading', async () => {
    const { wrapper, walletMovesStore } = mountTable([moveOut])
    expect(wrapper.find('.loading-overlay').exists()).toBe(false)

    walletMovesStore.loading = true
    await nextTick()

    expect(wrapper.find('.loading-overlay').exists()).toBe(true)
  })

  it('shows an empty state once loaded with nothing moved', () => {
    const { wrapper } = mountTable([])

    expect(wrapper.get('[data-testid="move-history-empty"]').text()).toContain(
      'Nenhuma movimentação',
    )
  })

  it('renders nothing before the first load', () => {
    const { wrapper } = mountTable([], false)

    expect(wrapper.find('[data-testid="move-history-empty"]').exists()).toBe(false)
    expect(wrapper.find('table').exists()).toBe(false)
  })
})
