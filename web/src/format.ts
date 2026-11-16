// Prices and money are integers in mills (1/1000 QAR) on the wire; formatting happens only here.
export const px = (m: number) => (m / 1000).toFixed(3)
export const money = (m: number) => (m / 1000).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
export const qty = (n: number) => n.toLocaleString('en-US')
export const pct = (bps: number) => `${(bps / 100).toFixed(2)}%`
export const signed = (n: string) => (n.startsWith('-') ? n : `+${n}`)
export const millsFromInput = (s: string) => Math.round(parseFloat(s) * 1000)
export const timeOf = (iso: string) => iso.slice(11, 19)

const REASONS: Record<string, string> = {
  UNKNOWN_SYMBOL: 'Unknown symbol', PRICE_OUTSIDE_BAND: 'Price is outside the daily band', MAX_ORDER_VALUE: 'Order value is above the limit',
  INSUFFICIENT_BUYING_POWER: 'Not enough buying power (cash plus margin line)', INSUFFICIENT_SHARES: 'Not enough shares: short selling is not allowed',
  SELF_TRADE_PREVENTED: 'Would trade against your own order', SYSTEM_ERROR: 'System error, nothing was executed',
}
export const reason = (r: string | null) => (r ? REASONS[r] ?? r : '')
