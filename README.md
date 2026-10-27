# Mobile Expense Sharing App

An Android mobile application developed for the **Mobile Software Design and Development** course at the University of Southern Denmark (SDU).

The application helps groups of people manage shared expenses, split costs, track balances, and see who owes money to whom.

The concept is similar to WeShare and MobilePay Groups.

## Technology Stack

- React Native
- Expo SDK 57
- TypeScript
- Expo Router
- Jest and React Native Testing Library
- ESLint
- GitHub Actions
- npm

**Repository:** https://github.com/TheBarlach/mobile-app

## 1. Requirements

Before getting started, install:

- [Node.js](https://nodejs.org/) — Node.js 22 recommended
- npm (included with Node.js)
- [Git](https://git-scm.com/)
- [Visual Studio Code](https://code.visualstudio.com/)
- [Expo Go](https://expo.dev/go) on an Android device, or an Android emulator through Android Studio

Verify your installation:

```bash
node --version
npm --version
git --version
```

The project uses npm and includes a `package-lock.json`. Do not switch package managers without team agreement.

## 2. Clone the Repository

Open a terminal and run:

```bash
git clone https://github.com/TheBarlach/mobile-app.git
cd mobile-app
```

If you already have the repository:

```bash
git switch main
git pull origin main
```

## 3. Install Dependencies

Install all project dependencies:

```bash
npm ci
```

Then configure the local environment:

```bash
npm run setup
```

This automatically creates `.env.local` using the values
from `.env.example`.

The setup script will not overwrite an existing `.env.local`.

You only need to run this setup once.

## 4. Start the Application

Start Expo:

```bash
npx expo start
```

If your phone cannot connect, try:

```bash
npx expo start --tunnel
```

Scan the QR code using Expo Go on your Android device.

## 4. Start the Application

Start the Expo development server:

```bash
npx expo start
```

A QR code should appear in the terminal.

### Android Phone

1. Install Expo Go.
2. Ensure your computer and phone can communicate over the local network.
3. Scan the QR code using Expo Go or your phone's camera.
4. The application should open on your device.

### Connection Problems or Remote Development

Try:

```bash
npx expo start --tunnel
```

This is useful when developing through SSH, on different networks, or when a local connection is unavailable.

### Android Emulator

With an Android emulator installed and configured:

```bash
npm run android
```

Expo Go is suitable for the initial project. Some future native dependencies may require a development build instead.

## 5. Project Structure

```text
mobile-app/
├── assets/                  Images, icons and other assets
├── docs/                    Project and AI documentation
├── src/
│   ├── app/                 Expo Router screens and layouts
│   ├── components/          Shared UI components
│   ├── constants/           Shared constants
│   ├── features/
│   │   ├── auth/            Profiles and authentication
│   │   ├── groups/          Group management
│   │   ├── expenses/        Expense management
│   │   ├── balances/        Debt calculations
│   │   └── notifications/   Notifications and reminders
│   ├── hooks/               Shared React hooks
│   ├── services/
│   │   ├── api/             Backend communication
│   │   ├── storage/         Local persistence
│   │   └── sync/            Offline synchronization
│   ├── types/               Shared TypeScript definitions
│   └── utils/               Helper functions
├── supabase
├── .github/workflows/       CI pipeline
├── AGENTS.md                Project development rules
├── CLAUDE.md                Claude project instructions
├── package.json
└── tsconfig.json
```

Some directories are placeholders and will be populated during development.

## Supabase Database

The application uses Supabase for authentication and
database access.

### Environment Configuration

The project uses two environment variables:

- `EXPO_PUBLIC_SUPABASE_URL`
- `EXPO_PUBLIC_SUPABASE_PUBLISHABLE_KEY`

These are public client-side configuration values.

Run:

```bash
npm run setup
```

This copies `.env.example` to `.env.local`.

The `.env.local` file is ignored by Git and must not
be committed.

### Security

- Never add a Supabase secret key to the mobile app.
- Never commit passwords, private tokens or credentials.
- All database access must be protected using Row Level
  Security (RLS).
- Users should only access groups and expenses they are
  authorized to view or modify.

### Supabase Client

The shared Supabase client is located at:

`src/services/api/supabase.ts`

Import this client instead of creating new Supabase
connections in individual components.

### Where Should New Code Go?

- New pages and navigation: `src/app/`
- Feature-specific business logic: `src/features/<feature>/`
- Reusable UI: `src/components/`
- Backend communication: `src/services/api/`
- Offline storage: `src/services/storage/`
- Background retry and synchronization: `src/services/sync/`
- Shared models: `src/types/`
- General calculations: `src/utils/`

Do not put all application logic directly inside the screens.

## 6. Main Project Features

The application should support:

### User Profiles

- Create and manage a user profile
- Update personal information
- Manage notification preferences

### Group Management

- Create and manage groups
- Add and remove participants
- Name and describe groups

### Expense Management

- Add expenses with amount and payer
- Split costs between all or selected participants
- Optionally attach receipts

### Balances

- Calculate amounts owed and owing
- Display group balances
- Show expense and transaction summaries

### Notifications

- Notify members about expenses and updates
- Send payment reminders

These represent course requirements and planned functionality, not a claim that all features are already implemented.

## 7. Reliability Requirements

Reliability is a mandatory part of the project.

### Fault Tolerance

Temporary network failures should not prevent continued app usage. Failed requests must be retried in the background.

### Offline Availability

Previously retrieved data must remain accessible when the device has no network connection.

### Request Status

The application must indicate whether an operation is pending, successful, or failed.

Users must be informed when a previously failed request later succeeds.

These requirements must be implemented and tested as development progresses.

## 8. Development Workflow

The `main` branch contains the stable version of the project.

Do not develop features directly on `main`.

### Start a New Feature

```bash
git switch main
git pull origin main
git switch -c feature/your-feature-name
```

Examples:

```text
feature/login-screen
feature/group-management
feature/add-expense
feature/balance-overview
feature/offline-storage
```

### Commit Your Changes

```bash
git add .
git commit -m "feat: add group creation screen"
```

### Push Your Branch

```bash
git push -u origin feature/your-feature-name
```

Create a pull request on GitHub targeting `main`.

Before merging:

1. Ensure GitHub Actions passes.
2. Request a review from another team member.
3. Resolve review comments.
4. Confirm that relevant tests are included.

AI-generated code must be reviewed by a teammate who did not generate it.

## 9. Testing and Code Quality

Before opening a pull request, run:

```bash
npm run precommit
```

This runs:

```bash
npm run lint
npm run check
npm run test:unit
```

| Command                    | Purpose                              |
| -------------------------- | ------------------------------------ |
| `npm run lint`             | Checks ESLint rules                  |
| `npm run check`            | Checks TypeScript                    |
| `npm run test:unit`        | Runs Jest unit tests                 |
| `npm run precommit`        | Runs all three checks                |
| `npx expo install --check` | Checks Expo dependency compatibility |

Existing tests include expense splitting and rounding.

New business logic should include relevant tests.

## 10. Continuous Integration

GitHub Actions automatically runs when:

- A pull request targeting `main` is opened or updated.
- Changes are pushed to `main`.

The CI pipeline checks:

- Dependency installation
- Expo dependency compatibility
- ESLint
- TypeScript
- Jest unit tests

All required checks must pass before merging. Android build verification is planned separately.

## 11. AI Usage

Generative AI is allowed for this project but must be documented.

Read [AGENTS.md](AGENTS.md) before contributing.

Significant AI interactions must be recorded in:

- [AI Dossier](docs/ai-dossier.md)
- [AI Failure Log](docs/ai-failure-log.md)

Document the tool and mode, use case, prompt or chat link, verification, evaluation, and final decision.

The project must include at least three verified examples of incorrect or unsuitable AI-generated output and how the team detected them.

AI-generated code must pass tests, CI, and independent review before merging.

## 12. Troubleshooting

### Expo Command Not Found

Install the project's dependencies:

```bash
npm ci
```

Then run:

```bash
npx expo start
```

### App Cannot Connect to Expo

Try:

```bash
npx expo start --tunnel
```

### TypeScript CSS Import Errors

Check whether the project contains the CSS type declarations in `src/types/`, then run:

```bash
npm run check
```

Do not remove CSS imports just to make TypeScript pass.

### Dependency Problems

Check compatibility:

```bash
npx expo install --check
```

Avoid forcing incompatible React or React Native upgrades.

### Missing Supabase Environment Variables

If you get an error about missing Supabase variables,
run:

```bash
npm run setup
```

Then restart Expo:

```bash
npx expo start --clear
```

If `.env.local` already exists but contains incorrect
values, update it manually or remove it and run setup again.

## 13. Documentation and Deliverables

Course deliverables include:

- Design document (maximum 3 pages)
- Implementation document (maximum 4 pages)
- Architecture diagram explaining reliability
- Testing of at least one Reliability QA
- Short demonstration video
- GitHub repository link

The project documentation should be updated as the implementation develops.

## 14. Definition of Done

A feature is ready when:

- It satisfies its relevant requirement.
- Code passes lint and TypeScript checks.
- Appropriate automated tests pass.
- Android behavior has been checked where relevant.
- Reliability implications have been considered.
- Documentation and AI logs are updated where needed.
- The pull request has been reviewed.
- GitHub Actions passes.

---

Developed as a group project for Mobile Software Design and Development at SDU.
