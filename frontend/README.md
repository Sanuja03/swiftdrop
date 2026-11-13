# SwiftDrop Frontend

React + Vite. The code is grouped by **feature**, the same way the backend is split into modules,
so each team member works mostly inside their own folder.

## Run it

```bash
npm install
npm run dev
```

Open http://localhost:5173. The dev server forwards `/api` and `/ws` to the backend on
`localhost:8080`, so start the backend first.

## Folder structure

```text
src/
├── App.jsx            routes for the whole app
├── main.jsx           entry point
├── features/          one folder per feature
│   ├── tracking/      public tracking, live updates, shipments, delivery history
│   ├── receipts/      PDF receipt download
│   ├── booking/       create delivery, pricing
│   ├── account/       profile, settings, sign-in
│   └── dashboard/     customer overview
└── shared/            used by every feature
    ├── api/           httpClient (fetch wrapper, ApiError)
    ├── components/    ui/ (Button, Card, StatusBadge…) and brand/ (Logo…)
    ├── layouts/       customer layout (sidebar, top bar) and public layout
    ├── config/        routes
    ├── domain/        parcel status rules, service options
    ├── styles/        colour tokens and global CSS
    └── utils/         formatting and download helpers
```

A feature folder can contain `api/`, `components/`, `hooks/`, `pages/`, `domain/` and an `index.js`.

## Rules

1. **Your code lives in your feature folder.** Add a new folder under `features/` for a new feature.
2. **Each feature exposes what others may use through its `index.js`.** Other features and
   `App.jsx` import only from there:
   ```js
   import { ReceiptButton } from '@/features/receipts'       // yes
   import ReceiptButton from '@/features/receipts/components/ReceiptButton' // no
   ```
3. **Inside a feature, use relative imports** (`../hooks/useParcelTracking`).
4. **Shared code is imported with the `@` alias** (`@/shared/components/ui/Button`).
5. **Move something into `shared/` only when two or more features need it.** Changes there affect
   everyone, so mention them in your pull request.
6. **Use the colour tokens** from `shared/styles/tokens.css` (`var(--color-orange)`), never hard-coded colours.
7. Each component keeps its styles next to it in a CSS Module (`Button.module.css`).
