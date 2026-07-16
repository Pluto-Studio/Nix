A Minecraft Paper server fork guided by profiling under specific workloads.

# Paperweight Patch Workflow

## Model

Durable state consists of the pinned Paper revision, tracked Nix patches, and outer-repository paperweight configuration. Generated worktrees are the editing interface:

```text
pinned Paper + tracked Nix patches
    -- ./gradlew applyAllPatches --> generated worktrees
    -- generated-worktree commits + layer rebuild --> tracked Nix patches
```

- Edit generated source, but commit in its owning generated worktree and rebuild the tracked patch before considering the change complete.
- `applyAllPatches` materializes or reconstructs generated projects; reapplying may discard uncommitted edits or edits not rebuilt into patches.
- The outer Git repository tracks patches and configuration, not gitignored generated source.
- Do not normally edit `.patch` files directly; let paperweight serialize generated-worktree commits.

## Ownership

Choose the owning layer before editing:

| Code | Generated location | Durable location |
| --- | --- | --- |
| Minecraft sources | `nix-server/src/minecraft/java` | `nix-server/minecraft-patches/features/*.patch` |
| Minecraft resources | `nix-server/src/minecraft/resources` | `nix-server/minecraft-patches/resources/*.patch` |
| Paper server additions | `paper-server` | `nix-server/paper-patches/features/*.patch` or `files/` |
| Paper API | `paper-api` | `nix-api/paper-patches/features/*.patch` or `files/` |
| Server build file | `nix-server/build.gradle.kts` | `nix-server/build.gradle.kts.patch` |
| API build file | `nix-api/build.gradle.kts` | `nix-api/build.gradle.kts.patch` |

## Tasks

This repository uses paperweight 2.x tasks, not legacy generic `applyPatches` or `rebuildPatches` tasks.

- Materialize everything: `./gradlew applyAllPatches`
- Rebuild with the narrowest matching task; no single `rebuildAllPatches` task exists:

| Layer | Command |
| --- | --- |
| Minecraft features | `./gradlew :nix-server:rebuildMinecraftFeaturePatches` |
| All Minecraft patches | `./gradlew :nix-server:rebuildMinecraftPatches` |
| Minecraft resources | `./gradlew :nix-server:rebuildMinecraftResourcePatches` |
| Paper server features | `./gradlew :nix-server:rebuildPaperServerFeaturePatches` |
| All Paper server patches | `./gradlew :nix-server:rebuildPaperServerPatches` |
| All Minecraft and server patches | `./gradlew :nix-server:rebuildAllServerPatches` |
| API features | `./gradlew rebuildPaperApiFeaturePatches` |
| All API patches | `./gradlew rebuildPaperApiPatches` |
| Root Paper/API and single-file patches | `./gradlew rebuildPaperPatches` |

Minecraft source changes normally use feature patches and `:nix-server:rebuildMinecraftFeaturePatches`. If unsure, run `./gradlew tasks --all`; do not rely on older Paper documentation.

## Required Flow

1. Identify the owning layer.
2. If its generated worktree is absent or stale, run `./gradlew applyAllPatches` at the outer root.
3. Search and edit the generated location.
4. In the owning generated worktree, inspect `git status` and the relevant `git log`; outer-root status cannot show changes in generated worktrees.
5. Make the smallest change and add Nix annotations.
6. Record it in the correct commit in that worktree, then run the matching rebuild task at the outer root.
7. Verify the expected tracked patch contains the intended diff and commit message. Modified generated source alone is not completion.

## Patch Commits

Paperweight reconstructs each generated worktree and its Git history from upstream and tracked patches. Each feature patch is represented by a commit; rebuild tasks write those commits back to `.patch` files.

- Existing patch: locate its commit and amend or fix it up; do not add a separate correction patch.
- New feature patch: create one logical commit at the correct point in that worktree's history. Do not split one change by file or combine unrelated changes.
- Use a concise imperative subject; it becomes the patch subject and filename, while the body becomes the patch description.
- The body must cover what changed, how it works, expected performance impact, and behavior, memory, or maintenance trade-offs. Explicitly state when none are meaningful.
- In `nix-server/src/minecraft/java`, `base` and `file` tags mark history boundaries. Custom source changes go after them and export to `nix-server/minecraft-patches/features`; do not use per-file `sources` patches.

Outer-repository commits follow normal Git practices; these generated-worktree commit rules do not apply to unrelated outer configuration or documentation.

# Source Search

- Root Grep/Glob may skip generated, gitignored, or nested worktrees; no root result does not prove source is absent.
- Search explicit paths: Minecraft sources in `nix-server/src/minecraft/java`, resources in `nix-server/src/minecraft/resources`, Paper server code in `paper-server`, and API code in `paper-api`. An `include: "*.java"` filter does not bypass ignored worktrees.
- If a generated path is absent, first consider that patches have not been applied.
- For call-site or behavior analysis, search both Minecraft and relevant Paper trees. Patches contain only the Nix delta, not complete source or all callers.

# Coding Conventions

## Patch Surface

- Keep Nix diffs minimal; do not reformat, reorder, rename, or clean unrelated upstream code.
- Preserve surrounding upstream style unless the change requires otherwise.
- Do not add or remove imports in patched upstream files. Retain imports made unused by Nix changes and use fully qualified names for new classes.

## Nix Annotations

Mark every Nix modification to upstream source. For one line:

```java
code; // Nix - <patch description>
```

For a contiguous multi-line change:

```java
// Nix start - <patch description>
changed code
// Nix end - <patch description>
```

- Use a concise description, normally matching the owning patch subject.
- Mark only Nix-owned lines; keep markers tight around replacements, not untouched methods or classes.
- Use one block for one contiguous operation; split blocks where unchanged upstream code intervenes.
