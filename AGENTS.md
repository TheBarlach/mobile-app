# AGENTS.md – Mobile Expense Sharing App

## 1. Project Overview

This project is an Android mobile application developed for the Mobile Software Design and Development course at the University of Southern Denmark (SDU).

The application helps groups of people manage shared expenses, track payments, and calculate who owes money to whom, similar to WeShare and MobilePay Groups.

**Technology stack:**

- Kotlin
- Jetpack Compose with Material 3
- Navigation Compose (type-safe routes)
- Gradle (Kotlin DSL) with a version catalog (`gradle/libs.versions.toml`)
- Supabase via supabase-kt
- JUnit for unit tests, Compose UI Test for instrumented tests
- ktlint and Android Lint for code quality
- GitHub Actions for continuous integration

**Main objective:** Build a reliable, user-friendly Android application that supports shared expenses, debt calculation, notifications, and offline availability.

## 2. Project Architecture

Follow a feature-based architecture as the application develops. All code lives in the `app` module under the package `dk.sdu.expenseshare`.

```text
app/src/main/java/dk/sdu/expenseshare/
├── MainActivity.kt       # Single activity hosting Compose
├── ui/
│   ├── navigation/       # Routes and NavHost
│   ├── theme/            # Material 3 theme
│   └── components/       # Shared composables
├── feature/
│   ├── auth/             # User profiles and authentication
│   ├── groups/           # Group management
│   ├── expenses/         # Expense registration and splitting
│   ├── balances/         # Debt calculations and summaries
│   └── notifications/    # Notifications and reminders
├── data/
│   ├── remote/           # Supabase / network access
│   ├── local/            # Offline persistence
│   └── sync/             # Background retry and synchronization
├── domain/               # Shared models and business rules
└── core/                 # Pure helpers (e.g. core/money)

app/src/test/             # JVM unit tests
app/src/androidTest/      # Instrumented / Compose UI tests
```

This is the target architecture. Do not assume packages or features already exist.

### Architecture Rules

- Keep navigation and route definitions in `ui/navigation/`.
- Separate business logic from composables. Screens get state from a `ViewModel` and send events back to it.
- Reusable composables belong in `ui/components/`.
- Feature-specific code belongs in `feature/<feature>/`.
- Network requests and persistence belong in `data/`.
- Debt calculations must be implemented as pure, independently testable functions.
- Use Kotlin data classes for important data structures.
- Avoid unnecessary dependencies and duplicated logic.
- Do not introduce a new architectural pattern or library (e.g. Hilt, Room) without explaining its benefits.

## 3. Coding Standards

### Kotlin

- Use Kotlin for all application code.
- Prefer immutable data (`val`, read-only collections, data classes).
- Avoid `!!`; model absence with nullable types and handle it.
- Use meaningful variable and function names.
- Keep functions focused on a single responsibility.
- Use coroutines and `suspend` functions for asynchronous work; expose state with `StateFlow`.
- Handle errors explicitly. Use `require`/`check` for invalid input.
- Code style is enforced by ktlint (`android_studio` style, see `.editorconfig`).

### Jetpack Compose

- Composables should be stateless where possible; hoist state to the caller or a `ViewModel`.
- Accept a `modifier: Modifier = Modifier` parameter on reusable composables.
- Use `MaterialTheme` colors and typography instead of hard-coded values.
- Put user-visible text in `res/values/strings.xml`.
- Add `@Preview` functions for new screens and components.
- Avoid unnecessary recompositions (use `remember`, stable parameters).

### Navigation

Use Navigation Compose with `@Serializable` route objects/classes.

- Routes and the `NavHost` belong in `ui/navigation/`.
- Do not introduce a second navigation library without a clear reason.

## 4. Expense and Debt Calculation

Financial calculations must be predictable and testable.

- Store monetary amounts as `Long` values in the smallest currency unit, such as øre.
- Never use `Float` or `Double` for money.
- Ensure that splitting an expense preserves the total amount.
- Handle uneven splits deterministically.
- Validate negative amounts and invalid participant counts.
- Write unit tests for calculations and edge cases.

For example, splitting 100.00 DKK between three people should produce:

33.34 DKK + 33.33 DKK + 33.33 DKK.

See `core/money/SplitExpense.kt`.

## 5. Reliability Requirements

Reliability is a mandatory quality attribute of this project.

### Fault Tolerance

- Temporary network failures must not crash the application.
- Failed requests caused by temporary network unavailability must be retried in the background.
- Failed requests must not prevent the user from continuing to use the application.
- Use bounded retry strategies and appropriate backoff.
- Avoid duplicate writes when retrying operations.

### Offline Availability

- Previously retrieved data must remain accessible offline.
- Clearly distinguish cached data from newly synchronized data where relevant.
- Do not assume that the device always has an internet connection.

