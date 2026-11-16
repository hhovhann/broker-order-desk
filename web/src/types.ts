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
