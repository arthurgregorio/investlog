import { afterEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import CreateStrategyModal from './CreateStrategyModal.vue'
import { useStrategiesStore } from '@/stores/strategies'
import { strategy } from '@/test/strategyFixtures'

let activeWrapper: VueWrapper | undefined

afterEach(() => {
  activeWrapper?.unmount()
  activeWrapper = undefined
})

describe('CreateStrategyModal', () => {
  it('creates a strategy with the trimmed name and hands it back', async () => {
    const pinia = createTestingPinia()
    const store = useStrategiesStore(pinia)
    const created = strategy({ id: 'strategy-new', name: 'Aposentadoria' })
    vi.mocked(store.create).mockResolvedValue(created)
    activeWrapper = mount(CreateStrategyModal, {
      global: { plugins: [pinia] },
      attachTo: document.body,
    })

    const button = activeWrapper.find('[data-testid="create-strategy"]')
    expect(button.attributes('disabled')).toBeDefined()

    await activeWrapper.find('input[data-testid="new-strategy-name"]').setValue('  Aposentadoria ')
    await button.trigger('click')
    await flushPromises()

    expect(store.create).toHaveBeenCalledWith('Aposentadoria')
    expect(activeWrapper.emitted('created')).toEqual([[created]])
    expect(activeWrapper.emitted('close')).toHaveLength(1)
  })

  it('creates on Enter and closes from Cancelar without creating', async () => {
    const pinia = createTestingPinia()
    const store = useStrategiesStore(pinia)
    vi.mocked(store.create).mockResolvedValue(strategy())
    activeWrapper = mount(CreateStrategyModal, {
      global: { plugins: [pinia] },
      attachTo: document.body,
    })

    const input = activeWrapper.find('input[data-testid="new-strategy-name"]')
    await input.trigger('keyup.enter')
    expect(store.create).not.toHaveBeenCalled()

    await input.setValue('Renda')
    await input.trigger('keyup.enter')
    await flushPromises()
    expect(store.create).toHaveBeenCalledWith('Renda')

    const cancel = activeWrapper.findAll('button').find((button) => button.text() === 'Cancelar')
    await cancel?.trigger('click')
    expect(activeWrapper.emitted('close')?.length).toBeGreaterThanOrEqual(2)
  })
})
