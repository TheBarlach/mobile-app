# AI Dossier

## Purpose

This document records significant uses of generative AI during the development of the Mobile Expense Sharing App, as required by the Mobile Software Design and Development course at SDU.

Each entry documents the task, AI tool, verification process, evaluation, and final decision.

## AI Usage Log

### AI-001 – Migration from React Native/Expo to Kotlin and Jetpack Compose

**Date:** 2 November 2026

**Use case:** Technology stack change, project scaffolding, build configuration, and CI.

**Tool:** Claude Code (Claude Opus 5.5), agentic mode in a separate git worktree and branch.

**Task:** "Change the whole project to use Jetpack Compose and Kotlin." The AI removed the Expo/TypeScript project and created a native Gradle Android project: Compose UI with Navigation Compose, a Kotlin port of `splitExpense` with extended JUnit tests, a `formatDkk` helper, a supabase-kt client replacing `@supabase/supabase-js`, ktlint and Android Lint, a Gradle-based GitHub Actions workflow, and updated `README.md` and `AGENTS.md`.

**Chat history:** Add conversation link if available.

**Verification:** Dependency versions were looked up from Google Maven, Maven Central and the Gradle plugin portal rather than from memory, and pre-release versions were rejected. `./gradlew ktlintCheck testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest` passed locally (11 unit tests, no Android Lint issues). The first ktlint run failed on trailing commas in the AI-written build script; this was fixed with `ktlintFormat`. The app has not yet been run on a device or emulator, and the Compose UI test has only been compiled, not executed.

**Evaluation:** Pending teammate review. The team must confirm that changing the stack is acceptable for the course, run the app on an Android device, and execute `./gradlew connectedDebugAndroidTest`.

**Decision:** Proposed via pull request, subject to teammate review.

**Alternatives investigated independently:** To be completed by the team (e.g. staying on Expo, Kotlin Multiplatform, Hilt/Room adoption).

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
