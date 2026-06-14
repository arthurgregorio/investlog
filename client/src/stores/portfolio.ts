/* InvestLog store — in-session state (resets on refresh), actions & selectors.
   Ported from the prototype's React context to a Pinia setup store.
   No-arg derived values are exposed as computed getters; selectors that take an
   id/holding are plain functions that read reactive state (so they stay reactive). */
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import type {
  CurrencyConfig,
  Gain,
  Holding,
  TypeTotal,
  Wallet,
  WalletKind,
} from '@/types'
import * as seed from '@/data/seed'

const clone = <T>(x: T): T => structuredClone(x)

interface AddStockArgs {
  walletId: string
  stockType: string
  ticker: string
  name?: string
  date: string
  qty: number
  price: number
  currentPrice?: number | string
}
interface AddCryptoArgs {
  walletId: string
  ticker: string
  name?: string
  date: string
  qty: number
  price: number
  currentPrice?: number | string
}
interface AddFundArgs {
  walletId: string
  name: string
  fundType: string
  date: string
  amount: number
  currentValue?: number | string
}

const optionalNumber = (v: number | string | undefined): number | undefined =>
  v === '' || v == null ? undefined : Number(v)

export const usePortfolioStore = defineStore('portfolio', () => {
  /* ---------------- state ---------------- */
  const config = ref<CurrencyConfig>(clone(seed.config))
  const stockTypes = ref<string[]>([...seed.stockTypes])
  const fundTypes = ref<string[]>([...seed.fundTypes])
  const wallets = ref<Wallet[]>(clone(seed.wallets))
  const holdings = ref<Holding[]>(clone(seed.holdings))

  let idCounter = 1
  const nid = (p: string) => p + '_' + Date.now().toString(36) + idCounter++

  /* static presentation maps (not reactive) */
  const WALLET_TYPES = seed.WALLET_TYPES
  const currencies = seed.currencies
  const currencySymbol = seed.currencySymbol
  const badgeFor = seed.badgeFor

  /* ---------------- actions ---------------- */
  function addWallet({ name, type, currency }: { name: string; type: WalletKind; currency: string }) {
    const w: Wallet = { id: nid('w'), name: name.trim(), type, currency }
    wallets.value = [...wallets.value, w]
    return w.id
  }
  function addStock({ walletId, stockType, ticker, name, date, qty, price, currentPrice }: AddStockArgs) {
    const h: Holding = {
      id: nid('h'), kind: 'stock', walletId, stockType,
      ticker: ticker.trim().toUpperCase(), name: (name || '').trim(),
      currentPrice: optionalNumber(currentPrice),
      lots: [{ id: nid('l'), date, qty: Number(qty), price: Number(price) }],
    }
    holdings.value = [h, ...holdings.value]
  }
  function addCrypto({ walletId, ticker, name, date, qty, price, currentPrice }: AddCryptoArgs) {
    const h: Holding = {
      id: nid('h'), kind: 'crypto', walletId,
      ticker: ticker.trim().toUpperCase(), name: (name || '').trim(),
      currentPrice: optionalNumber(currentPrice),
      lots: [{ id: nid('l'), date, qty: Number(qty), price: Number(price) }],
    }
    holdings.value = [h, ...holdings.value]
  }
  function addFund({ walletId, name, fundType, date, amount, currentValue }: AddFundArgs) {
    const h: Holding = {
      id: nid('h'), kind: 'fund', walletId, name: name.trim(), fundType,
      currentValue: optionalNumber(currentValue),
      contributions: [{ id: nid('c'), date, amount: Number(amount) }],
    }
    holdings.value = [h, ...holdings.value]
  }
  function addContribution(holdingId: string, { date, amount }: { date: string; amount: number }) {
    holdings.value = holdings.value.map((h) =>
      h.id === holdingId && h.kind === 'fund'
        ? { ...h, contributions: [...h.contributions, { id: nid('c'), date, amount: Number(amount) }] }
        : h,
    )
  }
  function addLot(holdingId: string, { date, qty, price }: { date: string; qty: number; price: number }) {
    holdings.value = holdings.value.map((h) =>
      h.id === holdingId && h.kind !== 'fund'
        ? { ...h, lots: [...h.lots, { id: nid('l'), date, qty: Number(qty), price: Number(price) }] }
        : h,
    )
  }
  function removeHolding(id: string) {
    holdings.value = holdings.value.filter((h) => h.id !== id)
  }
  function setRate(cur: string, value: number) {
    config.value = { ...config.value, rates: { ...config.value.rates, [cur]: Number(value) } }
  }
  function addStockType(name: string) {
    const t = name.trim()
    if (t && !stockTypes.value.includes(t)) stockTypes.value = [...stockTypes.value, t]
  }
  function removeStockType(name: string) {
    stockTypes.value = stockTypes.value.filter((x) => x !== name)
  }
  function addFundType(name: string) {
    const t = name.trim()
    if (t && !fundTypes.value.includes(t)) fundTypes.value = [...fundTypes.value, t]
  }
  function removeFundType(name: string) {
    fundTypes.value = fundTypes.value.filter((x) => x !== name)
  }

  /* ---------------- selectors ---------------- */
  const base = computed(() => config.value.base)
  const rate = (cur: string) => config.value.rates[cur] ?? 1
  const toBase = (v: number, cur: string) => v * rate(cur)
  const walletOf = (id: string) => wallets.value.find((w) => w.id === id)

  const holdingCost = (h: Holding): number =>
    h.kind === 'fund'
      ? h.contributions.reduce((s, c) => s + c.amount, 0)
      : h.lots.reduce((s, l) => s + l.qty * l.price, 0)

  const holdingQty = (h: Holding): number | null =>
    h.kind === 'fund' ? null : h.lots.reduce((s, l) => s + l.qty, 0)

  const holdingCurrentValue = (h: Holding): number | null => {
    if (h.kind === 'fund') return h.currentValue == null ? null : h.currentValue
    if (h.currentPrice == null) return null
    return h.currentPrice * (holdingQty(h) ?? 0)
  }

  const holdingGain = (h: Holding): Gain | null => {
    const cv = holdingCurrentValue(h)
    if (cv == null) return null
    const cost = holdingCost(h)
    return { value: cv - cost, pct: cost ? ((cv - cost) / cost) * 100 : 0 }
  }

  const holdingEvents = (h: Holding): { date: string; base: number }[] => {
    const cur = walletOf(h.walletId)?.currency ?? base.value
    return h.kind === 'fund'
      ? h.contributions.map((c) => ({ date: c.date, base: toBase(c.amount, cur) }))
      : h.lots.map((l) => ({ date: l.date, base: toBase(l.qty * l.price, cur) }))
  }

  const walletHoldings = (id: string) => holdings.value.filter((h) => h.walletId === id)
  const walletInvested = (id: string) => walletHoldings(id).reduce((s, h) => s + holdingCost(h), 0) // wallet currency
  const walletInvestedBase = (id: string) => toBase(walletInvested(id), walletOf(id)?.currency ?? base.value)

  const typeKeys: WalletKind[] = ['stocks', 'crypto', 'funds']
  const totalsByType = computed<TypeTotal[]>(() =>
    typeKeys.map((k) => {
      const ws = wallets.value.filter((w) => w.type === k)
      const invested = ws.reduce((s, w) => s + walletInvestedBase(w.id), 0)
      const hCount = ws.reduce((s, w) => s + walletHoldings(w.id).length, 0)
      return { key: k, label: WALLET_TYPES[k].label, accent: WALLET_TYPES[k].accent, invested, wallets: ws.length, holdings: hCount }
    }),
  )

  const grandInvestedBase = computed(() => wallets.value.reduce((s, w) => s + walletInvestedBase(w.id), 0))

  const currentSummary = computed(() => {
    let investedWithCV = 0, currentBase = 0, withCV = 0
    holdings.value.forEach((h) => {
      const cv = holdingCurrentValue(h)
      if (cv == null) return
      const cur = walletOf(h.walletId)?.currency ?? base.value
      investedWithCV += toBase(holdingCost(h), cur)
      currentBase += toBase(cv, cur)
      withCV += 1
    })
    const gain = currentBase - investedWithCV
    return {
      currentBase, investedWithCV, gain,
      pct: investedWithCV ? (gain / investedWithCV) * 100 : 0,
      withCV, total: holdings.value.length,
    }
  })

  const cumulativeSeries = computed<{ data: number[]; labels: string[] }>(() => {
    const ev: { date: string; base: number }[] = []
    holdings.value.forEach((h) => holdingEvents(h).forEach((e) => ev.push(e)))
    if (!ev.length) return { data: [0, 0], labels: ['', ''] }
    ev.sort((a, b) => a.date.localeCompare(b.date))
    const ym = (d: string) => { const x = new Date(d + 'T00:00:00'); return x.getFullYear() * 12 + x.getMonth() }
    const M = ['jan', 'fev', 'mar', 'abr', 'mai', 'jun', 'jul', 'ago', 'set', 'out', 'nov', 'dez']
    const minM = ym(ev[0].date), maxM = ym(ev[ev.length - 1].date)
    const data: number[] = [], labels: string[] = []
    for (let m = minM; m <= maxM; m++) {
      let cum = 0
      ev.forEach((e) => { if (ym(e.date) <= m) cum += e.base })
      data.push(cum)
      labels.push(M[m % 12] + '/' + String(Math.floor(m / 12)).slice(2))
    }
    if (data.length < 2) { data.unshift(0); labels.unshift('') }
    return { data, labels }
  })

  return {
    // state
    config, stockTypes, fundTypes, wallets, holdings,
    // static maps
    WALLET_TYPES, currencies, currencySymbol, badgeFor,
    // actions
    addWallet, addStock, addCrypto, addFund, addContribution, addLot,
    removeHolding, setRate, addStockType, removeStockType, addFundType, removeFundType,
    // selectors (no-arg → computed)
    base, totalsByType, grandInvestedBase, currentSummary, cumulativeSeries,
    // selectors (parametrized → functions)
    rate, toBase, walletOf, holdingCost, holdingQty, holdingCurrentValue, holdingGain,
    holdingEvents, walletHoldings, walletInvested, walletInvestedBase,
  }
})
