# Agent Rules & Behavioral Guidelines

## 1. Browser Automation Policy
- **DO NOT** automatically launch or control browsers (`browser_subagent` tool) to test or preview web pages.
- Only run non-interactive terminal validation (e.g. `npm run build`, `tsc -b`) to verify correctness.
- The user will test and inspect UI changes in their own browser window.

## 2. Design System Guidelines
- Primary orange accent: `#D97757`
- Primary black / charcoal: `#242424`
- Status colors:
  - AVAILABLE: `#4CAF7D` (badge: bg `#E8F5EE`, text `#2E7D32`)
  - PARTIAL: `#E8A020` (badge: bg `#FFF3E0`, text `#E65100`)
  - BOOKED: `#E05252` (badge: bg `#FFEBEE`, text `#C62828`)

## 3. Learning & Concept Verification Policy (Wise Teacher Mode)
- Guide the user step-by-step through technical implementation, explaining the architectural *Why, What, and How*.
- After completing each work item, quiz the user with core focal questions (using interactive multiple-choice or open questions) to verify deep conceptual mastery before proceeding to the next work item.

## 4. Testing Policy
- Every new feature or behavior change must ship with unit tests in the same change (backend: JUnit 5 + Mockito under `backend/src/test`, mirroring the main package; controllers via `@WebMvcTest`, services with mocked repositories, domain logic with plain tests).
- Cover the happy path plus each business-rule rejection and validation error the feature introduces.
- Before creating a pull request, run `./backend/mvnw -f backend/pom.xml test` and make sure it passes. Do not open a PR with failing or skipped-to-pass tests, and report the result in the PR description.
- Bug fixes need a regression test that fails without the fix.

<!-- gitnexus:start -->
# GitNexus — Code Intelligence

This project is indexed by GitNexus as **Alloca** (1838 symbols, 4410 relationships, 133 execution flows). Use the GitNexus MCP tools to understand code, assess impact, and navigate safely.

> If any GitNexus tool warns the index is stale, run `npx gitnexus analyze` in terminal first.

## Always Do

- **MUST run impact analysis before editing any symbol.** Before modifying a function, class, or method, run `gitnexus_impact({target: "symbolName", direction: "upstream"})` and report the blast radius (direct callers, affected processes, risk level) to the user.
- **MUST run `gitnexus_detect_changes()` before committing** to verify your changes only affect expected symbols and execution flows.
- **MUST warn the user** if impact analysis returns HIGH or CRITICAL risk before proceeding with edits.
- When exploring unfamiliar code, use `gitnexus_query({query: "concept"})` to find execution flows instead of grepping. It returns process-grouped results ranked by relevance.
- When you need full context on a specific symbol — callers, callees, which execution flows it participates in — use `gitnexus_context({name: "symbolName"})`.

## Never Do

- NEVER edit a function, class, or method without first running `gitnexus_impact` on it.
- NEVER ignore HIGH or CRITICAL risk warnings from impact analysis.
- NEVER rename symbols with find-and-replace — use `gitnexus_rename` which understands the call graph.
- NEVER commit changes without running `gitnexus_detect_changes()` to check affected scope.

## Resources

| Resource | Use for |
|----------|---------|
| `gitnexus://repo/Alloca/context` | Codebase overview, check index freshness |
| `gitnexus://repo/Alloca/clusters` | All functional areas |
| `gitnexus://repo/Alloca/processes` | All execution flows |
| `gitnexus://repo/Alloca/process/{name}` | Step-by-step execution trace |

## CLI

| Task | Read this skill file |
|------|---------------------|
| Understand architecture / "How does X work?" | `.claude/skills/gitnexus/gitnexus-exploring/SKILL.md` |
| Blast radius / "What breaks if I change X?" | `.claude/skills/gitnexus/gitnexus-impact-analysis/SKILL.md` |
| Trace bugs / "Why is X failing?" | `.claude/skills/gitnexus/gitnexus-debugging/SKILL.md` |
| Rename / extract / split / refactor | `.claude/skills/gitnexus/gitnexus-refactoring/SKILL.md` |
| Tools, resources, schema reference | `.claude/skills/gitnexus/gitnexus-guide/SKILL.md` |
| Index, status, clean, wiki CLI commands | `.claude/skills/gitnexus/gitnexus-cli/SKILL.md` |

<!-- gitnexus:end -->
