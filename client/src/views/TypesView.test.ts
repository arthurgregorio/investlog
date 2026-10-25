import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, type VueWrapper } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import TypesView from './TypesView.vue'
import { useAuthStore } from '@/stores/auth'
import { useTypesListStore } from '@/stores/typesList'
import type { AssetType, HoldingType } from '@/types'
import { expectDialogShowsLiterally } from '@/test/expectDialogShowsLiterally'

vi.mock('@/api/assetTypes', () => ({ assetTypesApi: {} }))

const holdingTypes: HoldingType[] = [
  { id: 'type-1', name: 'Ordinária', kinds: ['STOCKS'], usageCount: 0 },
  { id: 'type-2', name: 'Preferencial', kinds: ['STOCKS', 'FUNDS'], usageCount: 3 },
  { id: 'type-3', name: 'Multimercado', kinds: ['CRYPTO', 'FUNDS'], usageCount: 0 },
]
const segments: AssetType[] = [
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
    global: { plugins: [createTestingPinia()], config: { errorHandler: () => undefined } },
    attachTo: document.body,
  })
  activeWrapper = wrapper
  const typesListStore = useTypesListStore()
  typesListStore.holdingTypes = holdingTypes
  typesListStore.segments = segments
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

function modalNameInput() {
  return document.body.querySelector('[data-testid="holding-type-name"]') as HTMLInputElement
}

function modalKindInput(kind: string) {
  return document.body.querySelector(
    `[data-testid="holding-type-kind-${kind}"] input`,
  ) as HTMLInputElement
}

async function typeName(value: string) {
  const input = modalNameInput()
  input.value = value
  input.dispatchEvent(new Event('input', { bubbles: true }))
  await flushPromises()
}

async function toggleKind(kind: string) {
  modalKindInput(kind).click()
  await flushPromises()
}

