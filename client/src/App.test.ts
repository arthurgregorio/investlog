import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { h, type Component } from 'vue'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createTestingPinia, type TestingPinia } from '@pinia/testing'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import App from './App.vue'
import { useAppearanceStore } from '@/stores/appearance'
import { useAuthStore } from '@/stores/auth'
import { useModals } from '@/composables/useModals'
import type { SessionResponse } from '@/types'

const NavbarStub: Component = {
  name: 'TheNavbar',
  setup() {
    const modals = useModals()
    return () =>
      h('div', { class: 'navbar-stub' }, [
        h('button', { class: 'open-add', onClick: () => modals.openAddInvestment() }, 'add'),
        h(
          'button',
          { class: 'open-add-crypto', onClick: () => modals.openAddInvestment('CRYPTO') },
          'add crypto',
        ),
        h(
          'button',
          { class: 'open-wallet', onClick: () => modals.openCreateWallet('FUNDS') },
          'wallet',
        ),
        h(
          'button',
          { class: 'open-wallet-default', onClick: () => modals.openCreateWallet() },
          'wallet default',
        ),
        h(
          'button',
          { class: 'open-password', onClick: () => modals.openPasswordChange() },
          'password',
        ),
        h(
          'button',
          { class: 'open-devices', onClick: () => modals.openTrustedDevices() },
          'devices',
        ),
      ])
  },
}

const TopNavStub: Component = {
  name: 'TheTopNav',
  setup: () => () => h('nav', { class: 'top-nav-stub' }),
}

const CreateWalletModalStub: Component = {
  name: 'CreateWalletModal',
  props: { initialType: { type: String, default: '' } },
  emits: ['close'],
  setup:
    (props, { emit }) =>
    () =>
      h('div', { class: 'create-wallet-stub' }, [
        h('span', { class: 'initial-type' }, props.initialType),
        h('button', { class: 'close-wallet', onClick: () => emit('close') }, 'close'),
      ]),
}

const AddInvestmentModalStub: Component = {
  name: 'AddInvestmentModal',
  props: { initialKind: { type: String, default: '' } },
  emits: ['close', 'createWallet'],
  setup:
    (props, { emit }) =>
    () =>
      h('div', { class: 'add-investment-stub' }, [
        h('span', { class: 'initial-kind' }, props.initialKind),
        h('button', { class: 'close-add', onClick: () => emit('close') }, 'close'),
        h(
          'button',
          { class: 'request-wallet', onClick: () => emit('createWallet', 'CRYPTO') },
          'wallet',
        ),
      ]),
}

const PasswordChangeModalStub: Component = {
  name: 'PasswordChangeModal',
  emits: ['close'],
  setup:
    (_props, { emit }) =>
    () =>
      h('div', { class: 'password-change-stub' }, [
        h('button', { class: 'close-password', onClick: () => emit('close') }, 'close'),
      ]),
}

const TrustedDevicesModalStub: Component = {
  name: 'TrustedDevicesModal',
  emits: ['close'],
  setup:
    (_props, { emit }) =>
    () =>
      h('div', { class: 'trusted-devices-stub' }, [
        h('button', { class: 'close-devices', onClick: () => emit('close') }, 'close'),
      ]),
}

function sessionWithStatus(status: SessionResponse['status']): SessionResponse {
  return {
    name: 'Arthur',
    email: 'arthur@example.com',
    role: 'USER',
    status,
    authProvider: 'LOCAL',
    demoModeEnabled: false,
  }
}

function makeRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/overview',
        name: 'overview',
        component: { template: '<div class="page-overview" />' },
      },
      { path: '/login', name: 'login', component: { template: '<div class="page-login" />' } },
    ],
  })
}

let activeWrapper: VueWrapper | undefined

async function mountApp(options: {
  session: SessionResponse | null
  path?: string
  initialState?: Record<string, unknown>
}) {
  const pinia: TestingPinia = createTestingPinia({
    initialState: { appearance: { dark: false, accent: 'teal' }, ...options.initialState },
  })
  useAuthStore().session = options.session
  const router = makeRouter()
  router.push(options.path ?? '/overview')
  await router.isReady()
  activeWrapper = mount(App, {
    global: {
      plugins: [pinia, router],
      stubs: {
        TheNavbar: NavbarStub,
        TheTopNav: TopNavStub,
        CreateWalletModal: CreateWalletModalStub,
        AddInvestmentModal: AddInvestmentModalStub,
        PasswordChangeModal: PasswordChangeModalStub,
        TrustedDevicesModal: TrustedDevicesModalStub,
      },
    },
  })
  await flushPromises()
  return activeWrapper
}

