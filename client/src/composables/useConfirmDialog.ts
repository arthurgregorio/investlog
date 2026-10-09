import { useDialog } from 'buefy'
import { escapeHtml } from '@/utils/escapeHtml'

declare const safeHtmlBrand: unique symbol

export type SafeHtml = string & { readonly [safeHtmlBrand]: true }

export function safeHtml(
  markup: TemplateStringsArray,
  ...values: Array<string | number>
): SafeHtml {
  return markup.reduce(
    (html, part, index) => html + escapeHtml(String(values[index - 1])) + part,
  ) as SafeHtml
}

export interface ConfirmOptions {
  title: string
  message: SafeHtml
  confirmText: string
  danger?: boolean
  onConfirm: () => void | Promise<void>
}

export function useConfirmDialog() {
  const dialog = useDialog()

  function confirm({ title, message, confirmText, danger = true, onConfirm }: ConfirmOptions) {
    dialog.confirm({
      title,
      message,
      confirmText,
      cancelText: 'Cancelar',
      onConfirm,
      ...(danger ? { type: 'is-danger', hasIcon: true } : {}),
    })
  }

  return { confirm }
}
