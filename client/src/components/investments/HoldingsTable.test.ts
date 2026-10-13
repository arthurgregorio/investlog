import { afterEach, describe, expect, it } from 'vitest'
import { defineComponent, nextTick } from 'vue'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import HoldingsTable from './HoldingsTable.vue'
import type { HoldingRow } from '@/types'

const HoldingDrawerStub = defineComponent({
  name: 'HoldingDrawer',
  props: {
    row: { type: Object, required: true },
    convertToDisplayCurrency: { type: Boolean, required: true },
  },
  emits: ['close', 'deleted', 'position-added', 'relocated'],
  template: '<div data-testid="drawer">{{ row.id }}</div>',
})

const positionModalProps = {
  holdingId: { type: String, required: true },
  walletId: { type: String, required: true },
  kind: { type: String, required: true },
  walletCurrency: { type: String, required: true },
}

const AddPositionModalStub = defineComponent({
  name: 'AddPositionModal',
  props: positionModalProps,
  emits: ['added', 'close'],
  template: '<div data-testid="add-position-modal">{{ holdingId }}</div>',
})

const UpdatePriceModalStub = defineComponent({
  name: 'UpdatePriceModal',
  props: { ...positionModalProps, initialValue: { type: Number, default: null } },
  emits: ['updated', 'close'],
  template: '<div data-testid="update-price-modal">{{ holdingId }}:{{ initialValue }}</div>',
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

const fundRow: HoldingRow = {
  ...firstRow,
  id: 'holding-3',
  kind: 'FUNDS',
  ticker: null,
  name: 'Tesouro IPCA+',
  quantity: null,
  currentPrice: null,
  currentValue: 3300,
}

interface TableProps {
  rows: HoldingRow[]
  page: number
  pageSize: number
  totalElements: number
  loading: boolean
  showWalletColumn: boolean
  convertToDisplayCurrency: boolean
  sortable: boolean
  sortKey: string | null
  sortDirection: 'asc' | 'desc'
}

let activeWrapper: VueWrapper | undefined

function mountTable(props: Partial<TableProps> = {}) {
  const wrapper = mount(HoldingsTable, {
    props: {
      rows: [firstRow, secondRow],
      loading: false,
      page: 0,
      pageSize: 20,
      totalElements: 2,
      ...props,
    },
    attachTo: document.body,
    global: {
      plugins: [createTestingPinia()],
      stubs: {
        HoldingDrawer: HoldingDrawerStub,
        AddPositionModal: AddPositionModalStub,
        UpdatePriceModal: UpdatePriceModalStub,
      },
    },
  })
  activeWrapper = wrapper
  return wrapper
}

type TableWrapper = ReturnType<typeof mountTable>

function bodyRows(wrapper: TableWrapper) {
  return wrapper.findAll('tbody tr.inv-row')
}

function headerCell(wrapper: TableWrapper, label: string) {
  return wrapper.findAll('thead th').find((cell) => cell.text() === label)!
}

async function openFirstRow(wrapper: TableWrapper) {
  await bodyRows(wrapper)[0].trigger('click')
}

async function emitFromDrawer(wrapper: TableWrapper, event: string) {
  wrapper.getComponent(HoldingDrawerStub).vm.$emit(event)
  await flushPromises()
}

describe('HoldingsTable', () => {
  afterEach(() => {
    activeWrapper?.unmount()
    activeWrapper = undefined
  })

  it('renders one row per holding and marks the frozen one', () => {
    const wrapper = mountTable()

    const rows = bodyRows(wrapper)
    expect(rows).toHaveLength(2)
    expect(rows[0].classes()).not.toContain('is-frozen')
    expect(rows[1].classes()).toContain('is-frozen')
    expect(rows[1].find('[data-testid="frozen-tag"]').exists()).toBe(true)
  })

  it('heads the columns with the slim layout and a screen-reader label for the actions', () => {
    const wrapper = mountTable({ showWalletColumn: true })

    expect(wrapper.findAll('thead th').map((cell) => cell.text())).toEqual([
      'Ativo',
      'Carteira · tipo',
      'Qtd.',
      'Preço médio',
      'Preço atual',
      'Valor atual',
      'Resultado',
      'Ações',
    ])
    expect(wrapper.get('thead th.c-act .is-sr-only').text()).toBe('Ações')
  })

  it('names the second column Tipo and drops the wallet reference when there is no wallet column', () => {
    const wrapper = mountTable()

    expect(wrapper.findAll('thead th')[1].text()).toBe('Tipo')
    expect(bodyRows(wrapper)[0].find('.wallet-ref').exists()).toBe(false)
    expect(bodyRows(wrapper)[0].find('.type-tag').exists()).toBe(true)
  })

  it('renders plain headers unless the table is sortable', () => {
    expect(mountTable({ showWalletColumn: true }).find('.sort-th').exists()).toBe(false)

    expect(mountTable({ sortable: true, showWalletColumn: true }).findAll('.sort-th')).toHaveLength(
      5,
    )
  })

  it('only sorts by wallet when the wallet column is shown', () => {
    expect(mountTable({ sortable: true }).findAll('.sort-th')).toHaveLength(4)
  })

  it('sorts the Ativo column by asset only when the table is sortable', async () => {
    expect(headerCell(mountTable(), 'Ativo').classes()).not.toContain('sort-th')

    const wrapper = mountTable({ sortable: true })
    const assetHeader = headerCell(wrapper, 'Ativo')
    expect(assetHeader.classes()).toEqual(['sort-th'])

    await assetHeader.trigger('click')

    expect(wrapper.emitted('sort')).toEqual([['asset']])
  })

  it('emits the sort key of the clicked header and marks the active one', async () => {
    const wrapper = mountTable({ sortable: true, sortKey: 'gainPct', sortDirection: 'asc' })

    expect(headerCell(wrapper, 'Resultado').find('.sort-icon-active').exists()).toBe(true)
    expect(headerCell(wrapper, 'Valor atual').find('.sort-icon-active').exists()).toBe(false)

    await headerCell(wrapper, 'Valor atual').trigger('click')

    expect(wrapper.emitted('sort')).toEqual([['current']])
  })

  it('keeps quantity and average price as plain headers even when sortable', () => {
    const wrapper = mountTable({ sortable: true })

    expect(headerCell(wrapper, 'Qtd.').classes()).not.toContain('sort-th')
    expect(headerCell(wrapper, 'Preço médio').classes()).not.toContain('sort-th')
  })

  it('opens the drawer for a clicked row and highlights that row', async () => {
    const wrapper = mountTable({ convertToDisplayCurrency: true })

    await openFirstRow(wrapper)

    const drawer = wrapper.getComponent(HoldingDrawerStub)
    expect(drawer.text()).toBe('holding-1')
    expect(drawer.props('convertToDisplayCurrency')).toBe(true)
    expect(bodyRows(wrapper)[0].classes()).toContain('is-current')
    expect(bodyRows(wrapper)[1].classes()).not.toContain('is-current')
  })

  it('opens the drawer from the ticker button too', async () => {
    const wrapper = mountTable()

    await bodyRows(wrapper)[1].get('button.open-holding').trigger('click')

    expect(wrapper.getComponent(HoldingDrawerStub).text()).toBe('holding-2')
  })

  it('switches the drawer to another row', async () => {
    const wrapper = mountTable()

    await openFirstRow(wrapper)
    await bodyRows(wrapper)[1].trigger('click')

    expect(wrapper.findAllComponents(HoldingDrawerStub)).toHaveLength(1)
    expect(wrapper.getComponent(HoldingDrawerStub).text()).toBe('holding-2')
  })

  it('closes the drawer and hands focus back to the row it was opened from', async () => {
    const wrapper = mountTable()
    await openFirstRow(wrapper)

    await emitFromDrawer(wrapper, 'close')

    expect(wrapper.findComponent(HoldingDrawerStub).exists()).toBe(false)
    expect(bodyRows(wrapper)[0].classes()).not.toContain('is-current')
    expect(document.activeElement).toBe(bodyRows(wrapper)[0].get('button.open-holding').element)
  })

  it('closes and reports a removed holding', async () => {
    const wrapper = mountTable()
    await openFirstRow(wrapper)

    await emitFromDrawer(wrapper, 'deleted')

    expect(wrapper.findComponent(HoldingDrawerStub).exists()).toBe(false)
    expect(wrapper.emitted('holding-changed')).toHaveLength(1)
  })

  it('closes and reports a relocated holding', async () => {
    const wrapper = mountTable()
    await openFirstRow(wrapper)

    await emitFromDrawer(wrapper, 'relocated')

    expect(wrapper.findComponent(HoldingDrawerStub).exists()).toBe(false)
    expect(wrapper.emitted('relocated')).toHaveLength(1)
  })

  it('keeps the drawer open and reports an added position', async () => {
    const wrapper = mountTable()
    await openFirstRow(wrapper)

    await emitFromDrawer(wrapper, 'position-added')

    expect(wrapper.findComponent(HoldingDrawerStub).exists()).toBe(true)
    expect(wrapper.emitted('position-added')).toHaveLength(1)
  })

  it('closes the drawer when told to from outside', async () => {
    const wrapper = mountTable()
    await openFirstRow(wrapper)

    await wrapper.vm.closeDrawer()
    await nextTick()

    expect(wrapper.findComponent(HoldingDrawerStub).exists()).toBe(false)
  })

  it('does nothing when asked to close a drawer that is not open', async () => {
    const wrapper = mountTable()

    await wrapper.vm.closeDrawer()

    expect(wrapper.findComponent(HoldingDrawerStub).exists()).toBe(false)
  })

  it('closes the drawer when its holding drops out of the rows', async () => {
    const wrapper = mountTable()
    await openFirstRow(wrapper)

    await wrapper.setProps({ rows: [secondRow] })

    expect(wrapper.findComponent(HoldingDrawerStub).exists()).toBe(false)
    await wrapper.setProps({ rows: [firstRow, secondRow] })
    expect(wrapper.findComponent(HoldingDrawerStub).exists()).toBe(false)
  })

  it('keeps the drawer open when the rows reload with its holding still there', async () => {
    const wrapper = mountTable()
    await openFirstRow(wrapper)

    await wrapper.setProps({ rows: [{ ...firstRow, gain: 10 }, secondRow] })

    expect(wrapper.getComponent(HoldingDrawerStub).text()).toBe('holding-1')
  })

  it('opens the purchase modal from the row without opening the drawer', async () => {
    const wrapper = mountTable()

    await bodyRows(wrapper)[0].get('[data-testid="row-add-position"]').trigger('click')

    expect(wrapper.get('[data-testid="add-position-modal"]').text()).toBe('holding-1')
    expect(wrapper.findComponent(HoldingDrawerStub).exists()).toBe(false)
    wrapper.getComponent(AddPositionModalStub).vm.$emit('added')
    wrapper.getComponent(AddPositionModalStub).vm.$emit('close')
    await nextTick()
    expect(wrapper.emitted('position-added')).toHaveLength(1)
    expect(wrapper.find('[data-testid="add-position-modal"]').exists()).toBe(false)
  })

  it('opens the price modal from the row with the current price, or the value for a fund', async () => {
    const wrapper = mountTable({ rows: [firstRow, fundRow] })

    await bodyRows(wrapper)[0].get('[data-testid="row-update-price"]').trigger('click')
    expect(wrapper.get('[data-testid="update-price-modal"]').text()).toBe('holding-1:34.8')
    wrapper.getComponent(UpdatePriceModalStub).vm.$emit('updated')
    wrapper.getComponent(UpdatePriceModalStub).vm.$emit('close')
    await nextTick()
    expect(wrapper.emitted('position-added')).toHaveLength(1)
    expect(wrapper.find('[data-testid="update-price-modal"]').exists()).toBe(false)

    await bodyRows(wrapper)[1].get('[data-testid="row-update-price"]').trigger('click')
    expect(wrapper.get('[data-testid="update-price-modal"]').text()).toBe('holding-3:3300')
    expect(wrapper.findComponent(HoldingDrawerStub).exists()).toBe(false)
  })

  it('shows no pager when every row fits on one page', () => {
    expect(mountTable().find('.pagination').exists()).toBe(false)
  })

  it('closes the drawer and emits the zero-based page when the pager moves', async () => {
    const wrapper = mountTable({ page: 0, pageSize: 1, totalElements: 2 })
    await openFirstRow(wrapper)

    await wrapper.get('.pagination-next').trigger('click')

    expect(wrapper.emitted('page-change')).toEqual([[1]])
    expect(wrapper.findComponent(HoldingDrawerStub).exists()).toBe(false)
  })

  it('shows the loading overlay only while loading', () => {
    expect(mountTable().find('.loading-overlay').exists()).toBe(false)
    expect(mountTable({ loading: true }).find('.loading-overlay').exists()).toBe(true)
  })
})
