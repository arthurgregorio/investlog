import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, type VueWrapper } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import PriceCurrenciesView from './PriceCurrenciesView.vue'
import { stockPriceSyncApi } from '@/api/stockPriceSync'
import { cryptoPriceSyncApi } from '@/api/cryptoPriceSync'
import { useAuthStore } from '@/stores/auth'
import { useConfigurationsStore } from '@/stores/configurations'
import { useRatesStore } from '@/stores/rates'
import type { CurrencyRate } from '@/types'

vi.mock('@/api/stockPriceSync', () => ({ stockPriceSyncApi: { forceSync: vi.fn() } }))
vi.mock('@/api/cryptoPriceSync', () => ({ cryptoPriceSyncApi: { forceSync: vi.fn() } }))

const rates: CurrencyRate[] = [
  { currencyCode: 'BRL', rate: 1, isBase: true },
  { currencyCode: 'USD', rate: 5.2, isBase: false },
  { currencyCode: 'EUR', rate: 6.1, isBase: false },
]

let activeWrapper: VueWrapper | undefined

function flushPromises() {
  return new Promise((resolve) => setTimeout(resolve, 0))
}

async function mountView(
  options: { demoModeEnabled?: boolean; configurations?: Record<string, string> } = {},
) {
  const { demoModeEnabled = false, configurations = {} } = options
  const wrapper = mount(PriceCurrenciesView, {
    global: { plugins: [createTestingPinia()], config: { errorHandler: () => undefined } },
    attachTo: document.body,
  })
  activeWrapper = wrapper
  const ratesStore = useRatesStore()
  ratesStore.rates = rates
  const configurationsStore = useConfigurationsStore()
  configurationsStore.values = configurations
  useAuthStore().session = {
    name: 'Admin',
    email: 'admin@admin.com',
    role: 'ADMIN',
    status: 'APPROVED',
    authProvider: 'LOCAL',
    demoModeEnabled,
  }
  await flushPromises()
  return { wrapper, ratesStore, configurationsStore }
}

function rateRow(wrapper: VueWrapper, currencyCode: string) {
  return wrapper.findAll('.rate-row').find((row) => row.find('.cur-chip').text() === currencyCode)!
}

function switches(wrapper: VueWrapper) {
  return wrapper.findAll<HTMLInputElement>('.switch input[type="checkbox"]')
}

function syncButton(wrapper: VueWrapper, index: number) {
  return wrapper.findAll('.set-action-item button')[index]
}

