# AI Failure Log

This document records incorrect, incomplete, or unsuitable AI-generated suggestions and how the team identified and addressed them.

## Failure 1 – Missing TypeScript Types in GitHub Actions

**Date:** 21 October 2026

**AI-generated suggestion:** Configure GitHub Actions to run TypeScript checks directly after installing dependencies.

**Problem:** The initial CI pipeline failed because TypeScript could not resolve CSS imports in the Expo project.

**How it was detected:** GitHub Actions reported TS2307 and TS2882 errors for CSS imports.

**Investigation:** The files were verified to exist and were already tracked by Git. Local TypeScript checks passed.

**Correction:** The CI configuration was revised and the final workflow passed.

**Lesson learned:** Local success does not guarantee CI success. The application must be checked in a clean environment.

---

## Failure 2 – Incorrect Expo Type Generation Command

**Date:** 21 October 2026

**AI-generated suggestion:** Use `npx expo customize tsconfig.json` to generate Expo TypeScript definitions.

**Problem:** This command is intended for Expo configuration customization, not for generating the missing types.

**How it was detected:** The command's intended purpose was reviewed during CI configuration.

**Correction:** The unnecessary command was identified for removal.

**Lesson learned:** AI-generated commands must be checked against official tool documentation.

---

## Failure 3 – React Hook Lint Error

**Date:** 21 October 2026

**AI-related setup:** An Expo project template was recommended and used as the starting point.

**Problem:** The generated `use-color-scheme.web.ts` contained a synchronous state update inside an effect, which was flagged by ESLint.

**How it was detected:** `npm run precommit` failed with the `react-hooks/set-state-in-effect` rule.

**Correction:** The hook was simplified, and ESLint subsequently passed.

**Lesson learned:** Generated project templates must also pass the project's lint and testing requirements.

**Note:** This third entry should only be counted as an AI failure if the team can substantiate that AI output caused the issue. Otherwise, retain it as a tooling issue and document a different verified AI mistake.

---

## Future Failure Template

### Failure X – Title

**Date:**

**AI-generated suggestion:**

**Problem:**

**How it was detected:**

**Correction:**

**Lesson learned:**
