import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, type VueWrapper } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import { AxiosError, AxiosHeaders } from 'axios'
import EmailSettingsView from './EmailSettingsView.vue'
import { mailSettingsApi } from '@/api/mailSettings'
import { useMailSettingsStore } from '@/stores/mailSettings'
import type { MailSettingsResponse, SessionResponse } from '@/types'

vi.mock('@/api/mailSettings', () => ({
  mailSettingsApi: { get: vi.fn(), save: vi.fn(), setEnabled: vi.fn(), sendTest: vi.fn() },
}))

const savedSettings: MailSettingsResponse = {
  enabled: false,
  configured: true,
  host: 'smtp.gmail.com',
  port: 587,
  security: 'SSL_TLS',
  username: 'investlog@gmail.com',
  passwordSet: true,
  fromAddress: 'investlog@gmail.com',
  fromName: 'InvestLog',
  publicUrl: 'https://investlog.example.com',
  updatedAt: '2026-11-08T10:00:00Z',
}

const emptySettings: MailSettingsResponse = {
  enabled: false,
  configured: false,
  host: null,
  port: null,
  security: 'STARTTLS',
  username: null,
  passwordSet: false,
  fromAddress: null,
  fromName: null,
  publicUrl: null,
  updatedAt: '2026-11-08T10:00:00Z',
}

const adminSession: SessionResponse = {
  name: 'Arthur',
  email: 'arthur@email.com',
  role: 'ADMIN',
  status: 'APPROVED',
  authProvider: 'LOCAL',
  demoModeEnabled: false,
}

let activeWrapper: VueWrapper | undefined

function flushPromises() {
  return new Promise((resolve) => setTimeout(resolve, 0))
}

function httpError(status: number, data: unknown) {
  return new AxiosError('Request failed', String(status), undefined, null, {
    status,
    statusText: '',
    headers: {},
    config: { headers: new AxiosHeaders() },
    data,
  })
}

async function mountView(
  options: { settings?: MailSettingsResponse | null; session?: Partial<SessionResponse> } = {},
) {
  const { settings = savedSettings, session = {} } = options
  const wrapper = mount(EmailSettingsView, {
    global: {
      plugins: [
        createTestingPinia({
          initialState: {
            auth: { session: { ...adminSession, ...session } },
            mailSettings: { settings },
          },
        }),
      ],
      config: { errorHandler: () => undefined },
    },
    attachTo: document.body,
  })
  activeWrapper = wrapper
  await flushPromises()
  return { wrapper, mailSettingsStore: useMailSettingsStore() }
}

function field(wrapper: VueWrapper, label: string) {
  return wrapper.findAll('.field').find((candidate) => {
    const fieldLabel = candidate.find('.label')
    return fieldLabel.exists() && fieldLabel.text().startsWith(label)
  })!
}

function input(wrapper: VueWrapper, label: string) {
  return field(wrapper, label).find<HTMLInputElement>('input')
}

function securitySelect(wrapper: VueWrapper) {
  return field(wrapper, 'Segurança').find<HTMLSelectElement>('select')
}

function emailSwitch(wrapper: VueWrapper) {
  return wrapper.find<HTMLInputElement>('.switch input[type="checkbox"]')
}

function button(wrapper: VueWrapper, text: string) {
  return wrapper.findAll('button').find((candidate) => candidate.text() === text)!
}

