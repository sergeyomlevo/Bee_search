---
name: "android-compose-accessibility"
description: "Make Compose interfaces accessible with semantics, announcements, contrast, focus order, and adaptive touch targets."
metadata:
  version: "0.1.0"
  category: "ui"
  tags: ["android", "compose", "accessibility", "a11y"]
  triggers:
    include: ["compose accessibility review", "android semantics issue compose", "focus order in compose", "screen reader compose ui", "large font compose problem"]
    exclude: ["dependency conflict only", "room schema only", "gradle plugin migration only"]
  owners: ["@android-agent-skills/maintainers"]
---
# Android Compose Accessibility

## When To Use
- Use this skill when the request is about: compose accessibility review, android semantics issue compose, focus order in compose.
- Primary outcome: Make Compose interfaces accessible with semantics, announcements, contrast, focus order, and adaptive touch targets.
- Handoff skills when the scope expands:
- `android-ui-states-validation`
- `android-testing-ui`
- These are upstream capability labels and may not be installed as local Bee
  Search skills. Use them only when the current environment provides them.

## Workflow
1. Identify whether the target surface is Compose, View system, or a mixed interoperability screen.
2. Select the lowest-friction UI pattern that satisfies responsiveness, accessibility, and performance needs.
3. Build the UI around stable state, explicit side effects, and reusable design tokens.
4. Exercise edge cases such as long text, font scaling, RTL, and narrow devices
   in the affected Bee Search UI and its relevant tests.
5. Validate with unit, UI, and screenshot-friendly checks before handing off.

## Guardrails
- Optimize for stable state and predictable rendering before adding animation or abstraction.
- Respect accessibility semantics, contrast, focus order, and touch target guidance by default.
- Do not mix Compose and View system ownership without an explicit interoperability boundary.
- Prefer measured performance work over premature micro-optimizations.

## Anti-Patterns
- Embedding navigation or business logic directly in leaf UI components.
- Using fixed dimensions that break on localization or dynamic text.
- Ignoring semantics and announcing only visual changes.
- Porting XML patterns directly into Compose without adapting the mental model.

## Examples

The upstream skill examples referenced fixture and benchmark paths that are not
part of Bee Search. For this repository, select the relevant Gradle, UI, and
physical-device checks through the root `AGENTS.md` and the
`android-development` skill. Do not cite an upstream fixture run as Bee Search
verification.

## Done Checklist
- The implementation path is explicit, minimal, and tied to the right Android surface.
- Relevant Bee Search checks have been exercised or any unverified behavior is
  reported explicitly.
- Handoffs to adjacent skills are documented when the request crosses boundaries.
- Official references cover the chosen pattern and the main migration or troubleshooting path.

## Official References
- [https://developer.android.com/develop/ui/compose/accessibility](https://developer.android.com/develop/ui/compose/accessibility)
- [https://developer.android.com/guide/topics/ui/accessibility/apps](https://developer.android.com/guide/topics/ui/accessibility/apps)
- [https://developer.android.com/guide/topics/ui/accessibility/testing](https://developer.android.com/guide/topics/ui/accessibility/testing)
- [https://developer.android.com/develop/ui/compose/layouts/adaptive](https://developer.android.com/develop/ui/compose/layouts/adaptive)
