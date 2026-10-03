import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createTestingPinia, type TestingPinia } from '@pinia/testing'
import { createMemoryHistory, createRouter } from 'vue-router'
import TheNavbar from './TheNavbar.vue'
import { ModalKey } from '@/composables/useModals'
import { profileApi } from '@/api/profile'
import { useAppearanceStore } from '@/stores/appearance'
import { useAuthStore } from '@/stores/auth'
import { useCurrencyStore } from '@/stores/currency'
import { useOverviewStore } from '@/stores/overview'
import { useRatesStore } from '@/stores/rates'
import type { ProfileResponse, SessionResponse } from '@/types'

vi.mock('@/api/profile', () => ({
  profileApi: {
    getProfile: vi.fn(),
  },
}))

vi.mock('@/utils/appVersion', () => ({ APP_VERSION: '1.2.3' }))

const profile = {
  name: 'Arthur Gregorio',
  email: 'arthur@example.com',
  preferredCurrency: 'USD',
} as ProfileResponse

function sessionWith(overrides: Partial<SessionResponse> = {}): SessionResponse {
  return {
    name: 'Arthur Gregorio',
    email: 'arthur@example.com',
    role: 'USER',
    status: 'APPROVED',
    authProvider: 'LOCAL',
    demoModeEnabled: false,
    ...overrides,
  }
}

const modalControls = {
  openAddInvestment: vi.fn(),
  openCreateWallet: vi.fn(),
  openPasswordChange: vi.fn(),
  openTrustedDevices: vi.fn(),
}

let activeWrapper: VueWrapper | undefined
let pinia: TestingPinia

async function mountNavbar() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/', component: { template: '<div />' } }],
  })
  router.push('/')
  await router.isReady()
  activeWrapper = mount(TheNavbar, {
    global: { plugins: [pinia, router], provide: { [ModalKey as symbol]: modalControls } },
    attachTo: document.body,
  })
  await flushPromises()
  return activeWrapper
}

function dropdownItem(label: string): HTMLElement {
  const item = Array.from(document.body.querySelectorAll<HTMLElement>('.dropdown-item')).find(
    (candidate) => candidate.textContent?.includes(label),
  )
  if (!item) throw new Error(`dropdown item "${label}" not found`)
  return item
}