### User Feedback

Requests must have identifiable states:

- Pending
- Successful
- Failed

The application must communicate these states clearly.

Users must be informed when a previously failed request eventually succeeds in the background.

### Testing Reliability

Write tests for at least:

- Temporary network failures
- Background retry behavior
- Offline access to cached information
- Request status transitions
- Successful synchronization after connectivity returns

Use `kotlinx-coroutines-test` (`runTest`, virtual time) for retry and backoff logic.

These tests should be prioritized because the implementation report must demonstrate testing of at least one Reliability QA.

## 6. Testing and Code Quality

All new business logic should include appropriate unit tests.

- JVM unit tests: `app/src/test/` (JUnit 4).
- Compose UI / instrumented tests: `app/src/androidTest/` (require a device or emulator).

Before submitting a pull request, run:

```bash
./gradlew ktlintCheck testDebugUnitTest lintDebug assembleDebug
```

Use `./gradlew ktlintFormat` to fix formatting automatically.

Tests must verify actual behavior rather than simply checking implementation details.

Do not remove failing tests, add lint baselines, or suppress lint/ktlint rules merely to make CI pass.

## 7. Git and Collaboration

The repository uses `main` as its stable branch.

### Branch Naming

Use descriptive branches:

```text
feature/group-management
feature/expense-creation
feature/offline-storage
fix/expense-calculation
chore/update-dependencies
docs/architecture
```

### Pull Requests

- Develop changes on a separate branch.
- Use pull requests to merge changes into `main`.
- Keep pull requests focused and reasonably small.
- Explain what changed and how it was tested.
- Resolve review comments before merging.
- Ensure required CI checks pass.

### AI-Generated Code

AI-generated code must:

1. Be submitted through a pull request.
2. Include appropriate tests.
3. Be reviewed by a teammate who did not generate it.
4. Pass the GitHub Actions CI pipeline.

Administrator bypass must not be used to avoid these academic requirements.

## 8. AI Usage and Documentation

Generative AI is permitted, but its use must be disclosed according to the course requirements.

Document significant AI interactions in `docs/ai-dossier.md`.

Each entry should include:

- Use case
- AI tool and mode
- Task or prompt
- Chat history link, where possible
- How the output was verified
- Evaluation and final decision

Maintain an AI failure log documenting at least three cases where AI output was incorrect, incomplete, or did not meet project requirements.

Each failure entry should explain what went wrong, how the team discovered the issue, and what was done to correct it.

The team must also investigate alternatives independently and justify important technical decisions.

AI output must never be accepted solely because it compiles or appears to work.

## 9. Gradle and Dependencies

All dependency and plugin versions are declared in `gradle/libs.versions.toml`.

Before introducing or changing a dependency:

1. Check that it is actively maintained and supports the project's Kotlin, AGP and `minSdk`/`compileSdk`.
2. Prefer AndroidX and Jetpack libraries; use BOMs (Compose, Supabase) to keep related artifacts aligned.
3. Add it to the version catalog rather than hard-coding versions in build files.
4. Avoid pre-release (alpha/beta/RC) versions unless the team agrees.
5. Run the full check command after the change.

Always use the Gradle wrapper (`./gradlew`), never a locally installed Gradle.

Keep secrets, tokens, signing keys, and `local.properties` out of Git. Supabase configuration is read from `local.properties` (see `local.properties.example`); only the publishable key may be used in the app.

## 10. Android Builds

Android is the required target platform.

- `minSdk` is 26; `compileSdk`/`targetSdk` are set in `app/build.gradle.kts`.
- Do not assume that a successful compile guarantees correct behavior on a device.
- Test significant changes on an Android device or emulator.
- Declare new permissions in `AndroidManifest.xml` and request runtime permissions where required.
- Never commit release signing keys.

## 11. Definition of Done

A task is complete when:

- [ ] The implemented functionality meets the relevant requirement.
- [ ] The project compiles (`./gradlew assembleDebug`).
- [ ] ktlint and Android Lint pass.
- [ ] Appropriate automated tests pass.
- [ ] Relevant Android functionality has been verified on a device or emulator.
- [ ] Reliability implications have been considered.
- [ ] No credentials or secrets have been committed.
- [ ] Relevant documentation has been updated.
- [ ] Significant AI usage has been recorded.
- [ ] The pull request has been reviewed where required.
- [ ] GitHub Actions CI passes.

For AI-generated code, independent teammate review is mandatory.

## 12. Engineering Responsibility

All team members must understand the code they contribute and approve.

The team should be capable of explaining and modifying implemented functionality during project reviews and the final presentation.

Prioritize understandable, maintainable, and verified solutions over unnecessarily complex implementations.
