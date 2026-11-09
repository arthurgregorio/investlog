# InvestLog client

The InvestLog single-page app: Vue 3, TypeScript, Pinia and Buefy, built with Vite and served by nginx in production. Requires Node 24.15 or newer.

```bash
npm install
npm run dev        # dev server at http://localhost:8081, proxying /private to localhost:8080
npm run build      # type-check, then production build into dist/
```

The stack and design are described in the wiki's [Client stack](https://github.com/arthurgregorio/investlog/wiki/Client-stack) page, and every command (tests, linting) in [Local development](https://github.com/arthurgregorio/investlog/wiki/Local-development#client).
