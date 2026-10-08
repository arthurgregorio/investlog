import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import {
  ASSET_TYPE_KIND_TABS,
  useAssetTypeActions,
  type AssetTypeKind,
} from './useAssetTypeActions'
import { useTypesListStore } from '@/stores/typesList'
import type { AssetType } from '@/types'

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

function setup(initialKind: AssetTypeKind) {
  const kind = ref<AssetTypeKind>(initialKind)
  const store = useTypesListStore()
  store.stockTypes = [{ id: 'stock-1', name: 'Ordinária', usageCount: 0 }]
  store.stockSegments = [segment]
  vi.spyOn(store, 'addStockSegment').mockResolvedValue(segment)
  vi.spyOn(store, 'updateStockSegment').mockResolvedValue(segment)
  vi.spyOn(store, 'removeStockSegment').mockResolvedValue()
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

  it('offers one tab per kind in display order', () => {
    expect(ASSET_TYPE_KIND_TABS.map((tab) => tab.value)).toEqual(['stock', 'fund', 'segment'])
  })

  it('follows the active kind for its copy and its list', () => {
    const { kind, actions } = setup('stock')

    expect(actions.types.value.map((type) => type.name)).toEqual(['Ordinária'])

    kind.value = 'segment'

    expect(actions.copy.value.noun).toBe('segmento')
    expect(actions.types.value).toEqual([segment])
  })

  it('creates a trimmed segment and ignores a blank name', async () => {
    const { store, actions } = setup('segment')

    actions.addType()
    expect(dialogPrompt.mock.lastCall![0].title).toBe('Novo segmento')
    await answer(dialogPrompt, '   ')
    await answer(dialogPrompt, '  Saúde ')

    expect(store.addStockSegment).toHaveBeenCalledTimes(1)
    expect(store.addStockSegment).toHaveBeenCalledWith('Saúde')
    expect(toastOpen).toHaveBeenCalledWith({ message: 'Segmento criado.', type: 'is-success' })
  })

  it('renames only when the name changed', async () => {
    const { store, actions } = setup('segment')

    actions.renameType(segment)
    await answer(dialogPrompt, 'Energia')
    await answer(dialogPrompt, 'Energia limpa')

    expect(store.updateStockSegment).toHaveBeenCalledTimes(1)
    expect(store.updateStockSegment).toHaveBeenCalledWith('segment-1', 'Energia limpa')
  })

  it('removes after a danger confirmation that escapes the name', async () => {
    const { store, actions } = setup('segment')

    actions.confirmRemoveType({ ...segment, name: '<b>x</b>' })
    const options = dialogConfirm.mock.lastCall![0]
    await options.onConfirm()

    expect(options.title).toBe('Remover segmento')
    expect(options.type).toBe('is-danger')
    expect(options.message).toContain('&lt;b&gt;x&lt;/b&gt;')
    expect(store.removeStockSegment).toHaveBeenCalledWith('segment-1')
    expect(toastOpen).toHaveBeenCalledWith({ message: 'Segmento removido.', type: 'is-success' })
  })
})
