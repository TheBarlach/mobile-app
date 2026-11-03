# Mobile Expense Sharing App

An Android mobile application developed for the **Mobile Software Design and Development** course at the University of Southern Denmark (SDU).

The application helps groups of people manage shared expenses, split costs, track balances, and see who owes money to whom.

The concept is similar to WeShare and MobilePay Groups.

## Technology Stack

- Kotlin
- Jetpack Compose (Material 3)
- Navigation Compose
- Supabase (supabase-kt)
- Gradle with Kotlin DSL and a version catalog
- JUnit and Compose UI Test
- ktlint and Android Lint
- GitHub Actions

**Repository:** https://github.com/TheBarlach/mobile-app

## 1. Requirements

Install:

- [Android Studio](https://developer.android.com/studio) (latest stable). It includes the Android SDK and a bundled JDK.
- [Git](https://git-scm.com/)
- An Android device with USB/wireless debugging, or an emulator created in Android Studio's Device Manager

The project builds with JDK 17 or newer. Gradle is downloaded automatically by the wrapper (`./gradlew`); you do not need to install it.

## 2. Clone the Repository

```bash
git clone https://github.com/TheBarlach/mobile-app.git
cd mobile-app
```

If you already have the repository:

```bash
git switch main
git pull origin main
```

## 3. Open and Configure the Project

1. Open the repository root folder in Android Studio and let Gradle sync.
   Android Studio creates `local.properties` with your SDK path.
2. Copy the Supabase lines from `local.properties.example` into `local.properties`.

`local.properties` is ignored by Git and must not be committed.

Command-line alternative (if the SDK is installed and `ANDROID_HOME` is set):

```bash
echo "sdk.dir=$ANDROID_HOME" > local.properties
grep '^supabase' local.properties.example >> local.properties
```

## 4. Run the Application

In Android Studio, select the `app` run configuration and a device, then press **Run**.

From the command line, with a device or emulator connected:

```bash
./gradlew installDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## 5. Project Structure

```text
mobile-app/
├── app/
│   ├── build.gradle.kts             App module build configuration
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/dk/sdu/expenseshare/
│       │   │   ├── MainActivity.kt  Single activity hosting Compose
│       │   │   ├── ui/
│       │   │   │   ├── navigation/  Routes and NavHost
│       │   │   │   ├── theme/       Material 3 theme
│       │   │   │   └── components/  Shared composables (text field, button)
│       │   │   ├── feature/         Feature screens and ViewModels (auth, home, explore)
│       │   │   ├── data/remote/     Supabase client, auth API and repository
│       │   │   ├── domain/          Shared models (Profile)
│       │   │   └── core/money/      Money splitting and formatting
│       │   └── res/                 Strings, icons and other resources
│       ├── test/                    JVM unit tests
│       └── androidTest/             Compose UI / instrumented tests
├── gradle/libs.versions.toml        Dependency versions
├── supabase/                        Supabase CLI config and migrations
├── docs/                            Project and AI documentation
├── .github/workflows/               CI pipeline
├── AGENTS.md                        Project development rules
└── CLAUDE.md                        Claude project instructions
```

See [AGENTS.md](AGENTS.md) for the full target architecture (`feature/auth`, `feature/groups`, `data/local`, `data/sync`, ...). Packages are added as features are implemented.

### Where Should New Code Go?

- New screens: `feature/<feature>/`
- Navigation routes: `ui/navigation/`
- Reusable composables: `ui/components/`
- Screen state and logic: a `ViewModel` in `feature/<feature>/`
- Backend communication: `data/remote/`
- Offline storage: `data/local/`
- Background retry and synchronization: `data/sync/`
- Shared models: `domain/`
- Pure calculations: `core/`

Do not put application logic directly inside composables.

## Supabase Database

The application uses Supabase for authentication and database access.

### Configuration

The app reads two values at build time (exposed as `BuildConfig` fields):

| `local.properties` key    | Environment variable fallback |
| ------------------------- | ----------------------------- |
| `supabase.url`            | `SUPABASE_URL`                |
| `supabase.publishableKey` | `SUPABASE_PUBLISHABLE_KEY`    |

These are public client-side values. See step 3 above.

### Security

- Never add a Supabase secret key to the mobile app.
- Never commit passwords, private tokens or credentials.
- All database access must be protected using Row Level Security (RLS).
- Users should only access groups and expenses they are authorized to view or modify.

### Supabase Client

The shared client is `SupabaseProvider.client` in
`app/src/main/java/dk/sdu/expenseshare/data/remote/SupabaseProvider.kt`.

Use it instead of creating new Supabase clients. It is created lazily and fails with a clear message if the configuration is missing.

### Authentication

Login, registration and the profile shown on the home screen use Supabase Auth through `AuthRepository` (`data/remote/`). Screens never call the Supabase client directly: `SupabaseAuthApi` wraps supabase-kt behind the `AuthApi` interface, so the business rules can be unit tested with a fake.

The profile row is created by the `handle_new_user` database trigger. Apply `supabase/migrations/20261102120000_fix_profiles_signup.sql` (for example with `supabase db push`) before testing registration; it stores the username, adds the `is_username_available` function and tightens the table grants.

### Groups, Expenses and Balances

`supabase/migrations/20261103120000_groups_expenses.sql` adds `groups`, `group_members`, `expenses`, `expense_shares`, `payments` and `reminders`. Apply it with `supabase db push` before using the Balances tab.

- Only facts are stored: who paid what and how it was split. Balances are calculated in the app by the pure functions in `domain/balances/Balances.kt`.
- Members can only read their own groups (RLS). App users cannot write to the tables directly; they call the functions `create_group`, `add_group_member`, `create_expense`, `record_payment` and `send_reminder`, which validate the input.
- Every write has an id generated on the phone. The functions ignore an id that already exists, so a retry can never create a duplicate.
- `supabase/sql-tests/groups_expenses_test.sql` checks the rules (access, validation, idempotent retries) against a local database. Never run it against production.

In the app, `BalancesRepository` (`data/balances/`) combines three parts:

| Part | Where | What it does |
| ---- | ----- | ------------ |
| `SupabaseBalancesApi` | `data/remote/` | Fetches groups and calls the write functions |
| `BalancesCache` | `data/local/` | Saves the last fetched groups as JSON, so balances are shown offline |
| `Outbox` | `data/sync/` | Saves unsent writes and sends them in the background with bounded exponential backoff; retries when connectivity returns |

Data is stored per user in the app's private files folder (`files/balances/<user id>/`).

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

### Commit and Push

```bash
git add .
git commit -m "feat: add group creation screen"
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
./gradlew ktlintCheck testDebugUnitTest lintDebug assembleDebug
```

| Command                               | Purpose                                         |
| ------------------------------------- | ----------------------------------------------- |
| `./gradlew ktlintCheck`               | Checks Kotlin code style                        |
| `./gradlew ktlintFormat`              | Fixes Kotlin code style automatically           |
| `./gradlew testDebugUnitTest`         | Runs JVM unit tests                             |
| `./gradlew lintDebug`                 | Runs Android Lint                               |
| `./gradlew assembleDebug`             | Builds the debug APK                            |
| `./gradlew connectedDebugAndroidTest` | Runs Compose UI tests on a device/emulator      |

Reports are written to `app/build/reports/`.

Existing tests cover expense splitting, rounding and money formatting, plus a Compose UI test for the home and explore screens.

## 10. Continuous Integration

GitHub Actions runs on pull requests targeting `main` and on pushes to `main`. It checks:

- Gradle wrapper integrity
- ktlint
- JVM unit tests
- Android Lint
- Debug APK and instrumented test APK build

All checks must pass before merging. Instrumented tests are compiled in CI but must be run locally on a device or emulator.

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

### SDK Location Not Found

Gradle cannot find the Android SDK. Open the project in Android Studio once, or create `local.properties` with `sdk.dir=/path/to/Android/Sdk`.

### Missing Supabase Configuration

If the app reports missing Supabase configuration, add `supabase.url` and `supabase.publishableKey` to `local.properties` (see `local.properties.example`) and rebuild.

### ktlint Failures

Run `./gradlew ktlintFormat`, review the changes, and commit them.

### Gradle Sync Problems

Use **File → Invalidate Caches** in Android Studio, or run `./gradlew --stop` and try again. Make sure Android Studio uses JDK 17 or newer (**Settings → Build Tools → Gradle → Gradle JDK**).

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
- The project builds and ktlint and Android Lint pass.
- Appropriate automated tests pass.
- Android behavior has been checked on a device or emulator.
- Reliability implications have been considered.
- Documentation and AI logs are updated where needed.
- The pull request has been reviewed.
- GitHub Actions passes.

---

Developed as a group project for Mobile Software Design and Development at SDU.