describe('TheNavbar', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(profileApi.getProfile).mockResolvedValue(profile)
    pinia = createTestingPinia({ initialState: { appearance: { dark: false, accent: 'teal' } } })
    useAuthStore().session = sessionWith()
  })

  afterEach(() => {
    activeWrapper?.unmount()
    activeWrapper = undefined
  })

  describe('on mount', () => {
    it('loads the profile, hydrates the display currency and loads the rates', async () => {
      await mountNavbar()

      expect(profileApi.getProfile).toHaveBeenCalledOnce()
      expect(useCurrencyStore().hydrate).toHaveBeenCalledWith('USD')
      expect(useRatesStore().load).toHaveBeenCalledOnce()
    })

    it('shows the profile name, email and initials once loaded', async () => {
      const wrapper = await mountNavbar()

      expect(wrapper.get('.nu-name').text()).toBe('Arthur Gregorio')
      expect(wrapper.get('.nu-sub').text()).toBe('arthur@example.com')
      expect(wrapper.get('.avatar').text()).toBe('AG')
    })

    it('shows placeholders until the profile resolves', async () => {
      vi.mocked(profileApi.getProfile).mockReturnValue(new Promise(() => {}))

      const wrapper = await mountNavbar()

      expect(wrapper.get('.nu-name').text()).toBe('...')
      expect(wrapper.get('.avatar').text()).toBe('?')
    })

    it('builds initials from at most the first two words', async () => {
      vi.mocked(profileApi.getProfile).mockResolvedValue({
        ...profile,
        name: 'maria  da silva',
      })

      const wrapper = await mountNavbar()

      expect(wrapper.get('.avatar').text()).toBe('MD')
    })
  })

  describe('currency toggle', () => {
    it('shows the current display currency', async () => {
      useCurrencyStore().displayCurrency = 'EUR'

      const wrapper = await mountNavbar()

      expect(wrapper.get('.currency-toggle').text()).toBe('EUR')
    })

    it('cycles to the next available currency, then refreshes the overview', async () => {
      useRatesStore().rates = [
        { currencyCode: 'BRL', rate: 1, isBase: true },
        { currencyCode: 'USD', rate: 5, isBase: false },
        { currencyCode: 'EUR', rate: 6, isBase: false },
      ]
      useCurrencyStore().displayCurrency = 'BRL'
      const wrapper = await mountNavbar()

      await wrapper.get('.currency-toggle').trigger('click')
      await flushPromises()

      expect(useCurrencyStore().setDisplayCurrency).toHaveBeenCalledWith('USD')
      expect(useOverviewStore().refresh).toHaveBeenCalledOnce()
    })

    it('wraps around to the first currency after the last one', async () => {
      useRatesStore().rates = [
        { currencyCode: 'BRL', rate: 1, isBase: true },
        { currencyCode: 'USD', rate: 5, isBase: false },
      ]
      useCurrencyStore().displayCurrency = 'USD'
      const wrapper = await mountNavbar()

      await wrapper.get('.currency-toggle').trigger('click')

      expect(useCurrencyStore().setDisplayCurrency).toHaveBeenCalledWith('BRL')
    })

    it('falls back to BRL and USD when no rates are loaded', async () => {
      useCurrencyStore().displayCurrency = 'BRL'
      const wrapper = await mountNavbar()

      await wrapper.get('.currency-toggle').trigger('click')

      expect(useCurrencyStore().setDisplayCurrency).toHaveBeenCalledWith('USD')
    })
  })

  describe('user menu', () => {
    it('renders one accent swatch per accent and marks the active one', async () => {
      await mountNavbar()

      const swatches = Array.from(document.body.querySelectorAll('.accent-swatch'))
      expect(swatches.map((swatch) => swatch.getAttribute('aria-label'))).toEqual([
        'Cor blue',
        'Cor indigo',
        'Cor teal',
        'Cor yellow',
      ])
      const active = swatches.filter((swatch) => swatch.classList.contains('active'))
      expect(active.map((swatch) => swatch.getAttribute('aria-label'))).toEqual(['Cor teal'])
    })

    it('sets the accent when a swatch is clicked', async () => {
      await mountNavbar()

      document.body.querySelector<HTMLElement>('.accent-swatch[aria-label="Cor indigo"]')?.click()

      expect(useAppearanceStore().setAccent).toHaveBeenCalledWith('indigo')
    })

    it('offers the dark mode toggle in light mode and toggles on click', async () => {
      await mountNavbar()

      dropdownItem('Modo escuro').click()

      expect(useAppearanceStore().toggleDark).toHaveBeenCalledOnce()
    })

    it('offers the light mode toggle when dark mode is on', async () => {
      useAppearanceStore().dark = true

      await mountNavbar()

      expect(document.body.textContent).toContain('Modo claro')
      expect(document.body.textContent).not.toContain('Modo escuro')
    })

    it('opens the password change and trusted devices modals for a local account', async () => {
      await mountNavbar()

      dropdownItem('Alterar senha').click()
      dropdownItem('Dispositivos confiáveis').click()

      expect(modalControls.openPasswordChange).toHaveBeenCalledOnce()
      expect(modalControls.openTrustedDevices).toHaveBeenCalledOnce()
    })

    it('hides the password and trusted device items for a Google account', async () => {
      useAuthStore().session = sessionWith({ authProvider: 'GOOGLE' })

      await mountNavbar()

      expect(document.body.textContent).not.toContain('Alterar senha')
      expect(document.body.textContent).not.toContain('Dispositivos confiáveis')
    })

    it('logs out when Sair is clicked', async () => {
      await mountNavbar()

      dropdownItem('Sair').click()

      expect(useAuthStore().logout).toHaveBeenCalledOnce()
    })

    it('shows the plain version label outside demo mode', async () => {
      await mountNavbar()

      expect(document.body.querySelector('.version-line')?.textContent).toBe('1.2.3')
    })

    it('appends the demo marker to the version label in demo mode', async () => {
      useAuthStore().session = sessionWith({ demoModeEnabled: true })

      await mountNavbar()

      expect(document.body.querySelector('.version-line')?.textContent).toBe('1.2.3 - demo')
    })
  })
})