describe('EmailSettingsView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    activeWrapper?.unmount()
    activeWrapper = undefined
  })

  it('loads the settings on mount and fills the form without a password', async () => {
    const { wrapper, mailSettingsStore } = await mountView()

    expect(mailSettingsStore.load).toHaveBeenCalledTimes(1)
    expect(input(wrapper, 'Servidor').element.value).toBe('smtp.gmail.com')
    expect(input(wrapper, 'Porta').element.value).toBe('587')
    expect(securitySelect(wrapper).element.value).toBe('SSL_TLS')
    expect(input(wrapper, 'Usuário').element.value).toBe('investlog@gmail.com')
    expect(input(wrapper, 'Senha').element.value).toBe('')
    expect(input(wrapper, 'Senha').attributes('type')).toBe('password')
    expect(input(wrapper, 'Senha').attributes('placeholder')).toBe('•••••••• definida')
    expect(input(wrapper, 'E-mail do remetente').element.value).toBe('investlog@gmail.com')
    expect(input(wrapper, 'Nome do remetente').element.value).toBe('InvestLog')
    expect(input(wrapper, 'URL pública do InvestLog').element.value).toBe(
      'https://investlog.example.com',
    )
  })

  it('offers the three security modes', async () => {
    const { wrapper } = await mountView()

    const options = securitySelect(wrapper).findAll('option')
    expect(options.map((option) => option.text())).toEqual(['Nenhuma', 'STARTTLS', 'SSL/TLS'])
  })

  it('shows no help text under any field, only the two label tooltips', async () => {
    const { wrapper } = await mountView()

    expect(wrapper.find('.help').exists()).toBe(false)
    const tooltipTriggers = wrapper.findAll('.label [aria-label]')
    expect(tooltipTriggers.map((trigger) => trigger.attributes('aria-label'))).toEqual([
      'A senha salva nunca é exibida. Deixe em branco para mantê-la.',
      'Endereço que os usuários acessam no navegador. É usado nos links dos e-mails.',
    ])
  })

  it('starts on empty fields with STARTTLS and a disabled switch before the first save', async () => {
    const { wrapper } = await mountView({ settings: emptySettings })

    expect(input(wrapper, 'Servidor').element.value).toBe('')
    expect(input(wrapper, 'Porta').element.value).toBe('')
    expect(securitySelect(wrapper).element.value).toBe('STARTTLS')
    expect(input(wrapper, 'Senha').attributes('placeholder')).toBe('')
    expect(emailSwitch(wrapper).element.disabled).toBe(true)
    expect(wrapper.text()).toContain(
      'Preencha e salve o servidor SMTP abaixo para poder ativar o envio.',
    )
    expect(input(wrapper, 'Destinatário').element.disabled).toBe(true)
    expect(button(wrapper, 'Enviar teste').element.disabled).toBe(true)
    expect(button(wrapper, 'Salvar').element.disabled).toBe(false)
  })

  it('fills nothing while the settings have not arrived', async () => {
    const { wrapper } = await mountView({ settings: null })

    expect(input(wrapper, 'Servidor').element.value).toBe('')
    expect(securitySelect(wrapper).element.value).toBe('STARTTLS')
    expect(emailSwitch(wrapper).element.disabled).toBe(true)
  })

  it('enables the switch once the settings are configured and reflects its state', async () => {
    const { wrapper } = await mountView({ settings: { ...savedSettings, enabled: true } })

    expect(emailSwitch(wrapper).element.disabled).toBe(false)
    expect(emailSwitch(wrapper).element.checked).toBe(true)
    expect(wrapper.text()).toContain(
      'Liga ou desliga todos os e-mails do InvestLog: aprovação de contas, relatórios e alertas de investimentos.',
    )
    expect(wrapper.find('.tag').exists()).toBe(false)
  })

  it('saves the switch on its own as soon as it changes', async () => {
    const { wrapper, mailSettingsStore } = await mountView()
    vi.mocked(mailSettingsStore.setEnabled).mockImplementation(async (enabled) => {
      mailSettingsStore.settings = { ...savedSettings, enabled }
    })

    await emailSwitch(wrapper).setValue(true)
    await flushPromises()

    expect(mailSettingsStore.setEnabled).toHaveBeenCalledWith(true)
    expect(mailSettingsStore.save).not.toHaveBeenCalled()
    expect(emailSwitch(wrapper).element.checked).toBe(true)
    expect(document.body.textContent).toContain('Envio de e-mails ativado.')
  })

  it('confirms turning sending off', async () => {
    const { wrapper, mailSettingsStore } = await mountView({
      settings: { ...savedSettings, enabled: true },
    })
    vi.mocked(mailSettingsStore.setEnabled).mockImplementation(async (enabled) => {
      mailSettingsStore.settings = { ...savedSettings, enabled }
    })

    await emailSwitch(wrapper).setValue(false)
    await flushPromises()

    expect(mailSettingsStore.setEnabled).toHaveBeenCalledWith(false)
    expect(document.body.textContent).toContain('Envio de e-mails desativado.')
  })

  it('puts the switch back when the server refuses the change', async () => {
    const { wrapper, mailSettingsStore } = await mountView()
    vi.mocked(mailSettingsStore.setEnabled).mockRejectedValue(httpError(400, {}))

    await emailSwitch(wrapper).setValue(true)
    await flushPromises()

    expect(emailSwitch(wrapper).element.checked).toBe(false)
    expect(emailSwitch(wrapper).element.disabled).toBe(false)
    expect(document.body.textContent).not.toContain('Envio de e-mails ativado.')
  })

  it('saves the SMTP fields with an empty password sent as null and keeps it set', async () => {
    const { wrapper, mailSettingsStore } = await mountView()
    vi.mocked(mailSettingsStore.save).mockImplementation(async () => {
      mailSettingsStore.settings = { ...savedSettings, host: 'smtp.office365.com' }
    })

    await input(wrapper, 'Servidor').setValue('  smtp.office365.com ')
    await input(wrapper, 'Usuário').setValue('   ')
    await button(wrapper, 'Salvar').trigger('click')
    await flushPromises()

    expect(mailSettingsStore.save).toHaveBeenCalledWith({
      host: 'smtp.office365.com',
      port: 587,
      security: 'SSL_TLS',
      username: null,
      password: null,
      fromAddress: 'investlog@gmail.com',
      fromName: 'InvestLog',
      publicUrl: 'https://investlog.example.com',
    })
    expect(input(wrapper, 'Servidor').element.value).toBe('smtp.office365.com')
    expect(input(wrapper, 'Senha').attributes('placeholder')).toBe('•••••••• definida')
    expect(document.body.textContent).toContain('Servidor SMTP salvo.')
  })

  it('sends a typed password and clears the field once saved', async () => {
    const { wrapper, mailSettingsStore } = await mountView({ settings: emptySettings })
    vi.mocked(mailSettingsStore.save).mockImplementation(async () => {
      mailSettingsStore.settings = savedSettings
    })

    await input(wrapper, 'Servidor').setValue('smtp.gmail.com')
    await input(wrapper, 'Porta').setValue('465')
    await securitySelect(wrapper).setValue('NONE')
    await input(wrapper, 'Senha').setValue('app-password')
    await button(wrapper, 'Salvar').trigger('click')
    await flushPromises()

    expect(mailSettingsStore.save).toHaveBeenCalledWith(
      expect.objectContaining({
        host: 'smtp.gmail.com',
        port: 465,
        security: 'NONE',
        username: null,
        password: 'app-password',
        fromAddress: '',
        fromName: '',
        publicUrl: '',
      }),
    )
    expect(input(wrapper, 'Senha').element.value).toBe('')
    expect(emailSwitch(wrapper).element.disabled).toBe(false)
  })

  it('sends an empty port as null', async () => {
    const { wrapper, mailSettingsStore } = await mountView()

    await input(wrapper, 'Porta').setValue('')
    await button(wrapper, 'Salvar').trigger('click')
    await flushPromises()

    expect(mailSettingsStore.save).toHaveBeenCalledWith(expect.objectContaining({ port: null }))
  })

  it('shows a refused save inline, below the form', async () => {
    const { wrapper, mailSettingsStore } = await mountView()
    vi.mocked(mailSettingsStore.save).mockRejectedValue(
      httpError(400, { errors: ['Informe o servidor SMTP'] }),
    )

    await input(wrapper, 'Servidor').setValue('')
    await button(wrapper, 'Salvar').trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-testid="save-error"]').text()).toBe('Informe o servidor SMTP')
    expect(input(wrapper, 'Servidor').element.value).toBe('')
    expect(document.body.textContent).not.toContain('Servidor SMTP salvo.')
  })

  it('shows nothing inline when the save fails for another reason', async () => {
    const { wrapper, mailSettingsStore } = await mountView()
    vi.mocked(mailSettingsStore.save).mockRejectedValue(httpError(500, {}))

    await button(wrapper, 'Salvar').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="save-error"]').exists()).toBe(false)
    expect(button(wrapper, 'Salvar').element.disabled).toBe(false)
  })

  it('prefills the test recipient with the admin email', async () => {
    const { wrapper } = await mountView()

    expect(input(wrapper, 'Destinatário').element.value).toBe('arthur@email.com')
  })

  it('leaves the test recipient empty for the default admin account', async () => {
    const { wrapper } = await mountView({ session: { email: 'admin@admin.com' } })

    expect(input(wrapper, 'Destinatário').element.value).toBe('')
    expect(button(wrapper, 'Enviar teste').element.disabled).toBe(true)
  })

  it('reports a delivered test email under the recipient row', async () => {
    const { wrapper } = await mountView()
    vi.mocked(mailSettingsApi.sendTest).mockResolvedValue()

    await input(wrapper, 'Destinatário').setValue(' teste@email.com ')
    await button(wrapper, 'Enviar teste').trigger('click')
    await flushPromises()

    expect(mailSettingsApi.sendTest).toHaveBeenCalledWith('teste@email.com')
    const notification = wrapper.get('.notification.is-success')
    expect(notification.text()).toBe(
      'E-mail de teste enviado para teste@email.com. Confira a caixa de entrada.',
    )
  })

  it('reports the SMTP error of a failed test email', async () => {
    const { wrapper } = await mountView()
    vi.mocked(mailSettingsApi.sendTest).mockRejectedValue(
      httpError(422, { detail: '535 5.7.8 Username and Password not accepted.' }),
    )

    await button(wrapper, 'Enviar teste').trigger('click')
    await flushPromises()

    const notification = wrapper.get('.notification.is-danger')
    expect(notification.text()).toBe(
      'Não foi possível enviar. 535 5.7.8 Username and Password not accepted.',
    )
    expect(wrapper.find('.notification.is-success').exists()).toBe(false)
  })

  it('reports a failed test email that carries no SMTP message', async () => {
    const { wrapper } = await mountView()
    vi.mocked(mailSettingsApi.sendTest).mockRejectedValue(new Error('network'))

    await button(wrapper, 'Enviar teste').trigger('click')
    await flushPromises()

    expect(wrapper.get('.notification.is-danger').text()).toBe('Não foi possível enviar.')
  })

  it('shows one demo notice and disables everything in demo mode', async () => {
    const { wrapper } = await mountView({ session: { demoModeEnabled: true } })

    const notices = wrapper.findAll('.message.is-warning')
    expect(notices).toHaveLength(1)
    expect(notices[0].text()).toContain('Indisponível no modo demonstração.')
    expect(wrapper.findAll('.notification')).toHaveLength(0)
    expect(emailSwitch(wrapper).element.disabled).toBe(true)
    expect(
      wrapper.findAll<HTMLInputElement>('input').every((element) => element.element.disabled),
    ).toBe(true)
    expect(securitySelect(wrapper).element.disabled).toBe(true)
    expect(
      wrapper.findAll<HTMLButtonElement>('button').every((element) => element.element.disabled),
    ).toBe(true)
  })

  it('shows no demo notice outside demo mode', async () => {
    const { wrapper } = await mountView()

    expect(wrapper.find('.message').exists()).toBe(false)
  })
})
