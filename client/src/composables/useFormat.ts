/* Formatting helpers — multi-currency money, percentages, quantities, dates.
   Pure functions; exposed as a composable for ergonomic use in <script setup>. */

const SYM: Record<string, string> = { BRL: 'R$', USD: 'US$', EUR: '€' }
const num2 = new Intl.NumberFormat('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
const num0 = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 0 })
const numQ = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 8 })
const MONTHS = ['jan', 'fev', 'mar', 'abr', 'mai', 'jun', 'jul', 'ago', 'set', 'out', 'nov', 'dez']

export const fmt = {
  sym: (cur = 'BRL') => SYM[cur] || cur + ' ',
  money: (v: number, cur = 'BRL', opts: { compact?: boolean } = {}) => {
    const s = SYM[cur] || cur + ' '
    const big = opts.compact && Math.abs(v) >= 100000
    return s + ' ' + (big ? num0.format(v) : num2.format(v))
  },
  moneySigned: (v: number, cur = 'BRL') => (v >= 0 ? '+' : '−') + fmt.money(Math.abs(v), cur),
  pct: (v: number) => num2.format(Math.abs(v)) + '%',
  pctSigned: (v: number) => (v >= 0 ? '+' : '−') + num2.format(Math.abs(v)) + '%',
  qty: (v: number) => (Number.isInteger(v) ? String(v) : numQ.format(v)),
  date: (iso: string) => {
    const d = new Date(iso + 'T00:00:00')
    return `${d.getDate()} ${MONTHS[d.getMonth()]} ${d.getFullYear()}`
  },
  dateShort: (iso: string) => {
    const d = new Date(iso + 'T00:00:00')
    return `${MONTHS[d.getMonth()]}/${String(d.getFullYear()).slice(2)}`
  },
}

export function useFormat() {
  return fmt
}
