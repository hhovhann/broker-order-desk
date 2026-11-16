# Phase 9 Requirements - Charts, analytics, news

## Goal
Staff can look at a company the way analysts do: price history, fundamentals, announcements.

## Scope
- `GET /market/{symbol}/candles?range=1M|2M|3M|1Y|2Y|3Y|ALL` for any symbol and `INDEX`
- `GET /market/compare?symbols=...&range=` on one percentage axis (1 to 6 symbols)
- `GET /analytics`: statements and ratios computed from stored figures and the live last price
- `GET /news?kind=NEWS|DISCLOSURE&symbol=`
- Frontend: SVG candlestick chart with volume and crosshair, compare chart, analytics tabs with CSV export, news page
