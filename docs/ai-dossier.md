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

### AI-003 – Sign-up Profile Migration and Login Flow

**Date:** 2 November 2026

**Use case:** Authentication, user profiles and database migrations.

**Tool and mode:** Claude Code (Claude Opus 5.5), agentic mode in the repository.

**Task / prompt:** Fix the Supabase migration so creating an account stores the profile correctly in the database, and make login fetch the correct profile and log the user in.

**Chat history:** Add conversation link if available.

**Verification:** `npm run precommit` (lint, TypeScript, Jest) passes. Both migrations were applied to a throwaway Postgres 17 container with a stubbed `auth` schema to check that sign-up stores username and full name, that a duplicate username does not abort sign-up, that `updated_at` is updated, that existing users are backfilled and that `anon` can no longer truncate `profiles`. Not yet verified on an Android device or against the hosted Supabase project.

**Evaluation:** The original trigger only stored `full_name`, and `anon` had `TRUNCATE` on `profiles`, which Row Level Security does not cover. The fix is a new migration rather than an edit of the already-applied one.

**Final decision:** Pending teammate review and testing on Android.

**Alternatives investigated independently:** To be completed by the team, for example inserting the profile from the client after sign-up (fails with RLS when email confirmation is enabled, because there is no session yet).

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
