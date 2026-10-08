import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, type VueWrapper } from '@vue/test-utils'
import PriceSyncActions from './PriceSyncActions.vue'
import { stockPriceSyncApi } from '@/api/stockPriceSync'
import { cryptoPriceSyncApi } from '@/api/cryptoPriceSync'

vi.mock('@/api/stockPriceSync', () => ({ stockPriceSyncApi: { forceSync: vi.fn() } }))
vi.mock('@/api/cryptoPriceSync', () => ({ cryptoPriceSyncApi: { forceSync: vi.fn() } }))

let activeWrapper: VueWrapper | undefined

function flushPromises() {
  return new Promise((resolve) => setTimeout(resolve, 0))
}

function mountActions(disabled = false) {
  activeWrapper = mount(PriceSyncActions, {
    props: { disabled },
    attachTo: document.body,
    global: { config: { errorHandler: () => undefined } },
  })
  return activeWrapper
}

describe('PriceSyncActions', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    activeWrapper?.unmount()
    activeWrapper = undefined
  })

  it('offers one sync per price source', () => {
    const wrapper = mountActions()

    expect(
      wrapper.findAll('.set-action-item').map((item) => item.text().replace(/\s+/g, ' ')),
    ).toEqual([
      'Clique para atualizar as cotações das ações agora',
      'Clique para atualizar as cotações das criptomoedas agora',
    ])
  })

  it('runs only the chosen sync and confirms with a toast', async () => {
    vi.mocked(cryptoPriceSyncApi.forceSync).mockResolvedValue(undefined)
    const wrapper = mountActions()

    await wrapper.findAll('button')[1].trigger('click')
    await flushPromises()

    expect(cryptoPriceSyncApi.forceSync).toHaveBeenCalledTimes(1)
    expect(stockPriceSyncApi.forceSync).not.toHaveBeenCalled()
    expect(document.body.textContent).toContain('Preços de criptomoedas atualizados.')
  })

  it('shows the button loading while its sync runs', async () => {
    let finish: () => void = () => undefined
    vi.mocked(stockPriceSyncApi.forceSync).mockReturnValue(
      new Promise<void>((resolve) => (finish = resolve)),
    )
    const wrapper = mountActions()

    await wrapper.findAll('button')[0].trigger('click')

    expect(wrapper.findAll('button')[0].classes()).toContain('is-loading')
    expect(wrapper.findAll('button')[1].classes()).not.toContain('is-loading')

    finish()
    await flushPromises()

    expect(wrapper.findAll('button')[0].classes()).not.toContain('is-loading')
  })

  it('disables every sync when asked to', () => {
    const wrapper = mountActions(true)

    expect(
      wrapper.findAll<HTMLButtonElement>('button').every((button) => button.element.disabled),
    ).toBe(true)
  })
})
