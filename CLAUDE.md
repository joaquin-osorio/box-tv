# CLAUDE.md

@AGENTS.md

## Language

- The user writes in Spanish. Everything that goes into the repo must be in English: code, identifiers, comments, KDoc, tests, commit messages, `/docs` (including `/docs/HUMAN.md`), and any user-facing text unless the task explicitly says otherwise.
- Chat replies to the user can be in Spanish.

## Workflow

- Don't run the full test suite after a change — only run tests relevant to what changed (scope to the affected Gradle module/package).
- Work autonomously. When something is unclear, use your own judgment, pick the most reasonable option, and state the assumption in your plan or summary. Only ask when it's truly essential: the ambiguity would change the outcome in a big way, the decision is hard to reverse, or it touches data loss or security.
- If the user's request seems non-optimal, say so — but if they insist, do it as asked. They're in charge.
- Keep changes focused. Don't touch unrelated files, refactor unrelated code, or "clean up" surrounding code unless it's necessary for the task.
- For significant architectural changes, module restructuring, or new patterns: explain the trade-offs in the plan and proceed. Only stop for approval if the change would be hard to undo.
- Follow the project's existing conventions and config. Don't override or bypass lint (ktlint/detekt)/format/test rules just to make something pass — if a rule is genuinely a problem, explain why and ask before changing it.
- Stop any running emulator instances or Gradle daemons started during a session before finishing, if they were started specifically for the task.

## Planning and commits

- When planning a task, break it into small, committable milestones and list them in the plan. Each milestone is one coherent change that leaves the app in a working state (builds, compiles, relevant tests pass).
- Commit each milestone as soon as it's done and verified, before starting the next one. Don't batch several milestones into one commit, and don't leave finished work uncommitted.
- Before each commit: run lint/static analysis, build, and the tests relevant to that milestone. If anything fails, fix it first. Never commit broken code and never skip hooks.
- Tests and `/docs` updates belong in the same commit as the change they cover.
- Stage with `git add .` so nothing is left unstaged. Because everything gets staged:
  - Secrets (API keys, tokens) always go in `local.properties` or a non-committed `*.properties` file, read via `BuildConfig` fields — never hardcoded in `build.gradle.kts` or source. Check `.gitignore` covers these before committing.
  - Review `git status` before each commit. If an untracked file shouldn't live in the repo (build output, `.gradle/`, local artifacts, APKs), add it to `.gitignore` instead of committing it.
  - If a secret ever appears in the staged diff, stop, move it out, and don't commit until it's out.
- If the plan changes mid-task (a milestone needs splitting, reordering, or dropping), update the plan and say so before continuing.
- Use [Conventional Commits](https://www.conventionalcommits.org/): `type(scope): description`.
  - Types: `feat`, `fix`, `refactor`, `perf`, `test`, `docs`, `style`, `build`, `ci`, `chore`.
  - Subject: imperative mood, lowercase, no trailing period, ≤ 72 characters.
  - Add a body when the *why* isn't obvious from the subject.
  - Breaking changes: `type(scope)!:` plus a `BREAKING CHANGE:` footer.
- Work directly on `main`. Push after each commit.
- Don't rewrite history (amend of pushed commits, rebase, reset, force-push) unless the user asks.

## Code

- No giant files. A file pushing ~10k lines is treated as a mistake 9 times out of 10 — flag it and propose splitting it up (especially easy to hit in Compose screens; split into smaller composables).
- Prefer the simplest solution that correctly solves the problem. No unnecessary abstractions, patterns, or architectural complexity.
- Never hardcode secrets, API keys, credentials, or tokens. Always read them from `BuildConfig` / `local.properties`.

## Android TV / Compose conventions

- UI is built with Jetpack Compose for TV (`androidx.tv.*`) — use its D-pad-aware components (`TvLazyRow`, `TvLazyColumn`, `Carousel`, focus-aware `Card`s) instead of the plain mobile Compose equivalents, since focus/scroll-into-view behavior for D-pad is the whole point of the toolkit.
- Every interactive composable needs a sane, testable focus order. Don't assume touch input anywhere (no click-only affordances without a focusable equivalent).
- Follow MVVM: `ViewModel`s hold UI state, screens are stateless composables driven by that state. Keep business logic out of composables.
- **Site adapters**: each streaming site is implemented behind a common interface (e.g. `StreamSource`, with something like `getCategories()`, `getItems(category)`, `getStreamUrl(item)`). The UI layer only ever talks to this interface, never to a specific site's parsing logic. This keeps breakage (when a site changes its HTML/API) isolated to one adapter.
- Prefer consuming a site's internal API (found via inspecting network traffic) over HTML scraping when possible — it's more stable. Fall back to scraping with Jsoup when there's no usable API.
- Video playback goes through Media3 (ExoPlayer). Handle stream-resolution failures explicitly (a source going down or changing its stream format shouldn't crash the app — surface a clear error state in the UI).
- Start with a single Gradle module. Only split into multiple modules (e.g. `:app`, `:core-ui`, `:adapters:<site>`) once the adapter count or build times actually justify it — don't set up multi-module structure preemptively for a personal project.
- Use the Gradle version catalog (`libs.versions.toml`) for dependency versions rather than hardcoding them inline.

## Documentation

- `/docs` is a persistent knowledge base for the coding agent, not human-facing documentation. It can be as technical, detailed, and granular as needed. Its structure evolves organically as the app grows — don't force it into a predefined layout.
- `/docs/HUMAN.md` is the one exception: written for the user as the human orchestrator. It's an incremental, high-level report of the entire application — what it does, how it works, architecture, major concepts, important flows, and significant technical decisions. It must always describe the _current_ state: update or remove outdated information rather than letting it accumulate historical detail.
- Before implementing a feature or making a significant change, check the relevant parts of `/docs` to understand existing architecture and conventions.
- Only non-obvious changes need a `/docs` entry or update (including `/docs/HUMAN.md` when relevant) — skip it for changes that are self-evident from the code.
- KDoc functions when there's non-obvious logic, a public/reusable API, important assumptions, side effects, or non-obvious params/return values. Don't add KDoc that just restates the code.
- Use inline comments where they'll meaningfully help review — not everywhere.

## Testing

- Write JVM unit tests for adapter logic (parsing, stream URL extraction, category mapping) — this is where sites break most often and where automated coverage pays off most.
- Mock network responses (e.g. with MockWebServer or fixture HTML files) rather than hitting real sites in tests, so tests don't depend on a site being reachable or unchanged.
- UI/focus behavior is hard to test automatically in a meaningful way — verify changes to navigation or focus manually on the emulator or a real device rather than forcing brittle UI tests for it.
- Tests must verify meaningful behavior, not implementation details. Don't write tests just to inflate coverage.
- When fixing a bug in an adapter, add a regression test whenever practical (e.g. a fixture that reproduces the broken HTML/response).

## Structure

- Single root `CLAUDE.md`. Don't create per-module `CLAUDE.md` files unless there's a concrete reason later (e.g. genuinely different conventions between the app module and adapter modules).
