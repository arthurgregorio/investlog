import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import { AxiosError, AxiosHeaders } from 'axios'
import NotificationsView from './NotificationsView.vue'
import { useNotificationPreferencesStore } from '@/stores/notificationPreferences'
import type {
  NotificationPreferencesRequest,
  NotificationPreferencesResponse,
  PeriodicReportFrequency,
} from '@/types'

const defaults: NotificationPreferencesResponse = {
  periodicReportFrequency: 'DAILY',
  monthEndReportEnabled: true,
  frozenAlertEnabled: true,
  frozenAlertMinimumGainPercentage: 1,
  emailConfigured: true,
}

let activeWrapper: VueWrapper | undefined

async function mountView(preferences: NotificationPreferencesResponse | null = defaults) {
  const wrapper = mount(NotificationsView, {
    global: {
      plugins: [createTestingPinia({ initialState: { notificationPreferences: { preferences } } })],
      config: { errorHandler: () => undefined },
    },
    attachTo: document.body,
  })
  activeWrapper = wrapper
  const store = useNotificationPreferencesStore()
  await flushPromises()
  return { wrapper, store }
}

function savesSucceed(store: ReturnType<typeof useNotificationPreferencesStore>) {
  vi.mocked(store.save).mockImplementation(async (request: NotificationPreferencesRequest) => {
    store.preferences = { ...request, emailConfigured: store.preferences!.emailConfigured }
  })
}

function badRequest(message: string) {
  return new AxiosError('Request failed', '400', undefined, null, {
    status: 400,
    statusText: 'Bad Request',
    headers: {},
    config: { headers: new AxiosHeaders() },
    data: { errors: [message] },
  })
}

function frequencyButton(wrapper: VueWrapper, label: string) {
  return wrapper.findAll('.segmented-option').find((option) => option.text() === label)!
}

function activeFrequency(wrapper: VueWrapper) {
  return wrapper.get('.segmented-option.is-active').text()
}

function switchInput(wrapper: VueWrapper, testId: string) {
  return wrapper.get<HTMLInputElement>(`[data-testid="${testId}"] input[type="checkbox"]`)
}

function minimumGainInput(wrapper: VueWrapper) {
  return wrapper.get<HTMLInputElement>('input[data-testid="minimum-gain"]')
}