describe('PriceCurrenciesView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    activeWrapper?.unmount()
    activeWrapper = undefined
  })

  it('loads the rates and the configurations on mount', async () => {
    const { ratesStore, configurationsStore } = await mountView()

    expect(ratesStore.load).toHaveBeenCalledTimes(1)
    expect(configurationsStore.load).toHaveBeenCalledTimes(1)
  })

  it('lists every rate and marks the base currency instead of offering an input', async () => {
    const { wrapper } = await mountView()

    expect(wrapper.findAll('.rate-row')).toHaveLength(3)
    expect(wrapper.find('.base-chip').text()).toContain('BRL')
    expect(rateRow(wrapper, 'BRL').find('.rate-base').text()).toContain('Moeda base')
    expect(rateRow(wrapper, 'BRL').find('input').exists()).toBe(false)
    expect(rateRow(wrapper, 'USD').find('input').element.value).toBe('5.2')
    expect(rateRow(wrapper, 'EUR').find('input').element.value).toBe('6.1')
  })

  it('upserts an edited rate as a non-base rate when the input loses focus', async () => {
    const { wrapper, ratesStore } = await mountView()

    const input = rateRow(wrapper, 'USD').find('input')
    await input.setValue('5.5')
    await input.trigger('blur')
    await flushPromises()

    expect(ratesStore.upsertRate).toHaveBeenCalledWith('USD', 5.5, false)
    expect(document.body.textContent).toContain('Taxa de conversão atualizada.')
  })

  it.each([['0'], ['-3'], ['']])(
    'discards the draft rate "%s" without calling the store',
    async (draft) => {
      const { wrapper, ratesStore } = await mountView()

      const input = rateRow(wrapper, 'USD').find('input')
      await input.setValue(draft)
      await input.trigger('blur')
      await flushPromises()

      expect(ratesStore.upsertRate).not.toHaveBeenCalled()
      expect(document.body.textContent).not.toContain('Taxa de conversão atualizada.')
    },
  )

  it('does nothing when the input is blurred without being edited', async () => {
    const { wrapper, ratesStore } = await mountView()

    await rateRow(wrapper, 'EUR').find('input').trigger('blur')
    await flushPromises()

    expect(ratesStore.upsertRate).not.toHaveBeenCalled()
  })

  it('reflects each stored toggle value on its switch', async () => {
    const { wrapper } = await mountView({
      configurations: {
        stock_price_sync_enabled: 'true',
        crypto_price_sync_enabled: 'false',
        usd_price_sync_enabled: 'true',
      },
    })

    expect(switches(wrapper).map((toggle) => toggle.element.checked)).toEqual([true, false, true])
  })

  it('writes the stock toggle through the configurations store', async () => {
    const { wrapper, configurationsStore } = await mountView()

    await switches(wrapper)[0].setValue(true)
    await flushPromises()

    expect(configurationsStore.updateConfiguration).toHaveBeenCalledWith(
      'stock_price_sync_enabled',
      'true',
    )
    expect(document.body.textContent).toContain('Sincronização automática ativada.')
  })

  it('writes the crypto toggle through the configurations store', async () => {
    const { wrapper, configurationsStore } = await mountView({
      configurations: { crypto_price_sync_enabled: 'true' },
    })

    await switches(wrapper)[1].setValue(false)
    await flushPromises()

    expect(configurationsStore.updateConfiguration).toHaveBeenCalledWith(
      'crypto_price_sync_enabled',
      'false',
    )
    expect(document.body.textContent).toContain('Sincronização automática desativada.')
  })

  it('writes the dollar toggle through the configurations store', async () => {
    const { wrapper, configurationsStore } = await mountView()

    await switches(wrapper)[2].setValue(true)
    await flushPromises()

    expect(configurationsStore.updateConfiguration).toHaveBeenCalledWith(
      'usd_price_sync_enabled',
      'true',
    )
  })

  it('forces a stock price sync and confirms with a toast', async () => {
    vi.mocked(stockPriceSyncApi.forceSync).mockResolvedValue(undefined)
    const { wrapper } = await mountView()

    await syncButton(wrapper, 0).trigger('click')
    await flushPromises()

    expect(stockPriceSyncApi.forceSync).toHaveBeenCalledTimes(1)
    expect(cryptoPriceSyncApi.forceSync).not.toHaveBeenCalled()
    expect(document.body.textContent).toContain('Preços de ações atualizados.')
  })

  it('forces a crypto price sync and confirms with a toast', async () => {
    vi.mocked(cryptoPriceSyncApi.forceSync).mockResolvedValue(undefined)
    const { wrapper } = await mountView()

    await syncButton(wrapper, 1).trigger('click')
    await flushPromises()

    expect(cryptoPriceSyncApi.forceSync).toHaveBeenCalledTimes(1)
    expect(stockPriceSyncApi.forceSync).not.toHaveBeenCalled()
    expect(document.body.textContent).toContain('Preços de criptomoedas atualizados.')
  })

  it('leaves the sync button usable again and shows no success toast when the sync fails', async () => {
    vi.mocked(stockPriceSyncApi.forceSync).mockRejectedValue(new Error('boom'))
    const { wrapper } = await mountView()

    await syncButton(wrapper, 0).trigger('click')
    await flushPromises()

    expect(document.body.textContent).not.toContain('Preços de ações atualizados.')
    expect(syncButton(wrapper, 0).classes()).not.toContain('is-loading')
  })

  it('shows no demo-mode notice and keeps every control enabled outside demo mode', async () => {
    const { wrapper } = await mountView()

    expect(wrapper.find('.notification').exists()).toBe(false)
    expect(switches(wrapper).every((toggle) => !toggle.element.disabled)).toBe(true)
    expect(
      wrapper
        .findAll<HTMLButtonElement>('.set-action-item button')
        .every((button) => !button.element.disabled),
    ).toBe(true)
  })

  it('disables the toggles and the manual syncs in demo mode and explains why', async () => {
    const { wrapper } = await mountView({ demoModeEnabled: true })

    expect(wrapper.findAll('.notification').map((notice) => notice.text())).toEqual([
      'Indisponível no modo demonstração.',
      'Indisponível no modo demonstração.',
    ])
    expect(switches(wrapper).every((toggle) => toggle.element.disabled)).toBe(true)
    expect(
      wrapper
        .findAll<HTMLButtonElement>('.set-action-item button')
        .every((button) => button.element.disabled),
    ).toBe(true)
  })

  it('shows the loading overlay only while the rates store is loading', async () => {
    const { wrapper, ratesStore } = await mountView()

    expect(wrapper.find('.loading-overlay').exists()).toBe(false)

    ratesStore.loading = true
    await flushPromises()
    expect(wrapper.findAll('.loading-overlay')).toHaveLength(1)

    ratesStore.loading = false
    await flushPromises()
    expect(wrapper.find('.loading-overlay').exists()).toBe(false)
  })

  it('shows the loading overlay only while the configurations store is loading', async () => {
    const { wrapper, configurationsStore } = await mountView()

    expect(wrapper.find('.loading-overlay').exists()).toBe(false)

    configurationsStore.loading = true
    await flushPromises()
    expect(wrapper.findAll('.loading-overlay')).toHaveLength(1)

    configurationsStore.loading = false
    await flushPromises()
    expect(wrapper.find('.loading-overlay').exists()).toBe(false)
  })
})
