/* InvestLog — manual logbook seed data.
   No live market feed: every value is entered by hand.
   Money invested = cost basis. "Current value" is an OPTIONAL manual field
   (per holding) so gain/loss only appears where the user filled it in.

   Multi-currency: each wallet has a currency; the consolidated view converts
   to a base currency using the manually-configured rates in `config.rates`. */

import type {
  CurrencyConfig,
  Holding,
  Wallet,
  WalletKind,
  WalletTypeMeta,
} from '@/types'

/* ---- Currency config (editable in Configurações) ---- */
export const config: CurrencyConfig = {
  base: 'BRL',
  // value of 1 unit of the currency expressed in the BASE currency
  rates: { BRL: 1, USD: 5.42, EUR: 5.88 },
}

/* ---- Wallet types ---- */
// kind -> presentation. Stable across the app.
export const WALLET_TYPES: Record<WalletKind, WalletTypeMeta> = {
  stocks: { label: 'Ações', accent: 'var(--wt-stocks)', icon: 'trendUp' },
  crypto: { label: 'Cripto', accent: 'var(--wt-crypto)', icon: 'coins' },
  funds: { label: 'Fundos', accent: 'var(--wt-funds)', icon: 'building' },
}

/* ---- Stock sub-types (created before registering a stock) ---- */
export const stockTypes = ['Ação', 'ETF', 'FII', 'BDR']

/* ---- Fund sub-types (created before registering a fund) ---- */
export const fundTypes = ['PGBL', 'VGBL', 'Multimercados', 'Renda Fixa']

/* ---- Wallets ---- */
export const wallets: Wallet[] = [
  { id: 'w1', name: 'Carteira Principal', type: 'stocks', currency: 'BRL' },
  { id: 'w2', name: 'Cripto Longo Prazo', type: 'crypto', currency: 'BRL' },
  { id: 'w3', name: 'Reserva & Fundos', type: 'funds', currency: 'BRL' },
  { id: 'w4', name: 'Stocks EUA', type: 'stocks', currency: 'USD' },
]

/* ---- Holdings ----
   stock/crypto: lots [{ id, date, qty, price }], optional currentPrice
   fund:         contributions [{ id, date, amount }], optional currentValue  */
export const holdings: Holding[] = [
  // Carteira Principal (BRL)
  { id: 'h1', kind: 'stock', walletId: 'w1', stockType: 'Ação', ticker: 'PETR4', name: 'Petrobras',
    currentPrice: 38.42,
    lots: [{ id: 'l1', date: '2025-05-19', qty: 18, price: 35.10 }, { id: 'l2', date: '2025-12-08', qty: 12, price: 37.60 }] },
  { id: 'h2', kind: 'stock', walletId: 'w1', stockType: 'Ação', ticker: 'VALE3', name: 'Vale',
    currentPrice: 61.18,
    lots: [{ id: 'l3', date: '2025-09-01', qty: 15, price: 58.40 }] },
  { id: 'h3', kind: 'stock', walletId: 'w1', stockType: 'Ação', ticker: 'ITUB4', name: 'Itaú Unibanco',
    currentPrice: 33.05,
    lots: [{ id: 'l4', date: '2025-07-22', qty: 15, price: 30.10 }, { id: 'l5', date: '2026-01-15', qty: 10, price: 31.90 }] },
  { id: 'h4', kind: 'stock', walletId: 'w1', stockType: 'ETF', ticker: 'BOVA11', name: 'iShares Ibovespa',
    currentPrice: 128.40,
    lots: [{ id: 'l6', date: '2025-08-12', qty: 8, price: 121.05 }] },
  { id: 'h5', kind: 'stock', walletId: 'w1', stockType: 'FII', ticker: 'MXRF11', name: 'Maxi Renda FII',
    currentPrice: 10.55,
    lots: [{ id: 'l7', date: '2025-06-10', qty: 100, price: 10.30 }, { id: 'l8', date: '2025-11-05', qty: 80, price: 10.10 }] },

  // Stocks EUA (USD)
  { id: 'h6', kind: 'stock', walletId: 'w4', stockType: 'Ação', ticker: 'AAPL', name: 'Apple Inc.',
    currentPrice: 233.20,
    lots: [{ id: 'l9', date: '2025-07-01', qty: 5, price: 210.50 }] },
  { id: 'h7', kind: 'stock', walletId: 'w4', stockType: 'ETF', ticker: 'VOO', name: 'Vanguard S&P 500',
    lots: [{ id: 'l10', date: '2025-09-10', qty: 2, price: 540.00 }] },

  // Cripto Longo Prazo (BRL)
  { id: 'h8', kind: 'crypto', walletId: 'w2', ticker: 'BTC', name: 'Bitcoin',
    currentPrice: 384210.00,
    lots: [{ id: 'l11', date: '2025-04-09', qty: 0.004, price: 312500.00 }, { id: 'l12', date: '2025-11-21', qty: 0.002, price: 358900.00 }] },
  { id: 'h9', kind: 'crypto', walletId: 'w2', ticker: 'ETH', name: 'Ethereum',
    currentPrice: 19480.00,
    lots: [{ id: 'l13', date: '2025-07-05', qty: 0.05, price: 16240.00 }] },

  // Reserva & Fundos (BRL)
  { id: 'h10', kind: 'fund', walletId: 'w3', name: 'Tesouro Selic 2029', fundType: 'Renda Fixa',
    currentValue: 4980.00,
    contributions: [{ id: 'c1', date: '2025-03-15', amount: 2000 }, { id: 'c2', date: '2025-07-20', amount: 1500 }, { id: 'c3', date: '2025-12-01', amount: 1200 }] },
  { id: 'h11', kind: 'fund', walletId: 'w3', name: 'Nubank Renda Fixa', fundType: 'Multimercados',
    contributions: [{ id: 'c4', date: '2025-05-10', amount: 1000 }, { id: 'c5', date: '2025-09-12', amount: 800 }] },
]

/* Stable badge colors per ticker/fund (initials squares). */
const BADGE: Record<string, string> = {
  PETR4: '#0b6b3a', VALE3: '#1f7a8c', ITUB4: '#e8731a', BOVA11: '#2b6cb0',
  MXRF11: '#6d4bd8', AAPL: '#444b54', VOO: '#b0306b', BTC: '#f7931a', ETH: '#5b6dd8',
}
export function badgeFor(h: Holding): string {
  const ticker = h.kind === 'fund' ? undefined : h.ticker
  return (ticker && BADGE[ticker]) || (h.kind === 'fund' ? '#2a8f6f' : '#5b6dd8')
}

export const currencies = ['BRL', 'USD', 'EUR']
export const currencySymbol: Record<string, string> = { BRL: 'R$', USD: 'US$', EUR: '€' }