describe('App', () => {
  beforeEach(() => {
    document.documentElement.removeAttribute('data-theme')
    document.documentElement.removeAttribute('data-accent')
  })

  afterEach(() => {
    activeWrapper?.unmount()
    activeWrapper = undefined
  })

  describe('theming', () => {
    it('stamps the light theme and the stored accent on the root', async () => {
      const wrapper = await mountApp({ session: sessionWithStatus('APPROVED') })

      const root = wrapper.get('.app-root')
      expect(root.attributes('data-theme')).toBe('light')
      expect(root.attributes('data-accent')).toBe('teal')
    })

    it('mirrors the theme and accent onto the document element', async () => {
      await mountApp({
        session: sessionWithStatus('APPROVED'),
        initialState: { appearance: { dark: true, accent: 'indigo' } },
      })

      expect(document.documentElement.getAttribute('data-theme')).toBe('dark')
      expect(document.documentElement.getAttribute('data-accent')).toBe('indigo')
    })

    it('follows the appearance store when the user changes it', async () => {
      const wrapper = await mountApp({ session: sessionWithStatus('APPROVED') })
      const appearance = useAppearanceStore()

      appearance.dark = true
      appearance.accent = 'yellow'
      await flushPromises()

      const root = wrapper.get('.app-root')
      expect(root.attributes('data-theme')).toBe('dark')
      expect(root.attributes('data-accent')).toBe('yellow')
      expect(document.documentElement.getAttribute('data-theme')).toBe('dark')
      expect(document.documentElement.getAttribute('data-accent')).toBe('yellow')
    })
  })

  describe('app chrome', () => {
    it('renders the navbar, the top navigation and the routed page for an approved session', async () => {
      const wrapper = await mountApp({ session: sessionWithStatus('APPROVED') })

      expect(wrapper.find('.navbar-stub').exists()).toBe(true)
      expect(wrapper.find('.top-nav-stub').exists()).toBe(true)
      expect(wrapper.find('.main .app-content .page-overview').exists()).toBe(true)
    })

    it('renders only the routed page, with no chrome, when there is no session', async () => {
      const wrapper = await mountApp({ session: null, path: '/login' })

      expect(wrapper.find('.navbar-stub').exists()).toBe(false)
      expect(wrapper.find('.top-nav-stub').exists()).toBe(false)
      expect(wrapper.find('.main').exists()).toBe(false)
      expect(wrapper.find('.page-login').exists()).toBe(true)
    })

    it('hides the chrome for a session that is still pending approval', async () => {
      const wrapper = await mountApp({ session: sessionWithStatus('PENDING') })

      expect(wrapper.find('.navbar-stub').exists()).toBe(false)
      expect(wrapper.find('.page-overview').exists()).toBe(true)
    })

    it('renders no modal until one is opened', async () => {
      const wrapper = await mountApp({ session: sessionWithStatus('APPROVED') })

      expect(wrapper.find('.add-investment-stub').exists()).toBe(false)
      expect(wrapper.find('.create-wallet-stub').exists()).toBe(false)
      expect(wrapper.find('.password-change-stub').exists()).toBe(false)
      expect(wrapper.find('.trusted-devices-stub').exists()).toBe(false)
    })
  })

  describe('app-shell modals', () => {
    it('opens the add-investment modal with the default kind through useModals', async () => {
      const wrapper = await mountApp({ session: sessionWithStatus('APPROVED') })

      await wrapper.get('.open-add').trigger('click')

      expect(wrapper.get('.add-investment-stub .initial-kind').text()).toBe('STOCKS')
    })

    it('opens the add-investment modal with the requested kind', async () => {
      const wrapper = await mountApp({ session: sessionWithStatus('APPROVED') })

      await wrapper.get('.open-add-crypto').trigger('click')

      expect(wrapper.get('.add-investment-stub .initial-kind').text()).toBe('CRYPTO')
    })

    it('closes the add-investment modal when it asks to close', async () => {
      const wrapper = await mountApp({ session: sessionWithStatus('APPROVED') })
      await wrapper.get('.open-add').trigger('click')

      await wrapper.get('.close-add').trigger('click')

      expect(wrapper.find('.add-investment-stub').exists()).toBe(false)
    })

    it('opens the create-wallet modal with the requested type', async () => {
      const wrapper = await mountApp({ session: sessionWithStatus('APPROVED') })

      await wrapper.get('.open-wallet').trigger('click')

      expect(wrapper.get('.create-wallet-stub .initial-type').text()).toBe('FUNDS')
    })

    it('opens the create-wallet modal with the default type when none is given', async () => {
      const wrapper = await mountApp({ session: sessionWithStatus('APPROVED') })

      await wrapper.get('.open-wallet-default').trigger('click')

      expect(wrapper.get('.create-wallet-stub .initial-type').text()).toBe('STOCKS')
    })

    it('closes the create-wallet modal when it asks to close', async () => {
      const wrapper = await mountApp({ session: sessionWithStatus('APPROVED') })
      await wrapper.get('.open-wallet').trigger('click')

      await wrapper.get('.close-wallet').trigger('click')

      expect(wrapper.find('.create-wallet-stub').exists()).toBe(false)
    })

    it('swaps the add-investment modal for the create-wallet modal when it requests a wallet', async () => {
      const wrapper = await mountApp({ session: sessionWithStatus('APPROVED') })
      await wrapper.get('.open-add').trigger('click')

      await wrapper.get('.request-wallet').trigger('click')

      expect(wrapper.find('.add-investment-stub').exists()).toBe(false)
      expect(wrapper.get('.create-wallet-stub .initial-type').text()).toBe('CRYPTO')
    })

    it('opens and closes the password-change modal', async () => {
      const wrapper = await mountApp({ session: sessionWithStatus('APPROVED') })

      await wrapper.get('.open-password').trigger('click')
      expect(wrapper.find('.password-change-stub').exists()).toBe(true)

      await wrapper.get('.close-password').trigger('click')
      expect(wrapper.find('.password-change-stub').exists()).toBe(false)
    })

    it('opens and closes the trusted-devices modal', async () => {
      const wrapper = await mountApp({ session: sessionWithStatus('APPROVED') })

      await wrapper.get('.open-devices').trigger('click')
      expect(wrapper.find('.trusted-devices-stub').exists()).toBe(true)

      await wrapper.get('.close-devices').trigger('click')
      expect(wrapper.find('.trusted-devices-stub').exists()).toBe(false)
    })
  })
})
