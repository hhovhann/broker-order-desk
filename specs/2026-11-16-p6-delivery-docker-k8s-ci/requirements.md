# Phase 6 Requirements - Delivery: Docker, Kubernetes, CI

## Goal
Anyone can build, run and ship it the same way, locally and in CI.

## Scope
- Multi-stage `Dockerfile` (Node build, Maven build, JRE runtime, non-root user)
- `compose.yaml` with PostgreSQL and the desk
- `k8s/broker-order-desk.yaml`: ConfigMap, Secret placeholder, Deployment (`Recreate`, 1 replica, probes, resources, read-only root fs, non-root), Service
- `.github/workflows/ci.yml`: UI build, `./mvnw verify`, image build
