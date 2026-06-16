/* Shared add-investment form logic, used by the add-investment modal.
   The selected wallet is kept valid as `kind`/wallets change via a watcher
   (the prototype used a render-phase setTimeout hack — not needed in Vue). */
import { computed, reactive, watch } from 'vue'
import { ToastProgrammatic as Toast } from 'buefy'
import { usePortfolioStore } from '@/stores/portfolio'
import type { WalletKind } from '@/types'

export type InvestmentKind = WalletKind // 'stocks' | 'crypto' | 'funds'

export function useAddInvestmentForm(initialKind: InvestmentKind, onDone?: (kind: InvestmentKind) => void) {
  const store = usePortfolioStore()

  const form = reactive({
    kind: initialKind ?? 'stocks',
    walletId: '' as string,
    stockType: store.stockTypes[0] ?? '',
    fundType: store.fundTypes[0] ?? '',
    ticker: '',
    name: '',
    date: new Date() as Date | null,
    qty: '' as number | '',
    price: '' as number | '',
    currentPrice: '' as number | '',
    amount: '' as number | '',
    currentValue: '' as number | '',

    walletsOfKind: computed(() => store.wallets.filter((w) => w.type === form.kind)),
    wallet: computed(() => store.walletOf(form.walletId)),
    cur: computed(() => store.walletOf(form.walletId)?.currency ?? store.base),
    valid: computed(() => {
      if (!form.walletId) return false
      if (form.kind === 'funds') return !!form.name.trim() && !!form.date && Number(form.amount) > 0
      return !!form.ticker.trim() && !!form.date && Number(form.qty) > 0 && Number(form.price) > 0
    }),
  })

  // initial wallet selection
  form.walletId = form.walletsOfKind[0]?.id ?? ''

  // when the kind changes, jump to the first wallet of that kind
  watch(
    () => form.kind,
    () => {
      form.walletId = form.walletsOfKind[0]?.id ?? ''
    },
  )

  // keep the selected wallet valid as wallets are created/removed
  watch(
    () => form.walletsOfKind,
    (list) => {
      if (form.walletId && !list.some((w) => w.id === form.walletId)) {
        form.walletId = list[0]?.id ?? ''
      }
    },
  )

  function submit() {
    if (!form.valid || !form.date) return
    const dateStr = form.date.toISOString().slice(0, 10)
    if (form.kind === 'stocks') {
      store.addStock({
        walletId: form.walletId, stockType: form.stockType, ticker: form.ticker, name: form.name,
        date: dateStr, qty: Number(form.qty), price: Number(form.price), currentPrice: form.currentPrice,
      })
    } else if (form.kind === 'crypto') {
      store.addCrypto({
        walletId: form.walletId, ticker: form.ticker, name: form.name,
        date: dateStr, qty: Number(form.qty), price: Number(form.price), currentPrice: form.currentPrice,
      })
    } else {
      store.addFund({
        walletId: form.walletId, name: form.name, fundType: form.fundType,
        date: dateStr, amount: Number(form.amount), currentValue: form.currentValue,
      })
    }
    onDone?.(form.kind)
    Toast.open({ message: 'Investimento registrado!', type: 'is-success' })
  }

  return { form, submit }
}

export type AddInvestmentForm = ReturnType<typeof useAddInvestmentForm>['form']
