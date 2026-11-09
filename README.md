# InvestLog

A self-hosted logbook for your investments. Record wallets of stocks and FIIs, crypto and funds, every purchase, contribution, withdrawal and reinvestment, and see a consolidated, multi-currency view of your portfolio: what you put in, what it is worth, what you realised and how each wallet and strategy is doing. Stock, FII and crypto prices update automatically. The interface is in Brazilian Portuguese.

- **Website:** https://investlog.com.br
- **Live demo:** https://demo.investlog.com.br

## Quick start

```bash
cp example.env .env      # optional, the defaults already work
docker compose up -d
```

Open **http://localhost:8081** and log in with `admin@admin.com` / `admin`, then change that password. The [Quick start](https://github.com/arthurgregorio/investlog/wiki/Quick-start) page covers two-factor enrolment, sample data and release candidates.

## Documentation

Everything else lives in the [wiki](https://github.com/arthurgregorio/investlog/wiki):

- **Getting started** — [Quick start](https://github.com/arthurgregorio/investlog/wiki/Quick-start), [Features](https://github.com/arthurgregorio/investlog/wiki/Features)
- **Technology & design** — [Architecture](https://github.com/arthurgregorio/investlog/wiki/Architecture), [Server stack](https://github.com/arthurgregorio/investlog/wiki/Server-stack), [Client stack](https://github.com/arthurgregorio/investlog/wiki/Client-stack), [Security model](https://github.com/arthurgregorio/investlog/wiki/Security-model), [Background jobs & market data](https://github.com/arthurgregorio/investlog/wiki/Background-jobs-and-market-data)
- **Build & run** — [Build from source](https://github.com/arthurgregorio/investlog/wiki/Build-from-source), [Local development](https://github.com/arthurgregorio/investlog/wiki/Local-development), [CI pipeline](https://github.com/arthurgregorio/investlog/wiki/CI-pipeline)
- **Configuration** — [Environment variables](https://github.com/arthurgregorio/investlog/wiki/Environment-variables), [Spring profiles](https://github.com/arthurgregorio/investlog/wiki/Spring-profiles), [Google OAuth2 login](https://github.com/arthurgregorio/investlog/wiki/Google-OAuth2-login), [E-mail](https://github.com/arthurgregorio/investlog/wiki/E-mail), [Notifications](https://github.com/arthurgregorio/investlog/wiki/Notifications), [Market data keys](https://github.com/arthurgregorio/investlog/wiki/Market-data-keys)
- **Operations** — [Backup & restore](https://github.com/arthurgregorio/investlog/wiki/Backup-and-restore), [Sample data](https://github.com/arthurgregorio/investlog/wiki/Sample-data), [Health, logs & reset](https://github.com/arthurgregorio/investlog/wiki/Health-logs-and-reset), [Deploying without Compose](https://github.com/arthurgregorio/investlog/wiki/Deploying-without-Compose), [Troubleshooting](https://github.com/arthurgregorio/investlog/wiki/Troubleshooting)
- **Project** — [Releases & deployment](https://github.com/arthurgregorio/investlog/wiki/Releases-and-deployment), [Contributing](https://github.com/arthurgregorio/investlog/wiki/Contributing)

## Follow development

- [Issues](https://github.com/arthurgregorio/investlog/issues) — every piece of work starts as one
- [Milestones](https://github.com/arthurgregorio/investlog/milestones) — what is planned for each version
- [Releases](https://github.com/arthurgregorio/investlog/releases) — published versions and release candidates

## License

Licensed under the [Apache License 2.0](LICENSE).
