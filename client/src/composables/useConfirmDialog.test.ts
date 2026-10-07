import { beforeEach, describe, expect, it, vi } from 'vitest'
import { safeHtml, useConfirmDialog } from './useConfirmDialog'

const { dialogConfirm } = vi.hoisted(() => ({ dialogConfirm: vi.fn() }))

vi.mock('buefy', async (importOriginal) => ({
  ...(await importOriginal<typeof import('buefy')>()),
  useDialog: () => ({ confirm: dialogConfirm }),
}))

describe('safeHtml', () => {
  it('keeps the static markup and escapes every interpolated value', () => {
    const name = '<img src=x onerror=alert(1)>'

    expect(safeHtml`Remover <strong>${name}</strong>?`).toBe(
      'Remover <strong>&lt;img src=x onerror=alert(1)&gt;</strong>?',
    )
  })

  it('accepts numbers and markup with no interpolation', () => {
    expect(safeHtml`${3} itens`).toBe('3 itens')
    expect(safeHtml`Esta ação <strong>não pode ser desfeita</strong>.`).toBe(
      'Esta ação <strong>não pode ser desfeita</strong>.',
    )
  })
})

describe('useConfirmDialog', () => {
  beforeEach(() => dialogConfirm.mockClear())

  it('opens a danger confirmation with the shared options by default', () => {
    const onConfirm = vi.fn()
    const { confirm } = useConfirmDialog()

    confirm({
      title: 'Remover usuário',
      message: safeHtml`Remover <strong>${'Ana'}</strong>?`,
      confirmText: 'Remover',
      onConfirm,
    })

    expect(dialogConfirm).toHaveBeenCalledWith({
      title: 'Remover usuário',
      message: 'Remover <strong>Ana</strong>?',
      confirmText: 'Remover',
      cancelText: 'Cancelar',
      type: 'is-danger',
      hasIcon: true,
      onConfirm,
    })
  })

  it('opens a plain confirmation when danger is turned off', () => {
    const { confirm } = useConfirmDialog()

    confirm({
      title: 'Promover a administrador',
      message: safeHtml`Alterar o papel?`,
      confirmText: 'Confirmar',
      danger: false,
      onConfirm: vi.fn(),
    })

    const options = dialogConfirm.mock.calls[0][0]
    expect(options).not.toHaveProperty('type')
    expect(options).not.toHaveProperty('hasIcon')
    expect(options.cancelText).toBe('Cancelar')
  })
})
