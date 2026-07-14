# InvestLog

A manual investment logbook (PT-BR). Track wallets of **stocks, crypto and funds**,
record dated acquisitions/contributions, and see a consolidated, multi-currency view —
all entered by hand (no live market feed). State is in-session and resets on refresh.

Recreated in **Vue 3** from the Claude Design handoff prototype, matching the Tabler
visual language pixel-for-pixel.

## Stack

- Vue 3 (`<script setup>` + TypeScript)
- Pinia (state) · Vue Router 4 (routing)
- Vite 6

## Getting started

```bash
npm install
npm run dev        # start the dev server (http://localhost:8081)
npm run build      # type-check (vue-tsc) + production build
npm run type-check # type-check only
```

## Screens

- **Visão geral** — consolidated KPIs, cumulative-contributions chart, allocation donut,
  and per-type distribution cards.
- **Carteiras** — wallet cards (type, currency, invested, holdings preview) + create-wallet.
- **Investimentos** — filterable table with expandable rows (lots/contributions, preço
  médio) and an add-investment modal for stocks/crypto/funds.
- **Configurações** — currency conversion rates, editable stock/fund types, and the
  accent-color picker.

## Project structure

```
src/
  assets/styles.css      # ported design spec (Tabler-style CSS, theme/accent tokens)
  data/seed.ts           # seed wallets, holdings, types, rates
  stores/                # portfolio (logbook state) + appearance (theme/accent)
  composables/           # formatting, add-investment form, modal controls
  components/            # AppIcon, ui/ atoms, charts/, layout/, forms/, investments/
  views/                 # Overview / Wallets / Investments / Settings
  router/                # routes
```