describe('NotificationsView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    activeWrapper?.unmount()
    activeWrapper = undefined
  })

  it('loads the preferences on mount', async () => {
    const { store } = await mountView()

    expect(store.load).toHaveBeenCalledOnce()
  })

  it('shows the page header and the three sections', async () => {
    const { wrapper } = await mountView()

    expect(wrapper.get('.page-title').text()).toBe('Notificações')
    expect(wrapper.get('.page-desc').text()).toBe(
      'Escolha quais e-mails você quer receber sobre seus investimentos.',
    )
    expect(wrapper.findAll('.settings-title').map((title) => title.text())).toEqual([
      'Relatório de desempenho',
      'Relatório de fim de mês',
      'Congelados no positivo',
    ])
  })

  it('renders no controls and no warning until the preferences arrive', async () => {
    const { wrapper } = await mountView(null)

    expect(wrapper.findAll('.settings-title')).toHaveLength(3)
    expect(wrapper.find('.segmented').exists()).toBe(false)
    expect(wrapper.find('.switch').exists()).toBe(false)
    expect(wrapper.find('input[data-testid="minimum-gain"]').exists()).toBe(false)
    expect(wrapper.find('.message.is-warning').exists()).toBe(false)
  })

  describe('email configuration', () => {
    it('warns above the cards when email is not configured', async () => {
      const { wrapper } = await mountView({ ...defaults, emailConfigured: false })

      const warning = wrapper.get('.message.is-warning')
      expect(warning.classes()).toContain('is-warning')
      expect(warning.text()).toContain('O envio de e-mails ainda não foi configurado.')
      expect(
        warning.element.compareDocumentPosition(wrapper.get('.card').element) &
          Node.DOCUMENT_POSITION_FOLLOWING,
      ).toBeTruthy()
    })

    it('shows no warning when email is configured', async () => {
      const { wrapper } = await mountView()

      expect(wrapper.find('.message.is-warning').exists()).toBe(false)
    })
  })

  describe('performance report frequency', () => {
    it('offers the four frequencies in order and marks the saved one', async () => {
      const { wrapper } = await mountView()

      expect(wrapper.findAll('.segmented-option').map((option) => option.text())).toEqual([
        'Diário',
        'Semanal',
        'Quinzenal',
        'Desativado',
      ])
      expect(activeFrequency(wrapper)).toBe('Diário')
      expect(wrapper.get('.segmented').attributes('aria-label')).toBe('Frequência do relatório')
    })

    it('centres the control in its card', async () => {
      const { wrapper } = await mountView()

      expect(wrapper.get('.segmented').element.parentElement?.className).toBe(
        'is-flex is-justify-content-center',
      )
    })

    it.each<[PeriodicReportFrequency, string]>([
      ['DAILY', 'Enviado às 07:00 com o resultado do dia útil anterior'],
      ['WEEKLY', 'Enviado toda segunda-feira às 07:00'],
      ['BIWEEKLY', 'Enviado nos dias 1º e 16 às 07:00'],
      ['DISABLED', 'Você não recebe relatórios periódicos.'],
    ])('describes when %s is sent', async (periodicReportFrequency, schedule) => {
      const { wrapper } = await mountView({ ...defaults, periodicReportFrequency })

      const help = wrapper.get('[data-testid="frequency-schedule"]')
      expect(help.text()).toContain(schedule)
      expect(help.classes()).toEqual(['help', 'mt-4'])
    })

    it('shows the weekend notice for the daily report', async () => {
      const { wrapper } = await mountView()

      const notice = wrapper.get('.message.is-info')
      expect(notice.classes()).toContain('is-info')
      expect(notice.text()).toContain('Fins de semana não geram relatório.')
    })

    it.each<PeriodicReportFrequency>(['WEEKLY', 'BIWEEKLY', 'DISABLED'])(
      'shows no weekend notice for %s',
      async (periodicReportFrequency) => {
        const { wrapper } = await mountView({ ...defaults, periodicReportFrequency })

        expect(wrapper.find('.message.is-info').exists()).toBe(false)
      },
    )

    it('saves the whole preferences with the new frequency', async () => {
      const { wrapper, store } = await mountView()
      savesSucceed(store)

      await frequencyButton(wrapper, 'Semanal').trigger('click')
      await flushPromises()

      expect(store.save).toHaveBeenCalledWith({
        periodicReportFrequency: 'WEEKLY',
        monthEndReportEnabled: true,
        frozenAlertEnabled: true,
        frozenAlertMinimumGainPercentage: 1,
      })
      expect(activeFrequency(wrapper)).toBe('Semanal')
      expect(wrapper.find('.message.is-info').exists()).toBe(false)
    })

    it('keeps the saved frequency until the server answers', async () => {
      const { wrapper, store } = await mountView()
      vi.mocked(store.save).mockReturnValue(new Promise(() => {}))

      await frequencyButton(wrapper, 'Quinzenal').trigger('click')

      expect(activeFrequency(wrapper)).toBe('Diário')
    })

    it('keeps the previous frequency when saving fails', async () => {
      const { wrapper, store } = await mountView()
      vi.mocked(store.save).mockRejectedValue(new Error('boom'))

      await frequencyButton(wrapper, 'Desativado').trigger('click')
      await flushPromises()

      expect(activeFrequency(wrapper)).toBe('Diário')
    })
  })

  describe('month-end report', () => {
    it('reflects the saved choice', async () => {
      const { wrapper } = await mountView({ ...defaults, monthEndReportEnabled: false })

      expect(switchInput(wrapper, 'month-end-switch').element.checked).toBe(false)
      expect(wrapper.get('[data-testid="month-end-switch"]').text()).toBe(
        'Receber o relatório de fim de mês',
      )
    })

    it('saves the whole preferences when turned off', async () => {
      const { wrapper, store } = await mountView()
      savesSucceed(store)

      await switchInput(wrapper, 'month-end-switch').setValue(false)
      await flushPromises()

      expect(store.save).toHaveBeenCalledWith({
        periodicReportFrequency: 'DAILY',
        monthEndReportEnabled: false,
        frozenAlertEnabled: true,
        frozenAlertMinimumGainPercentage: 1,
      })
      expect(switchInput(wrapper, 'month-end-switch').element.checked).toBe(false)
    })

    it('switches back to the saved value when saving fails', async () => {
      const { wrapper, store } = await mountView()
      vi.mocked(store.save).mockRejectedValue(new Error('boom'))

      await switchInput(wrapper, 'month-end-switch').setValue(false)
      await flushPromises()

      expect(switchInput(wrapper, 'month-end-switch').element.checked).toBe(true)
    })
  })

  describe('frozen holdings alert', () => {
    it('saves the whole preferences when turned off and disables the minimum gain', async () => {
      const { wrapper, store } = await mountView()
      savesSucceed(store)

      expect(minimumGainInput(wrapper).element.disabled).toBe(false)

      await switchInput(wrapper, 'frozen-alert-switch').setValue(false)
      await flushPromises()

      expect(store.save).toHaveBeenCalledWith({
        periodicReportFrequency: 'DAILY',
        monthEndReportEnabled: true,
        frozenAlertEnabled: false,
        frozenAlertMinimumGainPercentage: 1,
      })
      expect(minimumGainInput(wrapper).element.disabled).toBe(true)
    })

    it('switches back to the saved value when saving fails', async () => {
      const { wrapper, store } = await mountView()
      vi.mocked(store.save).mockRejectedValue(new Error('boom'))

      await switchInput(wrapper, 'frozen-alert-switch').setValue(false)
      await flushPromises()

      expect(switchInput(wrapper, 'frozen-alert-switch').element.checked).toBe(true)
      expect(minimumGainInput(wrapper).element.disabled).toBe(false)
    })

    it('disables the minimum gain while the alert is off', async () => {
      const { wrapper } = await mountView({ ...defaults, frozenAlertEnabled: false })

      expect(minimumGainInput(wrapper).element.disabled).toBe(true)
    })

    it('shows the saved minimum gain with two decimals and a percent addon', async () => {
      const { wrapper } = await mountView()

      expect(minimumGainInput(wrapper).element.value).toBe('1.00')
      expect(wrapper.get('.button.is-static').text()).toBe('%')
    })

    it('explains the minimum gain in a tooltip on the label, not in help text', async () => {
      const { wrapper } = await mountView()

      const label = wrapper.get('.label')
      expect(label.text()).toContain('Ganho mínimo')
      expect(label.find('.mdi-information-outline').exists()).toBe(true)
      expect(label.get('.tooltip-content').text()).toBe(
        'Só avisa sobre congelados com ganho acima deste percentual. Use 0 para ser avisado com qualquer ganho positivo.',
      )
      expect(wrapper.find('.field .help').exists()).toBe(false)
    })

    it('does not save while typing, only on blur', async () => {
      const { wrapper, store } = await mountView()
      savesSucceed(store)
      const input = minimumGainInput(wrapper)

      await input.setValue('5.5')

      expect(store.save).not.toHaveBeenCalled()

      await input.trigger('blur')
      await flushPromises()

      expect(store.save).toHaveBeenCalledOnce()
      expect(store.save).toHaveBeenCalledWith({
        periodicReportFrequency: 'DAILY',
        monthEndReportEnabled: true,
        frozenAlertEnabled: true,
        frozenAlertMinimumGainPercentage: 5.5,
      })
      expect(input.element.value).toBe('5.50')
    })

    it('saves once on Enter, even when the field is left afterwards', async () => {
      const { wrapper, store } = await mountView()
      savesSucceed(store)
      const input = minimumGainInput(wrapper)

      await input.setValue('2')
      await input.trigger('keydown', { key: 'Enter' })
      await input.trigger('blur')
      await flushPromises()

      expect(store.save).toHaveBeenCalledOnce()
      expect(vi.mocked(store.save).mock.calls[0][0].frozenAlertMinimumGainPercentage).toBe(2)
    })

    it('accepts zero', async () => {
      const { wrapper, store } = await mountView()
      savesSucceed(store)
      const input = minimumGainInput(wrapper)

      await input.setValue('0')
      await input.trigger('blur')
      await flushPromises()

      expect(vi.mocked(store.save).mock.calls[0][0].frozenAlertMinimumGainPercentage).toBe(0)
      expect(input.element.value).toBe('0.00')
    })

    it('saves nothing when left untouched or emptied', async () => {
      const { wrapper, store } = await mountView()
      const input = minimumGainInput(wrapper)

      await input.trigger('blur')
      await input.setValue('')
      await input.trigger('blur')
      await flushPromises()

      expect(store.save).not.toHaveBeenCalled()
      expect(input.element.value).toBe('1.00')
    })

    it('shows the server message inline and keeps the saved value on a 400', async () => {
      const { wrapper, store } = await mountView()
      vi.mocked(store.save).mockRejectedValue(
        badRequest('frozenAlertMinimumGainPercentage deve estar entre 0 e 100'),
      )
      const input = minimumGainInput(wrapper)

      await input.setValue('150')
      await input.trigger('blur')
      await flushPromises()

      expect(wrapper.get('.help.is-danger').text()).toBe(
        'frozenAlertMinimumGainPercentage deve estar entre 0 e 100',
      )
      expect(input.element.value).toBe('1.00')
    })

    it('clears the inline message after the next successful save', async () => {
      const { wrapper, store } = await mountView()
      vi.mocked(store.save).mockRejectedValueOnce(badRequest('fora do intervalo'))
      const input = minimumGainInput(wrapper)

      await input.setValue('150')
      await input.trigger('blur')
      await flushPromises()
      savesSucceed(store)
      await input.setValue('3')
      await input.trigger('blur')
      await flushPromises()

      expect(wrapper.find('.help.is-danger').exists()).toBe(false)
      expect(input.element.value).toBe('3.00')
    })
  })

  it('waits for a pending save so the next one keeps its change', async () => {
    const { wrapper, store } = await mountView()
    let resolveFirstSave: () => void = () => undefined
    vi.mocked(store.save).mockImplementationOnce(
      (request: NotificationPreferencesRequest) =>
        new Promise<void>((resolve) => {
          resolveFirstSave = () => {
            store.preferences = { ...request, emailConfigured: true }
            resolve()
          }
        }),
    )
    const input = minimumGainInput(wrapper)

    await input.setValue('5.5')
    await input.trigger('blur')
    savesSucceed(store)
    await switchInput(wrapper, 'frozen-alert-switch').setValue(false)
    await flushPromises()

    expect(store.save).toHaveBeenCalledOnce()

    resolveFirstSave()
    await flushPromises()

    expect(store.save).toHaveBeenCalledTimes(2)
    expect(vi.mocked(store.save).mock.calls[1][0]).toEqual({
      periodicReportFrequency: 'DAILY',
      monthEndReportEnabled: true,
      frozenAlertEnabled: false,
      frozenAlertMinimumGainPercentage: 5.5,
    })
  })
})
