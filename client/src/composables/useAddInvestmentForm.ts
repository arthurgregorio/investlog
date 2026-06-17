import { computed, reactive, watch } from 'vue'
import { ToastProgrammatic as Toast } from 'buefy'
import { holdingsApi } from '@/api/holdings'
import { useWalletsStore } from '@/stores/wallets'
import { useTypesListStore } from '@/stores/typesList'
import type { WalletKind } from '@/types'

export type InvestmentKind = WalletKind

export function useAddInvestmentForm(initialKind: InvestmentKind, onDone?: (kind: InvestmentKind) => void) {
  const walletsStore = useWalletsStore()
  const typesListStore = useTypesListStore()

  const form = reactive({
    kind: initialKind ?? 'stocks',
    walletId: '' as string,
    stockTypeId: '' as string,
    fundTypeId: '' as string,
    ticker: '',
    name: '',
    date: new Date() as Date | null,
    quantity: '' as number | '',
    price: '' as number | '',
    currentPrice: '' as number | '',
    amount: '' as number | '',
    currentValue: '' as number | '',
    submitting: false,

    walletsOfKind: computed(() => walletsStore.wallets.filter((wallet) => wallet.kind === form.kind)),
    valid: computed(() => {
      if (!form.walletId) return false
      if (form.kind === 'funds') {
        return !!form.fundTypeId && !!form.name.trim() && !!form.date && Number(form.amount) > 0
      }
      return !!form.ticker.trim() && !!form.date && Number(form.quantity) > 0 && Number(form.price) > 0
    }),
  })

  form.walletId = form.walletsOfKind[0]?.id ?? ''
  form.stockTypeId = typesListStore.stockTypes[0]?.id ?? ''
  form.fundTypeId = typesListStore.fundTypes[0]?.id ?? ''

  watch(
    () => form.kind,
    () => {
      form.walletId = form.walletsOfKind[0]?.id ?? ''
    },
  )

  watch(
    () => form.walletsOfKind,
    (list) => {
      if (form.walletId && !list.some((wallet) => wallet.id === form.walletId)) {
        form.walletId = list[0]?.id ?? ''
      }
    },
  )

  watch(
    () => typesListStore.stockTypes,
    (types) => {
      if (!form.stockTypeId || !types.some((type) => type.id === form.stockTypeId)) {
        form.stockTypeId = types[0]?.id ?? ''
      }
    },
  )

  watch(
    () => typesListStore.fundTypes,
    (types) => {
      if (!form.fundTypeId || !types.some((type) => type.id === form.fundTypeId)) {
        form.fundTypeId = types[0]?.id ?? ''
      }
    },
  )

  async function submit() {
    if (!form.valid || !form.date || form.submitting) return
    form.submitting = true
    const dateStr = form.date.toISOString().slice(0, 10)
    const currentPriceNum = Number(form.currentPrice) > 0 ? Number(form.currentPrice) : undefined

    try {
      if (form.kind === 'stocks') {
        await holdingsApi.createStockHolding(form.walletId, {
          stockTypeId: form.stockTypeId,
          ticker: form.ticker.trim().toUpperCase(),
          name: form.name.trim() || undefined,
          currentPrice: currentPriceNum,
          lot: { lotDate: dateStr, quantity: Number(form.quantity), price: Number(form.price) },
        })
      } else if (form.kind === 'crypto') {
        await holdingsApi.createCryptoHolding(form.walletId, {
          ticker: form.ticker.trim().toUpperCase(),
          name: form.name.trim() || undefined,
          currentPrice: currentPriceNum,
          lot: { lotDate: dateStr, quantity: Number(form.quantity), price: Number(form.price) },
        })
      } else {
        const currentValueNum = Number(form.currentValue) > 0 ? Number(form.currentValue) : undefined
        await holdingsApi.createFundHolding(form.walletId, {
          fundTypeId: form.fundTypeId,
          name: form.name.trim(),
          currentValue: currentValueNum,
          contribution: { contributionDate: dateStr, amount: Number(form.amount) },
        })
      }
      onDone?.(form.kind)
      Toast.open({ message: 'Investimento registrado!', type: 'is-success' })
    } finally {
      form.submitting = false
    }
  }

  return { form, submit }
}

export type AddInvestmentForm = ReturnType<typeof useAddInvestmentForm>['form']
