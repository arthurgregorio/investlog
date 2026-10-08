import { afterEach, describe, expect, it } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import HoldingActionsDropdown from './HoldingActionsDropdown.vue'

interface DropdownProps {
  isFund: boolean
  isStock: boolean
  isFrozen: boolean
  isAdmin: boolean
}

let activeWrapper: VueWrapper | undefined

function mountDropdown(props: Partial<DropdownProps> = {}) {
  activeWrapper = mount(HoldingActionsDropdown, {
    props: { isFund: false, isStock: true, isFrozen: false, isAdmin: true, ...props },
    attachTo: document.body,
  })
  return activeWrapper
}

function items() {
  return Array.from(document.body.querySelectorAll<HTMLElement>('.dropdown-item'))
}

function labels() {
  return items().map((item) => item.textContent?.trim() ?? '')
}

function item(label: string) {
  return items().find((candidate) => candidate.textContent?.trim() === label)!
}

async function choose(label: string) {
  item(label).dispatchEvent(new MouseEvent('click', { bubbles: true }))
  await flushPromises()
}

describe('HoldingActionsDropdown', () => {
  afterEach(() => {
    activeWrapper?.unmount()
    activeWrapper = undefined
  })

  it('lists every stock action in order behind an Ações trigger', () => {
    const wrapper = mountDropdown()

    expect(wrapper.get('[data-testid="holding-actions"] button').text()).toBe('Ações')
    expect(labels()).toEqual([
      'Registrar nova compra',
      'Atualizar preço',
      'Definir segmento',
      'Resgatar',
      'Reinvestir',
      'Mover',
      'Congelar',
      'Remover',
    ])
  })

  it('uses the fund wording, drops Definir segmento and hides Remover from a non-admin', () => {
    mountDropdown({ isFund: true, isStock: false, isAdmin: false })

    expect(labels()).toEqual([
      'Registrar novo aporte',
      'Atualizar valor atual',
      'Resgatar',
      'Reinvestir',
      'Mover',
      'Congelar',
    ])
  })

  it.each([
    ['Registrar nova compra', 'add-position'],
    ['Atualizar preço', 'update-price'],
    ['Definir segmento', 'set-segment'],
    ['Resgatar', 'withdraw'],
    ['Reinvestir', 'reinvest'],
    ['Mover', 'move'],
    ['Congelar', 'toggle-frozen'],
    ['Remover', 'remove'],
  ])('emits %s as %s', async (label, event) => {
    const wrapper = mountDropdown()

    await choose(label)

    expect(wrapper.emitted(event)).toHaveLength(1)
  })

  it('offers Descongelar and disables the buy action without emitting on a frozen holding', async () => {
    const wrapper = mountDropdown({ isFrozen: true })

    expect(labels()).toContain('Descongelar')
    expect(item('Registrar nova compra').classList.contains('is-disabled')).toBe(true)
    await choose('Registrar nova compra')

    expect(wrapper.emitted('add-position')).toBeUndefined()
  })
})
