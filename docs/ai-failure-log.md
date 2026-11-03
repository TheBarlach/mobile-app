# AI Failure Log

This document records incorrect, incomplete, or unsuitable AI-generated suggestions and how the team identified and addressed them.

## Failure 1 – Incomplete React Native to Kotlin Migration

**Date:** 2 November 2026

**AI-generated output:** The first AI migration (AI-001) was reported as complete, but it only migrated the code on `main`.

**Problem:** The authentication feature on `feature/auth-profiles` was never migrated. The commit also contained an accidental git worktree reference (`.claude/worktrees/...`) and an Expo ESLint cache, `docs/ai-dossier.md` lost entries AI-001 and AI-002, and one attempt committed `node_modules`, which made the push fail.

**How it was detected:** The push to GitHub failed. In a follow-up session, the AI compared the branches and found that the Expo ESLint cache listed auth files that did not exist in the Kotlin project.

**Correction:** The auth feature was ported to Kotlin with tests (AI-004), the stray files were removed and git-ignored, and the dossier entries were restored.

**Lesson learned:** A migration must be checked against every active branch and against the diff of the commit, not only against whether the result builds.

## Future Failure Template

### Failure X – Title

**Date:**

**AI-generated suggestion:**

**Problem:**

**How it was detected:**

**Correction:**

**Lesson learned:**
