import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import {
  ASSET_TYPE_KIND_TABS,
  useAssetTypeActions,
  type AssetTypeKind,
} from './useAssetTypeActions'
import { useTypesListStore } from '@/stores/typesList'
import type { AssetType, HoldingType } from '@/types'

const { dialogPrompt, dialogConfirm, toastOpen } = vi.hoisted(() => ({
  dialogPrompt: vi.fn(),
  dialogConfirm: vi.fn(),
  toastOpen: vi.fn(),
}))

vi.mock('buefy', async (importOriginal) => ({
  ...(await importOriginal<typeof import('buefy')>()),
  useDialog: () => ({ prompt: dialogPrompt, confirm: dialogConfirm }),
  useToast: () => ({ open: toastOpen }),
}))

vi.mock('@/api/assetTypes', () => ({ assetTypesApi: {} }))

const segment: AssetType = { id: 'segment-1', name: 'Energia', usageCount: 0 }
const holdingType: HoldingType = {
  id: 'type-1',
  name: 'Ordinária',
  kinds: ['STOCKS', 'FUNDS'],
  usageCount: 0,
}

function setup(initialKind: AssetTypeKind) {
  const kind = ref<AssetTypeKind>(initialKind)
  const store = useTypesListStore()
  store.holdingTypes = [holdingType]
  store.segments = [segment]
  vi.spyOn(store, 'addSegment').mockResolvedValue(segment)
  vi.spyOn(store, 'updateSegment').mockResolvedValue(segment)
  vi.spyOn(store, 'removeSegment').mockResolvedValue()
  vi.spyOn(store, 'removeHoldingType').mockResolvedValue()
  return { kind, store, actions: useAssetTypeActions(kind) }
}

async function answer(mock: typeof dialogPrompt, value?: string) {
  await mock.mock.lastCall![0].onConfirm(value)
}

describe('useAssetTypeActions', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('offers the Tipos and Segmentos tabs', () => {
    expect(ASSET_TYPE_KIND_TABS).toEqual([
      { value: 'type', label: 'Tipos' },
      { value: 'segment', label: 'Segmentos' },
    ])
  })

  it('follows the active kind for its copy and its list', () => {
    const { kind, actions } = setup('type')

    expect(actions.types.value).toEqual([holdingType])
    expect(actions.copy.value.noun).toBe('tipo')

    kind.value = 'segment'

    expect(actions.copy.value.noun).toBe('segmento')
    expect(actions.types.value).toEqual([segment])
  })

  it('opens the type modal empty to create and filled to edit, instead of a prompt', () => {
    const { actions } = setup('type')

    actions.addType()
    expect(actions.holdingTypeModal.value).toEqual({ holdingType: null })

    actions.renameType(holdingType)
    expect(actions.holdingTypeModal.value).toEqual({ holdingType })

    actions.closeHoldingTypeModal()
    expect(actions.holdingTypeModal.value).toBeNull()
    expect(dialogPrompt).not.toHaveBeenCalled()
  })

  it('creates a trimmed segment and ignores a blank name', async () => {
    const { store, actions } = setup('segment')

    actions.addType()
    expect(dialogPrompt.mock.lastCall![0].title).toBe('Novo segmento')
    await answer(dialogPrompt, '   ')
    await answer(dialogPrompt, '  Saúde ')

    expect(store.addSegment).toHaveBeenCalledTimes(1)
    expect(store.addSegment).toHaveBeenCalledWith('Saúde')
    expect(toastOpen).toHaveBeenCalledWith({ message: 'Segmento criado.', type: 'is-success' })
    expect(actions.holdingTypeModal.value).toBeNull()
  })

  it('renames a segment only when the name changed', async () => {
    const { store, actions } = setup('segment')

    actions.renameType(segment)
    await answer(dialogPrompt, 'Energia')
    await answer(dialogPrompt, 'Energia limpa')

    expect(store.updateSegment).toHaveBeenCalledTimes(1)
    expect(store.updateSegment).toHaveBeenCalledWith('segment-1', 'Energia limpa')
  })

  it('removes a segment after a danger confirmation that escapes the name', async () => {
    const { store, actions } = setup('segment')

    actions.confirmRemoveType({ ...segment, name: '<b>x</b>' })
    const options = dialogConfirm.mock.lastCall![0]
    await options.onConfirm()

    expect(options.title).toBe('Remover segmento')
    expect(options.type).toBe('is-danger')
    expect(options.message).toContain('&lt;b&gt;x&lt;/b&gt;')
    expect(store.removeSegment).toHaveBeenCalledWith('segment-1')
    expect(toastOpen).toHaveBeenCalledWith({ message: 'Segmento removido.', type: 'is-success' })
  })

  it('removes a holding type through the type action', async () => {
    const { store, actions } = setup('type')

    actions.confirmRemoveType(holdingType)
    await dialogConfirm.mock.lastCall![0].onConfirm()

    expect(store.removeHoldingType).toHaveBeenCalledWith('type-1')
    expect(store.removeSegment).not.toHaveBeenCalled()
    expect(toastOpen).toHaveBeenCalledWith({ message: 'Tipo removido.', type: 'is-success' })
  })
})
