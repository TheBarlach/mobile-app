# AI Dossier

## Purpose

This document records significant uses of generative AI during the development of the Mobile Expense Sharing App, as required by the Mobile Software Design and Development course at SDU.

Each entry documents the task, AI tool, verification process, evaluation, and final decision.

## AI Usage Log

### AI-001 – Repository Setup and CI Configuration

**Date:** 21 October 2026

**Use case:** Development environment, GitHub repository setup, and continuous integration.

**Tool:** ChatGPT (GPT-6), conversational assistance.

**Task:** Configure the GitHub repository, branch protection, Expo project, TypeScript, ESLint, Jest, and GitHub Actions.

**Chat history:** Add conversation link if available.

**Verification:** The application was started using Expo, the TypeScript and ESLint checks passed locally, four Jest tests passed, and the GitHub Actions pipeline completed successfully.

**Evaluation:** The assistance provided a working development environment, but some initial suggestions required corrections, particularly around CI and TypeScript.

**Decision:** Retained the working setup after testing and corrections.

---

### AI-002 – AGENTS.md Project Instructions

**Date:** 21 October 2026

**Use case:** Development conventions, software architecture, code quality, and AI governance.

**Tool:** ChatGPT (GPT-6), conversational assistance.

**Task:** Draft project-specific instructions covering architecture, testing, reliability, collaboration, and the course AI policy.

**Chat history:** Add conversation link if available.

**Verification:** The team must review the generated instructions against the course description and actual project structure before the changes are merged.

**Evaluation:** Drafted instructions adapted to the course requirements. Team review is pending.

**Decision:** Proposed for inclusion in `AGENTS.md`, subject to teammate review.

---

### AI-003 – Migration from React Native/Expo to Kotlin and Jetpack Compose

**Date:** 2 November 2026

**Use case:** Technology stack change, project scaffolding, build configuration, and CI.

**Tool:** Claude Code (Claude Opus 5.5), agentic mode in a separate git worktree and branch.

**Task:** "Change the whole project to use Jetpack Compose and Kotlin." The AI removed the Expo/TypeScript project and created a native Gradle Android project: Compose UI with Navigation Compose, a Kotlin port of `splitExpense` with extended JUnit tests, a `formatDkk` helper, a supabase-kt client replacing `@supabase/supabase-js`, ktlint and Android Lint, a Gradle-based GitHub Actions workflow, and updated `README.md` and `AGENTS.md`.

**Chat history:** Add conversation link if available.

**Verification:** Dependency versions were looked up from Google Maven, Maven Central and the Gradle plugin portal rather than from memory, and pre-release versions were rejected. `./gradlew ktlintCheck testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest` passed locally (11 unit tests, no Android Lint issues). The first ktlint run failed on trailing commas in the AI-written build script; this was fixed with `ktlintFormat`. The app has not yet been run on a device or emulator, and the Compose UI test has only been compiled, not executed.

**Evaluation:** Pending teammate review. The team must confirm that changing the stack is acceptable for the course, run the app on an Android device, and execute `./gradlew connectedDebugAndroidTest`.

**Decision:** Proposed via pull request, subject to teammate review.

**Alternatives investigated independently:** To be completed by the team (e.g. staying on Expo, Kotlin Multiplatform, Hilt/Room adoption).

---

### AI-004 – Completing the Kotlin Migration: Authentication and Profiles

**Date:** 2 November 2026

**Use case:** Porting the remaining React Native feature (authentication and user profiles) to Kotlin and Jetpack Compose, and cleaning up the migration pull request.

**Tool:** Claude Code (Claude Opus 5.5), agentic mode.

**Task:** "Fix the last things so the whole code is migrated over to Kotlin and Jetpack Compose." The AI compared the migration branch with the other branches and found that the auth work on `feature/auth-profiles` (login, registration, session handling, profile loading, validation, tests and a Supabase migration) had never been migrated. It ported it to Kotlin: `domain/Profile`, pure validation in `feature/auth/AuthValidation.kt`, an `AuthApi` interface with a supabase-kt implementation, `AuthRepository` (normalisation, username check, hidden-duplicate detection, error mapping), `SessionViewModel`/`LoginViewModel`/`RegisterViewModel`, Compose login/registration screens, a signed-in/signed-out navigation gate and a profile card on the home screen. The SQL migration `20261102120000_fix_profiles_signup.sql` was copied unchanged. It also removed an accidentally committed worktree gitlink and Expo ESLint cache, and restored dossier entries AI-001 and AI-002 that the migration had dropped.

**Chat history:** Add conversation link if available.

**Verification:** The Jest tests from `validation.test.ts` and `auth.test.ts` were ported to JUnit (fake `AuthApi` instead of mocking the Supabase client) and extended with ViewModel tests for profile loading, retry, user switching and sign-out. `./gradlew ktlintCheck testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest` passed locally (38 unit tests). The supabase-kt API was checked against the 3.8.0 class files rather than from memory. The Compose UI tests have been compiled but not run, because no emulator was available, and sign-up/login have not been tried against the real Supabase project.

**Evaluation:** Pending teammate review. The team must run the app on a device, test registration and login against Supabase (with and without email confirmation), apply the SQL migration and run `./gradlew connectedDebugAndroidTest`.

**Decision:** Added to the migration pull request, subject to teammate review.

**Alternatives investigated independently:** To be completed by the team (e.g. Hilt instead of manual ViewModel factories).

## Entry Template

### AI-XXX – Title

**Date:**

**Use case:**

**Tool and mode:**

**Task / prompt:**

**Chat history:**

**Verification:**

**Evaluation:**

**Final decision:**

**Alternatives investigated independently:**
