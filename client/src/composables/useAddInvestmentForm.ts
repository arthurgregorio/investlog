import { computed, reactive, watch } from 'vue'
import { useToast } from 'buefy'
import { holdingsApi } from '@/api/holdings'
import { useWalletsStore } from '@/stores/wallets'
import { useTypesListStore } from '@/stores/typesList'
import type { WalletKind, WalletResponse } from '@/types'

export type InvestmentKind = WalletKind

interface AddInvestmentFormState {
  kind: InvestmentKind
  walletId: string
  typeId: string
  segmentId: string
  ticker: string
  name: string
  date: Date | null
  quantity: number | ''
  price: number | ''
  currentPrice: number | ''
  amount: number | ''
  currentValue: number | ''
  administrationFeeRate: number | ''
  performanceFeeRate: number | ''
  submitting: boolean
  walletsOfKind: WalletResponse[]
  valid: boolean
}

export function useAddInvestmentForm(
  initialKind: InvestmentKind,
  initialWalletId?: string,
  onDone?: (kind: InvestmentKind) => void,
) {
  const toast = useToast()
  const walletsStore = useWalletsStore()
  const typesListStore = useTypesListStore()

  const form: AddInvestmentFormState = reactive({
    kind: initialKind ?? 'STOCKS',
    walletId: '' as string,
    typeId: '' as string,
    segmentId: '' as string,
    ticker: '',
    name: '',
    date: new Date() as Date | null,
    quantity: '' as number | '',
    price: '' as number | '',
    currentPrice: '' as number | '',
    amount: '' as number | '',
    currentValue: '' as number | '',
    administrationFeeRate: '' as number | '',
    performanceFeeRate: '' as number | '',
    submitting: false,

    walletsOfKind: computed(() =>
      walletsStore.wallets.filter((wallet) => wallet.kind === form.kind),
    ),
    valid: computed(() => {
      if (!form.walletId) return false
      if (form.kind === 'FUNDS') {
        return !!form.typeId && !!form.name.trim() && !!form.date && Number(form.amount) > 0
      }
      if (form.kind === 'STOCKS' && !form.typeId) return false
      return (
        !!form.ticker.trim() && !!form.date && Number(form.quantity) > 0 && Number(form.price) > 0
      )
    }),
  })

  function initialWalletIn(wallets: WalletResponse[]) {
    return wallets.find((wallet) => wallet.id === initialWalletId)
  }

  form.walletId = (initialWalletIn(form.walletsOfKind) ?? form.walletsOfKind[0])?.id ?? ''
  function defaultTypeId(kind: InvestmentKind) {
    const types = typesListStore.typesForKind(kind)
    if (kind === 'CRYPTO') return types.length === 1 ? types[0].id : ''
    return types[0]?.id ?? ''
  }

  form.typeId = defaultTypeId(form.kind)

  watch(
    () => form.kind,
    () => {
      form.walletId = form.walletsOfKind[0]?.id ?? ''
    },
  )

  watch(
    () => form.walletsOfKind,
    (list) => {
      const initialWallet = initialWalletIn(list)
      if (!form.walletId && initialWallet) {
        form.walletId = initialWallet.id
      } else if (form.walletId && !list.some((wallet) => wallet.id === form.walletId)) {
        form.walletId = list[0]?.id ?? ''
      }
    },
  )

  watch(
    () => typesListStore.typesForKind(form.kind),
    (types) => {
      if (!types.some((type) => type.id === form.typeId)) {
        form.typeId = defaultTypeId(form.kind)
      }
    },
  )

  watch(
    () => typesListStore.segments,
    (segments) => {
      if (!segments.some((segment) => segment.id === form.segmentId)) {
        form.segmentId = ''
      }
    },
  )

  async function submit() {
    if (!form.valid || !form.date || form.submitting) return
    form.submitting = true
    const dateStr = form.date.toISOString().slice(0, 10)
    const currentPriceNum = Number(form.currentPrice) > 0 ? Number(form.currentPrice) : undefined

    try {
      if (form.kind === 'STOCKS') {
        await holdingsApi.createStockHolding(form.walletId, {
          typeId: form.typeId,
          segmentId: form.segmentId || undefined,
          ticker: form.ticker.trim().toUpperCase(),
          name: form.name.trim() || undefined,
          currentPrice: currentPriceNum,
          lot: { lotDate: dateStr, quantity: Number(form.quantity), price: Number(form.price) },
        })
      } else if (form.kind === 'CRYPTO') {
        await holdingsApi.createCryptoHolding(form.walletId, {
          typeId: form.typeId || undefined,
          segmentId: form.segmentId || undefined,
          ticker: form.ticker.trim().toUpperCase(),
          name: form.name.trim() || undefined,
          currentPrice: currentPriceNum,
          lot: { lotDate: dateStr, quantity: Number(form.quantity), price: Number(form.price) },
        })
      } else {
        const currentValueNum =
          Number(form.currentValue) > 0 ? Number(form.currentValue) : undefined
        await holdingsApi.createFundHolding(form.walletId, {
          typeId: form.typeId,
          segmentId: form.segmentId || undefined,
          name: form.name.trim(),
          currentValue: currentValueNum,
          ...(form.administrationFeeRate !== '' && {
            administrationFeeRate: Number(form.administrationFeeRate),
          }),
          ...(form.performanceFeeRate !== '' && {
            performanceFeeRate: Number(form.performanceFeeRate),
          }),
          contribution: { contributionDate: dateStr, amount: Number(form.amount) },
        })
      }
      onDone?.(form.kind)
      toast.open({ message: 'Investimento registrado!', type: 'is-success' })
    } finally {
      form.submitting = false
    }
  }

  return { form, submit }
}

export type AddInvestmentForm = ReturnType<typeof useAddInvestmentForm>['form']
