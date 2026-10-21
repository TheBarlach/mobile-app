# AGENTS.md – Mobile Expense Sharing App

## 1. Project Overview

This project is an Android mobile application developed for the Mobile Software Design and Development course at the University of Southern Denmark (SDU).

The application helps groups of people manage shared expenses, track payments, and calculate who owes money to whom, similar to WeShare and MobilePay Groups.

**Technology stack:**

- React Native with Expo SDK 57
- TypeScript
- Expo Router for navigation
- Jest and React Native Testing Library for automated tests
- ESLint for code quality
- GitHub Actions for continuous integration
- npm for dependency management

**Main objective:** Build a reliable, user-friendly Android application that supports shared expenses, debt calculation, notifications, and offline availability.

## 2. Project Architecture

Follow a feature-based architecture as the application develops.

```text
src/
├── app/                  # Expo Router navigation and screens
├── components/           # Shared UI components
├── features/
│   ├── auth/             # User profiles and authentication
│   ├── groups/           # Group management
│   ├── expenses/         # Expense registration and splitting
│   ├── balances/         # Debt calculations and summaries
│   └── notifications/    # Notifications and reminders
├── hooks/                # Reusable React hooks
├── services/             # API, persistence and synchronization
├── types/                # Shared TypeScript definitions
├── utils/                # Helper functions
└── constants/            # Shared constants
```

This is the target architecture. Do not assume directories or features already exist.

### Architecture Rules

- Keep navigation and route definitions in `src/app/`.
- Separate business logic from UI components.
- Reusable components belong in `src/components/`.
- Feature-specific code belongs in `src/features/`.
- API requests and persistence belong in `src/services/`.
- Debt calculations must be implemented as independently testable functions.
- Use TypeScript types for all important data structures.
- Avoid unnecessary dependencies and duplicated logic.
- Do not introduce a new architectural pattern without explaining its benefits.

## 3. Coding Standards

### TypeScript

- Use TypeScript for all new application code.
- Prefer strict types and avoid `any`.
- Use interfaces or type aliases for data models.
- Use meaningful variable and function names.
- Keep functions focused on a single responsibility.
- Use `async/await` for asynchronous operations.
- Handle errors explicitly.

### React Native

- Prefer functional components and React hooks.
- Use existing components before creating new ones.
- Keep screens focused on presentation and user interaction.
- Avoid unnecessary re-renders.
- Use platform-compatible APIs.
- Maintain consistent spacing, typography, and navigation.

### Navigation

Use Expo Router.

- Screens and layouts belong in `src/app/`.
- Use Expo Router's supported navigation APIs.
- Do not introduce React Navigation as a separate navigation system without a clear reason.

## 4. Expense and Debt Calculation

Financial calculations must be predictable and testable.

- Store monetary amounts as integer values in the smallest currency unit, such as øre.
- Avoid floating-point calculations for money.
- Ensure that splitting an expense preserves the total amount.
- Handle uneven splits deterministically.
- Validate negative amounts and invalid participant counts.
- Write unit tests for calculations and edge cases.

For example, splitting 100.00 DKK between three people should produce:

33.34 DKK + 33.33 DKK + 33.33 DKK.

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

These tests should be prioritized because the implementation report must demonstrate testing of at least one Reliability QA.

## 6. Testing and Code Quality

All new business logic should include appropriate unit tests.

Use Jest and React Native Testing Library.

Before submitting a pull request, run:

```bash
npm run lint
npm run check
npm run test:unit
```

Or run the combined command:

```bash
npm run precommit
```

Tests must verify actual behavior rather than simply checking implementation details.

Do not remove failing tests or disable ESLint rules merely to make CI pass.

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

## 9. Expo and Dependencies

Expo and React Native APIs can change between SDK releases.

Before introducing or changing an Expo-related dependency:

1. Check the current Expo SDK version in `package.json`.
2. Consult the matching Expo documentation.
3. Prefer `npx expo install` for SDK-compatible dependency versions.
4. Verify that the package supports the project's Expo and React Native versions.
5. Run relevant checks after installation.

Useful commands:

```bash
npx expo start
npx expo start --tunnel
npx expo install --check
npm run precommit
```

Do not run `npm audit fix --force` or introduce major dependency upgrades without checking compatibility.

Keep secrets, tokens, signing keys, and local environment files out of Git.

## 10. Android Builds

Android is the required target platform.

- Verify that new native dependencies work on Android.
- Prefer Expo configuration and config plugins for native settings.
- Do not assume that a successful TypeScript check guarantees a successful Android build.
- Test significant changes on an Android device or emulator.
- Use Expo development builds or EAS Build when native functionality requires them.

Do not commit generated native build directories unless the team explicitly decides to maintain native projects.

## 11. Definition of Done

A task is complete when:

- [ ] The implemented functionality meets the relevant requirement.
- [ ] TypeScript checks pass.
- [ ] ESLint passes.
- [ ] Appropriate automated tests pass.
- [ ] Relevant Android functionality has been verified.
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
