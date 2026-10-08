import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, type VueWrapper } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import TypesView from './TypesView.vue'
import { useAuthStore } from '@/stores/auth'
import { useTypesListStore } from '@/stores/typesList'
import type { AssetType } from '@/types'
import { expectDialogShowsLiterally } from '@/test/expectDialogShowsLiterally'

vi.mock('@/api/assetTypes', () => ({ assetTypesApi: {} }))

const stockTypes: AssetType[] = [
  { id: 'stock-type-1', name: 'Ordinária', usageCount: 0 },
  { id: 'stock-type-2', name: 'Preferencial', usageCount: 3 },
]
const fundTypes: AssetType[] = [{ id: 'fund-type-1', name: 'Multimercado', usageCount: 0 }]
const stockSegments: AssetType[] = [
  { id: 'segment-1', name: 'Energia', usageCount: 0 },
  { id: 'segment-2', name: 'Tecnologia', usageCount: 2 },
]

let activeWrapper: VueWrapper | undefined

function flushPromises() {
  return new Promise((resolve) => setTimeout(resolve, 0))
}

async function mountView(options: { isAdmin?: boolean; loaded?: boolean } = {}) {
  const { isAdmin = true, loaded = true } = options
  const wrapper = mount(TypesView, {
    global: { plugins: [createTestingPinia()] },
    attachTo: document.body,
  })
  activeWrapper = wrapper
  const typesListStore = useTypesListStore()
  typesListStore.stockTypes = stockTypes
  typesListStore.fundTypes = fundTypes
  typesListStore.stockSegments = stockSegments
  typesListStore.loaded = loaded
  useAuthStore().session = {
    name: 'Admin',
    email: 'admin@admin.com',
    role: isAdmin ? 'ADMIN' : 'USER',
    status: 'APPROVED',
    authProvider: 'LOCAL',
    demoModeEnabled: false,
  }
  await flushPromises()
  return { wrapper, typesListStore }
}

function bodyButton(label: string) {
  return Array.from(document.body.querySelectorAll('button')).find(
    (button) => button.textContent?.trim() === label,
  ) as HTMLButtonElement | undefined
}

async function answerPrompt(value: string, confirmLabel: string) {
  await flushPromises()
  const input = document.body.querySelector('.dialog input') as HTMLInputElement
  input.value = value
  input.dispatchEvent(new Event('input', { bubbles: true }))
  bodyButton(confirmLabel)?.dispatchEvent(new MouseEvent('click', { bubbles: true }))
  await flushPromises()
}

async function confirmDialog(label: string) {
  await flushPromises()
  bodyButton(label)?.dispatchEvent(new MouseEvent('click', { bubbles: true }))
  await flushPromises()
}

function tableNames(wrapper: VueWrapper) {
  return wrapper.findAll('tbody td:first-child').map((cell) => cell.text())
}

function newTypeButton(wrapper: VueWrapper) {
  return wrapper.findAll('button').find((button) => button.text() === 'Novo tipo')!
}

