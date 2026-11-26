# CloudHub frontend

CloudHub's frontend is a React, TypeScript, Vite, and Tailwind CSS application. Shared UI primitives are installed from shadcn/ui into the monorepo's `packages/ui` workspace package.

## Development

Run commands from the repository root:

```powershell
pnpm --filter frontend dev
pnpm --filter frontend lint
pnpm --filter frontend test
pnpm --filter frontend build
```

The Vite development server proxies `/api` requests to `http://localhost:8080`.

## Structure

```text
apps/frontend/src/
├── app/                    # Router, auth gate, and application shell
├── features/
│   ├── accounts/
│   │   ├── api/             # Account and quota requests
│   │   ├── hooks/           # Account query and mutations
│   │   └── components/      # Dashboard and account cards
│   ├── auth/                # Login/logout API, session hook, login screen
│   └── explorer/            # File API, explorer state hook, file UI
└── shared/                  # Locale state and small cross-feature helpers

packages/
├── api-client/              # Shared fetch client and typed API errors
├── contracts/               # Shared frontend API/domain interfaces
├── i18n/                    # English and Vietnamese dictionaries
└── ui/                      # shadcn/ui components and Tailwind class helper
```

Feature-specific requests, state, and UI stay together under each feature's `api`, `hooks`, and `components` directories. Shared contracts, i18n, API transport, and shadcn/ui primitives live in workspace packages so other apps can reuse them.

## UI components and styling

Use the shadcn/ui CLI to add primitives to the shared package:

```powershell
pnpm dlx shadcn@latest add button -c packages/ui
```

Use Tailwind utility classes for all application styling. `src/index.css` only imports Tailwind and registers the shared UI package as a Tailwind source; do not add component stylesheets or handwritten CSS rules.

## Localization

The locale is selected from the browser on first visit, can be switched from the application header, and is persisted in local storage. Add translations to both dictionaries in `packages/i18n/src/index.ts`.
