# AGENTS.md

## Purpose

This file defines repository-wide instructions for coding agents working on this Android project.

Use it to decide:

* where a change belongs;
* which UI primitives and architectural boundaries must be preserved;
* how user-visible text must be authored;
* what to verify before reporting a task as complete.

Task-specific maintainer instructions take precedence over this file. For focused requests, make the
smallest coherent change that satisfies the request. For broad behavior changes or refactors, first
identify the nearest existing implementation pattern and preserve the project structure.

---

## Repository package layout

The application code is rooted at `com.resukisu.resukisu`.

* `data/` — data repositories and related data-layer implementation.
* `ui/` — user-interface code, including pages, state, and reusable Compose components.

Preserve these responsibilities. Do not move code into a convenient but incorrect package merely to
complete a change faster.

### Data layer

Repository implementations belong under `com.resukisu.resukisu.data` and its subpackages. Keep
persistence, data loading, mapping, and repository concerns in this layer. Do not embed UI
composition or presentation-only behavior in repositories.

---

## UI component conventions

### Canonical component location

All reusable UI components must be placed under:

```text
com.resukisu.resukisu.ui.component
```

Do not create parallel reusable-component packages inside individual pages, features, or view
models. Screen-specific composables may remain close to their screen when they are truly local, but
components intended for reuse must live in `ui.component`.

### Settings UI

Use the settings component system under:

```text
com.resukisu.resukisu.ui.component.settings
```

Do not hand-build settings rows, dividers, switch rows, page-navigation rows, or similar settings
primitives when an existing settings component can express the required behavior.

#### Layout groups

Use one of the standard settings layout groups:

* `SegmentedColumn` — the default for ordinary settings groups whose complete content is statically
  known at compile time and cannot change because of runtime data updates.
* `LazySegmentedColumn` — use when the group's content is driven by data that can change at runtime,
  such as an updated collection, reordered items, additions, removals, or otherwise dynamic settings
  entries.

Choose based on whether the group can change at runtime, not on personal layout preference. Do not
replace these group components with ad-hoc columns and manually drawn separators or rounded
containers.

#### Fine-grained settings widgets

For switch controls, page-navigation entries, and other individual settings rows, use the
corresponding `SettingsBaseWidget` wrapper rather than composing an imitation manually.

Examples include:

* `SettingsSwitchWidget` for settings controlled by a switch;
* `SettingsJumpPageWidget` for settings that open or navigate to another page;
* the appropriate existing `SettingsBaseWidget` wrapper for comparable settings interactions.

Reuse the closest existing wrapper and its API. Do not manually assemble a `Row`, text, click
handler, divider, shape, and trailing icon/switch just to reproduce a standard settings widget.

### Animated corner shapes

When a component needs a dynamic rounded-corner animation, use the implementation in:

```text
com.resukisu.resukisu.ui.component.settings.material3internal.AnimatedShape.kt
```

Do not introduce duplicate animated-shape implementations or manually interpolate equivalent corner
geometry when `AnimatedShape.kt` fits the need.

---

## Text and Android resources

### No hardcoded user-visible text

All user-visible text is forbidden from being hardcoded in Kotlin, Java, or Compose source. Use
Android string resources for labels, titles, descriptions, button text, accessibility text, errors,
and other displayed copy.

When changing user-visible wording:

* add or update the appropriate Android resource;
* preserve existing formatting placeholders and plural behavior;
* update every maintained locale required by the project;
* avoid embedding translated text in code, previews, or component defaults.

### Compose resource access

When a string is resolved from a Compose context, obtain it with `stringResource`, for example:

```kotlin
val title = stringResource(R.string.example_title)
```

Prefer `stringResource` whenever the value is needed in Compose. Use context-based resource access
only where Compose resource access is not available or not appropriate, such as non-Compose
data/platform code.

Do not pass hardcoded text into reusable UI components when a resource-backed value can be passed
instead.

---

## Architecture discipline

* Keep repositories and data access in `data/`.
* Keep presentation and Compose work in `ui/`.
* Put reusable composables in `ui.component`, and prefer the settings component system for settings
  surfaces.
* Extend the nearest existing pattern before creating a new abstraction.
* Avoid duplicating existing widgets, shapes, or resource-access patterns.

---

## Recommended workflow

For implementation tasks:

1. Identify the affected layer and the nearest comparable implementation.
2. Place new code in the canonical package for that responsibility.
3. For settings UI, select `SegmentedColumn` or `LazySegmentedColumn` based on runtime mutability,
   then use the appropriate `SettingsBaseWidget` wrapper.
4. Add or update Android resources before wiring user-visible text into UI.
5. Use `stringResource` for strings resolved in Compose.
6. Verify changes by running `./gradlew assembleRelease` from the repository root.
7. Report exactly what was changed and whether `./gradlew assembleRelease` completed successfully.

---

## Completion checklist

Before completing a UI or settings task, verify:

* Reusable components are under `com.resukisu.resukisu.ui.component`.
* Settings screens use `com.resukisu.resukisu.ui.component.settings` components.
* Static settings groups use `SegmentedColumn`; runtime-changing groups use `LazySegmentedColumn`.
* Standard settings rows use the relevant `SettingsBaseWidget` wrapper instead of a hand-built
  equivalent.
* Dynamic corner-shape animation uses `AnimatedShape.kt` when applicable.
* No user-visible string is hardcoded.
* Compose strings use `stringResource` whenever possible.
* Verify the project with `./gradlew assembleRelease` before reporting completion.
* Any build or test result is reported honestly; never claim verification passed when it was not
  run.
