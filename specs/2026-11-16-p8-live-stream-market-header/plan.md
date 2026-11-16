# Phase 8 Plan - Live stream and market header

1. EventSource cannot send an Authorization header, so the client reads the stream with `fetch`
2. Time only through an injected `Clock`