describe('TypesView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    activeWrapper?.unmount()
    activeWrapper = undefined
  })

  it('loads the types store on mount', async () => {
    const { typesListStore } = await mountView()

    expect(typesListStore.load).toHaveBeenCalledTimes(1)
  })

  it('lists the stock types first with their usage counts and switches to the fund types', async () => {
    const { wrapper } = await mountView()

    expect(tableNames(wrapper)).toEqual(['Ordinária', 'Preferencial'])
    expect(wrapper.findAll('tbody .c-num').map((cell) => cell.text())).toEqual(['0', '3'])

    await wrapper.findAll('.seg-tab')[1].trigger('click')

    expect(tableNames(wrapper)).toEqual(['Multimercado'])
    expect(wrapper.find('.set-desc').text()).toContain('fundo')
  })

  it('shows the empty state for the active kind only once the store has loaded', async () => {
    const { wrapper, typesListStore } = await mountView({ loaded: false })
    typesListStore.stockTypes = []
    await flushPromises()

    expect(wrapper.find('.empty').exists()).toBe(false)

    typesListStore.loaded = true
    await flushPromises()

    expect(wrapper.find('.empty-title').text()).toBe('Nenhum tipo ainda')
    expect(wrapper.find('.empty-text').text()).toContain('ação')
  })

  it('hides every management control from a non-admin user', async () => {
    const { wrapper } = await mountView({ isAdmin: false })

    expect(wrapper.text()).not.toContain('Novo tipo')
    expect(wrapper.findAll('thead th').map((cell) => cell.text())).toEqual([
      'Nome',
      'Investimentos',
    ])
    expect(wrapper.find('.c-act').exists()).toBe(false)
  })

  it('creates a stock type from the prompt, trimming the name', async () => {
    const { wrapper, typesListStore } = await mountView()

    await newTypeButton(wrapper).trigger('click')
    await answerPrompt('  Units  ', 'Criar')

    expect(typesListStore.addStockType).toHaveBeenCalledWith('Units')
    expect(typesListStore.addFundType).not.toHaveBeenCalled()
    expect(document.body.textContent).toContain('Tipo criado.')
  })

  it('creates a fund type when the fund tab is active', async () => {
    const { wrapper, typesListStore } = await mountView()

    await wrapper.findAll('.seg-tab')[1].trigger('click')
    await newTypeButton(wrapper).trigger('click')
    await answerPrompt('Renda fixa', 'Criar')

    expect(typesListStore.addFundType).toHaveBeenCalledWith('Renda fixa')
    expect(typesListStore.addStockType).not.toHaveBeenCalled()
  })

  it('ignores a blank name typed into the create prompt', async () => {
    const { wrapper, typesListStore } = await mountView()

    await newTypeButton(wrapper).trigger('click')
    await answerPrompt('   ', 'Criar')

    expect(typesListStore.addStockType).not.toHaveBeenCalled()
    expect(document.body.textContent).not.toContain('Tipo criado.')
  })

  it('renames a stock type and skips the call when the name is unchanged', async () => {
    const { wrapper, typesListStore } = await mountView()

    await wrapper.findAll('tbody tr')[0].find('button').trigger('click')
    await answerPrompt('Ordinária', 'Salvar')

    expect(typesListStore.updateStockType).not.toHaveBeenCalled()

    await wrapper.findAll('tbody tr')[0].find('button').trigger('click')
    await answerPrompt('Ordinária nominativa', 'Salvar')

    expect(typesListStore.updateStockType).toHaveBeenCalledWith(
      'stock-type-1',
      'Ordinária nominativa',
    )
    expect(document.body.textContent).toContain('Tipo renomeado.')
  })

  it('renames a fund type through the fund action', async () => {
    const { wrapper, typesListStore } = await mountView()

    await wrapper.findAll('.seg-tab')[1].trigger('click')
    await wrapper.findAll('tbody tr')[0].find('button').trigger('click')
    await answerPrompt('Multimercado macro', 'Salvar')

    expect(typesListStore.updateFundType).toHaveBeenCalledWith('fund-type-1', 'Multimercado macro')
  })

  it('removes an unused stock type after confirmation', async () => {
    const { wrapper, typesListStore } = await mountView()

    await wrapper.findAll('tbody tr')[0].findAll('button')[1].trigger('click')
    await confirmDialog('Remover')

    expect(typesListStore.removeStockType).toHaveBeenCalledWith('stock-type-1')
    expect(document.body.textContent).toContain('Tipo removido.')
  })

  it('shows a type name containing markup literally in the remove confirmation', async () => {
    const markupName = '<img src=x onerror=alert(1)>'
    const { wrapper, typesListStore } = await mountView()
    typesListStore.stockTypes = [{ id: 'stock-type-3', name: markupName, usageCount: 0 }]
    await flushPromises()

    await wrapper.findAll('tbody tr')[0].findAll('button')[1].trigger('click')
    await flushPromises()

    expectDialogShowsLiterally(markupName)
  })

  it('removes a fund type through the fund action', async () => {
    const { wrapper, typesListStore } = await mountView()

    await wrapper.findAll('.seg-tab')[1].trigger('click')
    await wrapper.findAll('tbody tr')[0].findAll('button')[1].trigger('click')
    await confirmDialog('Remover')

    expect(typesListStore.removeFundType).toHaveBeenCalledWith('fund-type-1')
  })

  it('does not remove anything when the confirmation is cancelled', async () => {
    const { wrapper, typesListStore } = await mountView()

    await wrapper.findAll('tbody tr')[0].findAll('button')[1].trigger('click')
    await confirmDialog('Cancelar')

    expect(typesListStore.removeStockType).not.toHaveBeenCalled()
  })

  it('disables the delete control of a type that is in use', async () => {
    const { wrapper } = await mountView()

    const usedRowButtons = wrapper.findAll('tbody tr')[1].findAll('button')

    expect(usedRowButtons).toHaveLength(2)
    expect(usedRowButtons[1].attributes('disabled')).toBeDefined()
  })

  it('shows the loading overlay only while the types store is loading', async () => {
    const { wrapper, typesListStore } = await mountView()

    expect(wrapper.find('.loading-overlay').exists()).toBe(false)

    typesListStore.loading = true
    await flushPromises()
    expect(wrapper.findAll('.loading-overlay')).toHaveLength(1)

    typesListStore.loading = false
    await flushPromises()
    expect(wrapper.find('.loading-overlay').exists()).toBe(false)
  })

  describe('segments tab', () => {
    function newSegmentButton(wrapper: VueWrapper) {
      return wrapper.findAll('button').find((button) => button.text() === 'Novo segmento')!
    }

    async function openSegments() {
      const mounted = await mountView()
      await mounted.wrapper.findAll('.seg-tab')[2].trigger('click')
      return mounted
    }

    it('is the third tab and lists the segments with their usage counts', async () => {
      const { wrapper } = await openSegments()

      expect(wrapper.findAll('.seg-tab').map((tab) => tab.text())).toEqual([
        'Tipos de ação',
        'Tipos de fundo',
        'Segmentos',
      ])
      expect(tableNames(wrapper)).toEqual(['Energia', 'Tecnologia'])
      expect(wrapper.findAll('tbody .c-num').map((cell) => cell.text())).toEqual(['0', '2'])
      expect(wrapper.find('.set-desc').text()).toContain('Setores de atuação')
    })

    it('creates, renames and removes a segment through the segment actions', async () => {
      const { wrapper, typesListStore } = await openSegments()

      await newSegmentButton(wrapper).trigger('click')
      await answerPrompt('Saúde', 'Criar')
      expect(typesListStore.addStockSegment).toHaveBeenCalledWith('Saúde')
      expect(typesListStore.addStockType).not.toHaveBeenCalled()
      expect(document.body.textContent).toContain('Segmento criado.')

      await wrapper.findAll('tbody tr')[0].find('button').trigger('click')
      await answerPrompt('Energia elétrica', 'Salvar')
      expect(typesListStore.updateStockSegment).toHaveBeenCalledWith(
        'segment-1',
        'Energia elétrica',
      )

      await wrapper.findAll('tbody tr')[0].findAll('button')[1].trigger('click')
      await confirmDialog('Remover')
      expect(typesListStore.removeStockSegment).toHaveBeenCalledWith('segment-1')
      expect(document.body.textContent).toContain('Segmento removido.')
    })

    it('blocks removing a segment in use', async () => {
      const { wrapper } = await openSegments()

      const deleteButton = wrapper.findAll('tbody tr')[1].findAll('button')[1]
      expect(deleteButton.attributes('disabled')).toBeDefined()
    })

    it('shows the segment empty state', async () => {
      const { wrapper, typesListStore } = await openSegments()
      typesListStore.stockSegments = []
      await flushPromises()

      expect(wrapper.find('.empty-title').text()).toBe('Nenhum segmento ainda')
      expect(newSegmentButton(wrapper).exists()).toBe(true)
    })
  })
})
