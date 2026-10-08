import { describe, expect, it } from 'vitest'
import { defineComponent, nextTick } from 'vue'
import { mount } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import HoldingsTable from './HoldingsTable.vue'
import type { HoldingRow } from '@/types'

const HoldingDetailPanelStub = defineComponent({
  name: 'HoldingDetailPanel',
  props: { row: { type: Object, required: true } },
  emits: ['deleted', 'position-added', 'relocated'],
  template: '<div data-testid="detail-panel">{{ row.id }}</div>',
})

const firstRow: HoldingRow = {
  id: 'holding-1',
  kind: 'STOCKS',
  name: 'Petróleo Brasileiro',
  ticker: 'PETR4',
  typeLabel: 'Ação PN',
  segmentLabel: null,
  walletId: 'wallet-1',
  walletName: 'Carteira B3',
  walletCurrency: 'BRL',
  quantity: 200,
  costBasis: 5652,
  currentPrice: 34.8,
  currentValue: 6960,
  gain: 1308,
  gainPct: 23.1,
  frozen: false,
}

const secondRow: HoldingRow = { ...firstRow, id: 'holding-2', ticker: 'VALE3', frozen: true }

interface TableProps {
  page: number
  pageSize: number
  totalElements: number
  loading: boolean
  showWalletColumn: boolean
  sortable: boolean
  sortKey: string | null
  sortDirection: 'asc' | 'desc'
}

function mountTable(props: Partial<TableProps> = {}) {
  return mount(HoldingsTable, {
    props: {
      rows: [firstRow, secondRow],
      loading: false,
      page: 0,
      pageSize: 20,
      totalElements: 2,
      ...props,
    },
    global: {
      plugins: [createTestingPinia()],
      stubs: { HoldingDetailPanel: HoldingDetailPanelStub },
    },
  })
}

type TableWrapper = ReturnType<typeof mountTable>

function bodyRows(wrapper: TableWrapper) {
  return wrapper.findAll('tbody tr.inv-row')
}

function headerCell(wrapper: TableWrapper, label: string) {
  return wrapper.findAll('thead th').find((cell) => cell.text() === label)!
}

async function expandFirstRow(wrapper: TableWrapper) {
  await bodyRows(wrapper)[0].trigger('click')
}

async function emitFromPanel(wrapper: TableWrapper, event: string) {
  wrapper.getComponent(HoldingDetailPanelStub).vm.$emit(event)
  await nextTick()
}

describe('HoldingsTable', () => {
  it('renders one row per holding and marks the frozen one', () => {
    const wrapper = mountTable()

    const rows = bodyRows(wrapper)
    expect(rows).toHaveLength(2)
    expect(rows[0].classes()).not.toContain('is-frozen')
    expect(rows[1].classes()).toContain('is-frozen')
    expect(rows[1].find('[data-testid="frozen-tag"]').exists()).toBe(true)
  })

  it('leaves the wallet column out by default and adds it on request', () => {
    expect(headerCell(mountTable(), 'Carteira')).toBeUndefined()

    const wrapper = mountTable({ showWalletColumn: true })
    expect(wrapper.findAll('thead th')[1].text()).toBe('Carteira')
    expect(bodyRows(wrapper)[0].find('.wallet-ref').exists()).toBe(true)
  })

  it('renders plain headers unless the table is sortable', () => {
    expect(mountTable({ showWalletColumn: true }).find('.sort-th').exists()).toBe(false)

    expect(mountTable({ sortable: true, showWalletColumn: true }).findAll('.sort-th')).toHaveLength(
      5,
    )
  })

  it('emits the sort key of the clicked header and marks the active one', async () => {
    const wrapper = mountTable({ sortable: true, sortKey: 'gain', sortDirection: 'asc' })

    expect(headerCell(wrapper, 'Resultado').find('.sort-icon-active').exists()).toBe(true)
    expect(headerCell(wrapper, 'Investido').find('.sort-icon-active').exists()).toBe(false)

    await headerCell(wrapper, 'Investido').trigger('click')

    expect(wrapper.emitted('sort')).toEqual([['invested']])
  })

  it('expands a row into the detail panel spanning every column', async () => {
    const wrapper = mountTable({ showWalletColumn: true })

    await expandFirstRow(wrapper)

    expect(bodyRows(wrapper)[0].classes()).toContain('is-open')
    const detailCell = wrapper.get('tr.detail-row td')
    expect(detailCell.attributes('colspan')).toBe('8')
    expect(detailCell.get('[data-testid="detail-panel"]').text()).toBe('holding-1')
  })

  it('keeps one row open at a time and closes it on a second click', async () => {
    const wrapper = mountTable()

    await expandFirstRow(wrapper)
    await bodyRows(wrapper)[1].trigger('click')
    expect(wrapper.findAll('tr.detail-row')).toHaveLength(1)
    expect(wrapper.get('tr.detail-row').text()).toBe('holding-2')
    expect(wrapper.get('tr.detail-row td').attributes('colspan')).toBe('7')

    await bodyRows(wrapper)[1].trigger('click')
    expect(wrapper.find('tr.detail-row').exists()).toBe(false)
  })

  it('collapses and reports a removed holding', async () => {
    const wrapper = mountTable()
    await expandFirstRow(wrapper)

    await emitFromPanel(wrapper, 'deleted')

    expect(wrapper.find('tr.detail-row').exists()).toBe(false)
    expect(wrapper.emitted('holding-changed')).toHaveLength(1)
  })

  it('collapses and reports a relocated holding', async () => {
    const wrapper = mountTable()
    await expandFirstRow(wrapper)

    await emitFromPanel(wrapper, 'relocated')

    expect(wrapper.find('tr.detail-row').exists()).toBe(false)
    expect(wrapper.emitted('relocated')).toHaveLength(1)
  })

  it('keeps the row open and reports an added position', async () => {
    const wrapper = mountTable()
    await expandFirstRow(wrapper)

    await emitFromPanel(wrapper, 'position-added')

    expect(wrapper.find('tr.detail-row').exists()).toBe(true)
    expect(wrapper.emitted('position-added')).toHaveLength(1)
  })

  it('collapses the open row when told to from outside', async () => {
    const wrapper = mountTable()
    await expandFirstRow(wrapper)

    wrapper.vm.collapse()
    await nextTick()

    expect(wrapper.find('tr.detail-row').exists()).toBe(false)
  })

  it('shows no pager when every row fits on one page', () => {
    expect(mountTable().find('.pagination').exists()).toBe(false)
  })

  it('collapses the open row and emits the zero-based page when the pager moves', async () => {
    const wrapper = mountTable({ page: 0, pageSize: 1, totalElements: 2 })
    await expandFirstRow(wrapper)

    await wrapper.get('.pagination-next').trigger('click')

    expect(wrapper.emitted('page-change')).toEqual([[1]])
    expect(wrapper.find('tr.detail-row').exists()).toBe(false)
  })

  it('shows the loading overlay only while loading', () => {
    expect(mountTable().find('.loading-overlay').exists()).toBe(false)
    expect(mountTable({ loading: true }).find('.loading-overlay').exists()).toBe(true)
  })
})
