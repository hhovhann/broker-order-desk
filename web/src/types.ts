export interface Me { username: string; roles: string[]; accountId: number | null }
export interface BoardRow {
  symbol: string; name: string; offerShares: number; offerPriceMills: number; lastPriceMills: number; bidPriceMills: number; bidShares: number
  trades: number; averageMills: number; shares: number; valueMills: number; prevCloseMills: number; highMills: number; lowMills: number
  changeMills: number; changeBps: number; spark: number[]
}
export interface Level { priceMills: number; quantity: number; orders: number }
export interface Depth { bids: Level[]; asks: Level[] }
export interface TradePrint { priceMills: number; quantity: number; executedAt: string }
export interface OrderView {
  orderId: number; clientOrderId: string; symbol: string; side: 'BUY' | 'SELL'; quantity: number; limitPriceMills: number
  filledQty: number; avgPriceMills: number; status: 'NEW' | 'PARTIALLY_FILLED' | 'FILLED' | 'CANCELLED' | 'REJECTED'; rejectReason: string | null
}
export interface Position { symbol: string; quantity: number; lastPriceMills: number; valueMills: number }
export interface Report {
  accountId: number; owner: string; cashMills: number; loanMills: number; marketValueMills: number; equityMills: number; equityBps: number
  maintenanceBps: number; buyingPowerMills: number; reservedMills: number; marginCall: boolean; positions: Position[]
}
export interface StatementLine { tradeId: number; executedAt: string; symbol: string; side: 'BUY' | 'SELL'; quantity: number; priceMills: number; valueMills: number }
export interface AuditLine { at: string; actor: string; action: string; detail: string; correlationId: string | null }
export interface Rules { bandPercent: number; maintenanceMarginPercent: number; maxOrderValueMills: number }
export interface QuoteResult { bid: OrderView; ask: OrderView }
export interface Summary { session: 'OPEN' | 'CLOSED'; simulatedOpen: boolean; indexPoints: number; indexChangeBps: number; trades: number; shares: number; valueMills: number; advancers: number; decliners: number; unchanged: number; asOf: string }
export interface Candle { date: string; open: number; high: number; low: number; close: number; volume: number }
export interface Series { symbol: string; points: number[][] }
export interface AnalyticsRow {
  symbol: string; name: string; period: string; lastPriceMills: number; assetsMills: number; liabilitiesMills: number; equityMills: number; profitMills: number
  profitOverEquityBps: number | null; epsMills: number | null; dividendYieldBps: number | null; bookValueMills: number | null; peX1000: number | null; pbX1000: number | null; upDaysYear: number
}
export interface NewsItem { id: number; kind: string; symbol: string | null; date: string; title: string; body: string }
export interface Alert { id: number; symbol: string; direction: 'ABOVE' | 'BELOW'; priceMills: number; status: 'ACTIVE' | 'TRIGGERED'; createdAt: string; triggeredAt: string | null; triggeredPriceMills: number | null }
export interface Transfer { id: number; clientRef: string; direction: 'DEPOSIT' | 'WITHDRAW'; amountMills: number; bank: string; rail: string; status: string; createdAt: string }
export interface Wallet { cashMills: number; reservedMills: number; withdrawableMills: number; transfers: Transfer[] }
export interface Bank { name: string; rail: string }
export interface Member { owner: string; quotedSymbols: number; orders: number; filledShares: number; trades: number }
export interface Print { time: string; priceMills: number; quantity: number; kind: 'LP_BUY' | 'LP_SELL' | 'MARKET_BUY' | 'MARKET_SELL' }
export interface ApplicationRow { id: number; fullName: string; email: string; type: string; note: string | null; status: string; createdAt: string }
