# REST Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire the Vue 3 frontend to the Spring Boot REST API, moving all business logic to the backend and replacing in-memory seed state with real API calls.

**Architecture:** Split Pinia stores (wallets, holdingsList, overview, typesList, rates) backed by an axios API layer; backend gains a unified `/holdings` list endpoint and `/overview` summary/series endpoints; seed data removed from frontend and converted to SQL.

**Tech Stack:** Spring Boot 4 / jOOQ / Kotlin (backend) · Vue 3 / Pinia / axios / Buefy DataTable / Vitest (frontend)

---

## Backend Tasks

- [ ] **Task B1** — DB migration: convert `holdings_overview` from materialized view to regular view
- [ ] **Task B2** — Add `holdingCount` + `totalInvested` to `WalletResponse` (subquery from `holdings_overview`)
- [ ] **Task B3** — Add `GET /{holdingId}` to stock, crypto, and fund holding controllers + repositories
- [ ] **Task B4** — `holdingsoverview` module: payloads + repository (jOOQ query on view + wallet join)
- [ ] **Task B5** — `holdingsoverview` module: service + controller + integration test
- [ ] **Task B6** — `overview` module: payloads + repository (summary + series SQL)
- [ ] **Task B7** — `overview` module: service + controller + integration test
- [ ] **Task B8** — Fix abbreviations in existing backend files (`WalletRepository`, `StockHoldingRepository`, etc.)
- [ ] **Task B9** — `sample-data.sql` (convert frontend seed to SQL) + update `server/CLAUDE.md`

## Frontend Tasks

- [ ] **Task F1** — Install `axios`, `vitest`, `@vue/test-utils`, `jsdom`; add vitest config; add Vite dev proxy
- [ ] **Task F2** — `src/api/client.ts` (axios instance + error interceptor)
- [ ] **Task F3** — `src/api/wallets.ts`, `src/api/overview.ts`, `src/api/types.ts`, `src/api/rates.ts`
- [ ] **Task F4** — `src/api/holdings.ts`, `src/api/holdingDetails.ts`
- [ ] **Task F5** — Update `src/types.ts` (rename `Wallet.type` → `Wallet.kind`, add `HoldingRow`, `PortfolioSummary`, etc.)
- [ ] **Task F6** — `src/stores/wallets.ts`
- [ ] **Task F7** — `src/stores/holdingsList.ts`
- [ ] **Task F8** — `src/stores/overview.ts`
- [ ] **Task F9** — `src/stores/typesList.ts` + `src/stores/rates.ts`
- [ ] **Task F10** — Update `OverviewView.vue`
- [ ] **Task F11** — Update `WalletsView.vue`
- [ ] **Task F12** — Update `InvestmentsView.vue` (Buefy DataTable + backend pagination)
- [ ] **Task F13** — New `HoldingDetailPanel.vue` (lazy-loads lots/contributions on expand, handles delete)
- [ ] **Task F14** — Update `PositionAdder.vue` (API calls instead of store mutations)
- [ ] **Task F15** — Update `SettingsView.vue`
- [ ] **Task F16** — Update `useAddInvestmentForm.ts` + `AddInvestmentForm.vue`
- [ ] **Task F17** — Update `AddInvestmentModal.vue` + `CreateWalletModal.vue`
- [ ] **Task F18** — Remove `src/stores/portfolio.ts` + `src/data/seed.ts`; fix remaining references
- [ ] **Task F19** — Frontend unit tests (stores + composable)
- [ ] **Task F20** — Update `client/CLAUDE.md`
- [ ] **Task F21** — Browser smoke test with Chrome MCP
