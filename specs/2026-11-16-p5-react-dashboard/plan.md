# Phase 5 Plan - React dashboard

1. React 19 + TypeScript strict, Vite builds into `src/main/resources/static` so one jar serves everything
2. No UI library: `Spark`, `Gauge`, `Depth` are small SVG/CSS components
3. Polling hook with last-good-value semantics instead of a websocket (backlog: SSE)
