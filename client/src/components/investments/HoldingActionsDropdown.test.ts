import { afterEach, describe, expect, it } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import HoldingActionsDropdown from './HoldingActionsDropdown.vue'

interface DropdownProps {
  isFund: boolean
  isFrozen: boolean
  isAdmin: boolean
}

let activeWrapper: VueWrapper | undefined

function mountDropdown(props: Partial<DropdownProps> = {}) {
  activeWrapper = mount(HoldingActionsDropdown, {
    props: { isFund: false, isFrozen: false, isAdmin: true, ...props },
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

function groupLabels() {
  return Array.from(document.body.querySelectorAll('.actions-group-label')).map((label) =>
    label.textContent?.trim(),
  )
}

async function choose(label: string) {
  const item = items().find((candidate) => candidate.textContent?.trim() === label)!
  item.dispatchEvent(new MouseEvent('click', { bubbles: true }))
  await flushPromises()
}

describe('HoldingActionsDropdown', () => {
  afterEach(() => {
    activeWrapper?.unmount()
    activeWrapper = undefined
  })

  it('groups every occasional stock action behind a Mais ações trigger', () => {
    const wrapper = mountDropdown()

    expect(wrapper.get('[data-testid="holding-actions"] button').text()).toBe('Mais ações')
    expect(groupLabels()).toEqual(['Movimentar', 'Editar'])
    expect(labels()).toEqual([
      'Vender / resgatar',
      'Reinvestir',
      'Mover para outra carteira',
      'Atualizar preço',
      'Editar tags',
      'Congelar',
      'Remover investimento',
    ])
  })

  it('leaves the buy action out, since the drawer shows it as its own button', () => {
    mountDropdown()

    expect(labels().some((label) => label.startsWith('Registrar'))).toBe(false)
  })

  it('uses the fund wording, keeps Editar tags and hides Remover from a non-admin', () => {
    mountDropdown({ isFund: true, isAdmin: false })

    expect(labels()).toEqual([
      'Resgatar',
      'Reinvestir',
      'Mover para outra carteira',
      'Atualizar valor atual',
      'Editar tags',
      'Congelar',
    ])
  })

  it.each([
    ['Vender / resgatar', 'withdraw'],
    ['Reinvestir', 'reinvest'],
    ['Mover para outra carteira', 'move'],
    ['Atualizar preço', 'update-price'],
    ['Editar tags', 'edit-tags'],
    ['Congelar', 'toggle-frozen'],
    ['Remover investimento', 'remove'],
  ])('emits %s as %s', async (label, event) => {
    const wrapper = mountDropdown()

    await choose(label)

    expect(wrapper.emitted(event)).toHaveLength(1)
  })

  it('offers Descongelar on a frozen holding', () => {
    mountDropdown({ isFrozen: true })

    expect(labels()).toContain('Descongelar')
    expect(labels()).not.toContain('Congelar')
  })
})