function modalSubmit() {
  return document.body.querySelector('[data-testid="holding-type-submit"]') as HTMLButtonElement
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

  it('has two tabs, Tipos and Segmentos, with Tipos first', async () => {
    const { wrapper } = await mountView()

    expect(wrapper.findAll('[role="tab"]').map((tab) => tab.text())).toEqual(['Tipos', 'Segmentos'])
    expect(tableNames(wrapper)).toEqual(['Ordinária', 'Preferencial', 'Multimercado'])
  })

  it('shows the kinds each type applies to as tags', async () => {
    const { wrapper } = await mountView()

    expect(wrapper.findAll('thead th').map((cell) => cell.text())).toEqual([
      'Nome',
      'Aplica-se a',
      'Investimentos',
      'Ações',
    ])
    expect(wrapper.findAll('[data-testid="type-kinds"]').map((cell) => cell.text())).toEqual([
      'Ações',
      'AçõesFundos',
      'CriptoFundos',
    ])
    expect(wrapper.findAll('tbody .c-num').map((cell) => cell.text())).toEqual(['0', '3', '0'])
  })

  it('shows the empty state only once the store has loaded', async () => {
    const { wrapper, typesListStore } = await mountView({ loaded: false })
    typesListStore.holdingTypes = []
    await flushPromises()

    expect(wrapper.find('.empty').exists()).toBe(false)

    typesListStore.loaded = true
    await flushPromises()

    expect(wrapper.find('.empty-title').text()).toBe('Nenhum tipo ainda')
  })

  it('hides every management control from a non-admin user', async () => {
    const { wrapper } = await mountView({ isAdmin: false })

    expect(wrapper.text()).not.toContain('Novo tipo')
    expect(wrapper.findAll('thead th').map((cell) => cell.text())).toEqual([
      'Nome',
      'Aplica-se a',
      'Investimentos',
    ])
    expect(wrapper.find('.c-act').exists()).toBe(false)
  })

  it('creates a type applying to two kinds from the modal, trimming the name', async () => {
    const { wrapper, typesListStore } = await mountView()

    await newTypeButton(wrapper).trigger('click')
    await flushPromises()
    await typeName('  Units  ')
    await toggleKind('STOCKS')
    await toggleKind('FUNDS')
    modalSubmit().click()
    await flushPromises()

    expect(typesListStore.addHoldingType).toHaveBeenCalledWith('Units', ['STOCKS', 'FUNDS'])
    expect(document.body.textContent).toContain('Tipo criado.')
    expect(document.body.querySelector('.modal-card')).toBeNull()
  })

  it('sends the kinds in display order whatever order they were ticked', async () => {
    const { wrapper, typesListStore } = await mountView()

    await newTypeButton(wrapper).trigger('click')
    await flushPromises()
    await typeName('Cripto e ações')
    await toggleKind('CRYPTO')
    await toggleKind('STOCKS')
    modalSubmit().click()
    await flushPromises()

    expect(typesListStore.addHoldingType).toHaveBeenCalledWith('Cripto e ações', [
      'STOCKS',
      'CRYPTO',
    ])
  })

  it('does not let a type be created with no kind or with a blank name', async () => {
    const { wrapper, typesListStore } = await mountView()

    await newTypeButton(wrapper).trigger('click')
    await flushPromises()
    await typeName('Sem classe')

    expect(modalSubmit().disabled).toBe(true)
    modalSubmit().click()
    await flushPromises()

    await toggleKind('CRYPTO')
    expect(modalSubmit().disabled).toBe(false)
    await typeName('   ')
    expect(modalSubmit().disabled).toBe(true)

    expect(typesListStore.addHoldingType).not.toHaveBeenCalled()
  })

  it('edits a type in the modal, prefilled with its name and kinds', async () => {
    const { wrapper, typesListStore } = await mountView()

    await wrapper.findAll('tbody tr')[1].find('button').trigger('click')
    await flushPromises()

    expect(modalNameInput().value).toBe('Preferencial')
    expect(modalKindInput('STOCKS').checked).toBe(true)
    expect(modalKindInput('CRYPTO').checked).toBe(false)
    expect(modalKindInput('FUNDS').checked).toBe(true)

    await toggleKind('CRYPTO')
    await typeName('Preferencial nominativa')
    modalSubmit().click()
    await flushPromises()

    expect(typesListStore.updateHoldingType).toHaveBeenCalledWith(
      'type-2',
      'Preferencial nominativa',
      ['STOCKS', 'CRYPTO', 'FUNDS'],
    )
    expect(document.body.textContent).toContain('Tipo atualizado.')
  })

  it('keeps the modal open when the server refuses the change', async () => {
    const { wrapper, typesListStore } = await mountView()
    vi.mocked(typesListStore.updateHoldingType).mockRejectedValue(new Error('em uso'))

    await wrapper.findAll('tbody tr')[1].find('button').trigger('click')
    await flushPromises()
    await toggleKind('FUNDS')
    modalSubmit().click()
    await flushPromises()

    expect(document.body.querySelector('.modal-card')).not.toBeNull()
    expect(document.body.textContent).not.toContain('Tipo atualizado.')
  })

  it('closes the modal on Cancelar without saving', async () => {
    const { wrapper, typesListStore } = await mountView()

    await newTypeButton(wrapper).trigger('click')
    await flushPromises()
    bodyButton('Cancelar')?.click()
    await flushPromises()

    expect(document.body.querySelector('.modal-card')).toBeNull()
    expect(typesListStore.addHoldingType).not.toHaveBeenCalled()
  })

  it('removes an unused type after confirmation', async () => {
    const { wrapper, typesListStore } = await mountView()

    await wrapper.findAll('tbody tr')[0].findAll('button')[1].trigger('click')
    await confirmDialog('Remover')

    expect(typesListStore.removeHoldingType).toHaveBeenCalledWith('type-1')
    expect(document.body.textContent).toContain('Tipo removido.')
  })

  it('shows a type name containing markup literally in the remove confirmation', async () => {
    const markupName = '<img src=x onerror=alert(1)>'
    const { wrapper, typesListStore } = await mountView()
    typesListStore.holdingTypes = [
      { id: 'type-9', name: markupName, kinds: ['STOCKS'], usageCount: 0 },
    ]
    await flushPromises()

    await wrapper.findAll('tbody tr')[0].findAll('button')[1].trigger('click')
    await flushPromises()

    expectDialogShowsLiterally(markupName)
  })

  it('does not remove anything when the confirmation is cancelled', async () => {
    const { wrapper, typesListStore } = await mountView()

    await wrapper.findAll('tbody tr')[0].findAll('button')[1].trigger('click')
    await confirmDialog('Cancelar')

    expect(typesListStore.removeHoldingType).not.toHaveBeenCalled()
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
      await mounted.wrapper.findAll('[role="tab"]')[1].trigger('click')
      return mounted
    }

    it('lists the segments with their usage counts and no kinds column', async () => {
      const { wrapper } = await openSegments()

      expect(tableNames(wrapper)).toEqual(['Energia', 'Tecnologia'])
      expect(wrapper.findAll('thead th').map((cell) => cell.text())).toEqual([
        'Nome',
        'Investimentos',
        'Ações',
      ])
      expect(wrapper.findAll('tbody .c-num').map((cell) => cell.text())).toEqual(['0', '2'])
      expect(wrapper.find('.set-desc').text()).toContain('Setores de atuação')
    })

    it('creates, renames and removes a segment through the segment actions', async () => {
      const { wrapper, typesListStore } = await openSegments()

      await newSegmentButton(wrapper).trigger('click')
      await answerPrompt('Saúde', 'Criar')
      expect(typesListStore.addSegment).toHaveBeenCalledWith('Saúde')
      expect(typesListStore.addHoldingType).not.toHaveBeenCalled()
      expect(document.body.textContent).toContain('Segmento criado.')

      await wrapper.findAll('tbody tr')[0].find('button').trigger('click')
      await answerPrompt('Energia elétrica', 'Salvar')
      expect(typesListStore.updateSegment).toHaveBeenCalledWith('segment-1', 'Energia elétrica')

      await wrapper.findAll('tbody tr')[0].findAll('button')[1].trigger('click')
      await confirmDialog('Remover')
      expect(typesListStore.removeSegment).toHaveBeenCalledWith('segment-1')
      expect(document.body.textContent).toContain('Segmento removido.')
    })

    it('blocks removing a segment in use', async () => {
      const { wrapper } = await openSegments()

      const deleteButton = wrapper.findAll('tbody tr')[1].findAll('button')[1]
      expect(deleteButton.attributes('disabled')).toBeDefined()
    })

    it('shows the segment empty state', async () => {
      const { wrapper, typesListStore } = await openSegments()
      typesListStore.segments = []
      await flushPromises()

      expect(wrapper.find('.empty-title').text()).toBe('Nenhum segmento ainda')
      expect(newSegmentButton(wrapper).exists()).toBe(true)
    })
  })
})
