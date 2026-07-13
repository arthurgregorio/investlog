const SYM: Record<string, string> = { BRL: 'R$', USD: 'US$', EUR: '€' }
const twoDecimalFormatter = new Intl.NumberFormat('pt-BR', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
})
const quantityFormatter = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 8 })
const MONTHS = ['jan', 'fev', 'mar', 'abr', 'mai', 'jun', 'jul', 'ago', 'set', 'out', 'nov', 'dez']

export const fmt = {
  sym: (cur = 'BRL') => SYM[cur] || cur + ' ',
  money: (v: number, cur = 'BRL', opts: { compact?: boolean } = {}) => {
    const s = SYM[cur] || cur + ' '
    if (opts.compact) {
      const absoluteValue = Math.abs(v)
      if (absoluteValue >= 1_000_000)
        return s + ' ' + (v / 1_000_000).toFixed(1).replace('.', ',') + 'M'
      if (absoluteValue >= 100_000) return s + ' ' + Math.round(v / 1_000) + 'k'
    }
    return s + ' ' + twoDecimalFormatter.format(v)
  },
  moneySigned: (v: number, cur = 'BRL', opts: { compact?: boolean } = {}) =>
    (v >= 0 ? '+' : '−') + fmt.money(Math.abs(v), cur, opts),
  pct: (v: number) => twoDecimalFormatter.format(Math.abs(v)) + '%',
  pctSigned: (v: number) => (v >= 0 ? '+' : '−') + twoDecimalFormatter.format(Math.abs(v)) + '%',
  qty: (v: number) => (Number.isInteger(v) ? String(v) : quantityFormatter.format(v)),
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
