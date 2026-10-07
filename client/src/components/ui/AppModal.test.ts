import { afterEach, describe, expect, it } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import AppModal from './AppModal.vue'

let activeWrapper: VueWrapper | undefined

function mountModal(
  props: Record<string, unknown> = {},
  slots: Record<string, string> = { default: '<p>Corpo do modal</p>' },
) {
  activeWrapper = mount(AppModal, {
    props: { title: 'Título', ...props },
    slots,
    attachTo: document.body,
  })
  return activeWrapper
}

describe('AppModal', () => {
  afterEach(() => {
    activeWrapper?.unmount()
    activeWrapper = undefined
  })

  it('renders the title and the default slot', () => {
    mountModal({ title: 'Novo aporte' })

    expect(document.body.querySelector('.modal-card-title')?.textContent).toBe('Novo aporte')
    expect(document.body.querySelector('.modal-card-body')?.textContent).toContain('Corpo do modal')
  })

  it('renders the subtitle only when given', () => {
    mountModal({ subtitle: 'Detalhes' })

    expect(document.body.querySelector('.modal-card-body .subtitle')?.textContent).toBe('Detalhes')
  })

  it('omits the subtitle and footer when not given', () => {
    mountModal()

    expect(document.body.querySelector('.subtitle')).toBeNull()
    expect(document.body.querySelector('.modal-card-foot')).toBeNull()
  })

  it('renders the footer slot', () => {
    mountModal({}, { default: 'x', footer: '<button type="button">Salvar</button>' })

    expect(document.body.querySelector('.modal-card-foot')?.textContent).toContain('Salvar')
  })

  it('widens the card on request', () => {
    mountModal({ wide: true })

    expect(document.body.querySelector('.modal-card')?.classList).toContain('is-wide')
  })

  it('emits close when the header close button is clicked', () => {
    const wrapper = mountModal()

    document.body.querySelector<HTMLButtonElement>('button[aria-label="Fechar"]')?.click()

    expect(wrapper.emitted('close')).toHaveLength(1)
  })

  it('emits close when escape is pressed', async () => {
    const wrapper = mountModal()

    document.dispatchEvent(new KeyboardEvent('keyup', { key: 'Escape', keyCode: 27 }))
    await flushPromises()

    expect(wrapper.emitted('close')).toBeTruthy()
  })

  it('emits close when the background overlay is clicked', () => {
    const wrapper = mountModal()

    document.body.querySelector<HTMLElement>('.modal-background')?.click()

    expect(wrapper.emitted('close')).toBeTruthy()
  })
})
