# DisciplineOS Console (Web App)

The authoritative web dashboard for DisciplineOS. This app owns the database, handles
device pairing with the Enforcer (native Android app), and provides the UI for goal
management, tier status, Tribunal, reports, and dispute resolution.

**Status:** Scaffold only (Prompt 3 of 5). Business logic ported in Prompt 4.

## Stack

- **Framework:** Next.js 14 (App Router)
- **Language:** TypeScript
- **Database:** PostgreSQL via Prisma ORM
- **Auth:** NextAuth.js (web sessions) + JWT device tokens (Enforcer pairing)
- **Styling:** Tailwind CSS

## Getting Started

### Prerequisites

- Node.js 18+
- PostgreSQL 14+

### Setup

```bash
cd web-app

# Install dependencies
npm install

# Set up environment
cp .env.example .env
# Edit .env with your PostgreSQL credentials

# Generate Prisma client
npx prisma generate

# Push schema to database
npx prisma db push

# Start dev server
npm run dev
```

The app runs at http://localhost:3000.

### Database

```bash
# Open Prisma Studio (visual DB browser)
npx prisma studio

# Reset database
npx prisma db push --force-reset
```

## Project Structure

```
web-app/
├── prisma/
│   └── schema.prisma          # Authoritative DB schema (matches Kotlin entities)
├── src/
│   ├── app/
│   │   ├── (auth)/            # Login, register pages
│   │   ├── (dashboard)/       # Main app pages
│   │   │   ├── missions/      # PRD §9 — Mission management
│   │   │   ├── goals/         # PRD §8 — Goal definition
│   │   │   ├── tier/          # PRD §12 — Tier status
│   │   │   ├── tribunal/      # PRD §30 — Tribunal reviews
│   │   │   ├── reports/       # PRD §32-34 — Daily/Weekly/Monthly reports
│   │   │   └── settings/      # Account, paired devices
│   │   ├── api/
│   │   │   ├── auth/          # Pairing code + device token endpoints
│   │   │   ├── sync/          # Enforcer ↔ Console sync protocol
│   │   │   ├── users/         # User CRUD
│   │   │   ├── missions/      # Session CRUD
│   │   │   ├── goals/         # Goal CRUD
│   │   │   ├── profiles/      # Mission profile CRUD
│   │   │   ├── ledger/        # Ledger entries (authoritative)
│   │   │   ├── tier-events/   # Tier transition history
│   │   │   └── reports/       # Report generation
│   │   ├── layout.tsx         # Root layout
│   │   └── page.tsx           # Redirect to dashboard
│   └── lib/
│       ├── prisma.ts          # Prisma client singleton
│       └── auth.ts            # Device pairing + JWT utilities
├── package.json
├── tsconfig.json
├── next.config.js
├── tailwind.config.js
└── .env.example
```

## API Endpoints

### Auth (Design doc §3.1)

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/auth/pair` | Generate 6-digit pairing code (5-min expiry) |
| POST | `/api/auth/login` | Exchange pairing code for device token |

### Sync Protocol (Design doc §2)

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/sync/violations` | Enforcer pushes pending violations |
| GET | `/api/sync/pending` | Enforcer pulls pending Console actions |
| POST | `/api/sync/state` | Full state refresh for Enforcer |

### Data CRUD

| Method | Path | Description |
|--------|------|-------------|
| GET/POST | `/api/users` | List/create users |
| GET/POST | `/api/missions` | List/create enforcement sessions |
| GET/POST | `/api/goals` | List/create goal missions |
| GET/POST | `/api/profiles` | List/create mission profiles |
| GET | `/api/ledger` | List ledger entries |
| GET | `/api/tier-events` | List tier transition events |
| GET | `/api/reports` | Generate reports (stub) |

## Schema Design

The Prisma schema in `prisma/schema.prisma` is the **authoritative database schema**.
It matches the Kotlin entities from `data/src/main/java/com/disciplineos/data/entity/`
field-for-field, preserving:

- Exact field names (no silent renaming at sync boundary)
- Exact nullability (nullable fields encode product decisions)
- Exact enum values (Tier.RECRUIT/OPERATOR/WARDEN/IRON, etc.)
- Kdoc semantics documented in comments

**Tables included (Console-authoritative):**
- `User`, `LedgerEntry`, `Violation`, `EnforcementSession`, `GoalMission`, `MissionProfile`
- `TierEvent`, `MissionPeriod`, `MissionLogEntry`, `Trigger`, `Milestone`, `OutputArtifact`
- `AdherenceLedgerEntry`, `UnsupervisedSignal`, `OnboardingScreenEvent`
- `PredictiveFailureAlertDismissal`
- `DeviceCredentials` (pairing), `PendingViolation` (sync reconciliation)

**Tables NOT included (Enforcer-local-only, per design doc §1.3):**
- `cached_user`, `cached_goal_missions`, `cached_mission_profiles`
- `sync_metadata`
- `provisional_ledger_entries` (these exist only on the Enforcer)

## What's NOT Implemented Yet (Prompt 4 scope)

- Business logic (Debt/Reputation calculation, tier transitions, dispute resolution)
- Real auth (NextAuth integration, password hashing)
- Report generation algorithms
- Tribunal AI (Behavioral Correction Plan generation)
- WebSocket/SSE push to Enforcer
- Frontend data fetching and state management

All of these are marked with `TODO(prompt4)` in the code.
