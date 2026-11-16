# Phase 6 Validation - Delivery: Docker, Kubernetes, CI

- [x] `docker build` completes and the container starts and answers `/actuator/health/readiness`
- [x] The Kubernetes manifests parse as valid YAML, and the container runs under the same settings they request (non-root, read-only root, capabilities dropped). Not validated against a live cluster
- [ ] The GitHub Actions run on `main` is green
