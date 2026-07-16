A Minecraft Paper server fork driven by real profiling data under specific workload scenarios.

# Paperweight Patch Workflow

## Mental Model

The durable project state is the pinned Paper revision, the tracked Nix patch files, and the paperweight configuration in the outer repository. Paper and Minecraft source trees are generated editing workspaces:

```text
pinned Paper upstream + tracked Nix patches
    -- ./gradlew applyAllPatches -->
generated editable worktrees
    -- worktree commits + layer-specific rebuild task -->
updated tracked Nix patches
```

- `applyAllPatches` materializes or reconstructs the generated projects and Minecraft worktrees from the pinned upstream and tracked patches.
- Generated source is the normal editing interface, but it is not the durable output. An edit is not complete until it is represented by the correct worktree commit and rebuilt into a tracked patch.
- Reapplying patches can replace generated worktrees. Uncommitted edits or edits that were not rebuilt into patches may be lost.
- The outer Git repository tracks patches and configuration. Do not try to stage gitignored generated source with the outer repository's Git index.
- Do not manually edit `.patch` files during normal development. Edit the generated worktree, commit there where required, and let paperweight serialize the result.

## Patch Ownership

Identify the owning layer before making a change:

| Code being changed | Generated editing location | Durable patch location |
| --- | --- | --- |
| Minecraft Java | `nix-server/src/minecraft/java` | `nix-server/minecraft-patches/features/*.patch` |
| Minecraft resources | `nix-server/src/minecraft/resources` | `nix-server/minecraft-patches/resources/*.patch` |
| Paper-added server code | `paper-server` | `nix-server/paper-patches/features/*.patch` or `files/` |
| Paper API code | `paper-api` | `nix-api/paper-patches/features/*.patch` or `files/` |
| Generated server build file | `nix-server/build.gradle.kts` | `nix-server/build.gradle.kts.patch` |
| Generated API build file | `nix-api/build.gradle.kts` | `nix-api/build.gradle.kts.patch` |

Do not put a Minecraft change in `paper-patches`, or a Paper-added source change in `minecraft-patches`, merely because both are visible through the generated server project.

## Paperweight Tasks

This repository uses paperweight 2.x task names. Legacy instructions using the generic `applyPatches` or `rebuildPatches` tasks do not apply here.

- Materialize all generated projects and source worktrees with `./gradlew applyAllPatches`.
- There is no single `rebuildAllPatches` task. Use the narrowest rebuild task matching the edited layer:

| Changed layer | Rebuild command |
| --- | --- |
| Nix Minecraft feature patches | `./gradlew :nix-server:rebuildMinecraftFeaturePatches` |
| All Nix Minecraft patches | `./gradlew :nix-server:rebuildMinecraftPatches` |
| Minecraft resource patches | `./gradlew :nix-server:rebuildMinecraftResourcePatches` |
| Paper-added server feature patches | `./gradlew :nix-server:rebuildPaperServerFeaturePatches` |
| All Paper-added server patches | `./gradlew :nix-server:rebuildPaperServerPatches` |
| All Minecraft and server patches | `./gradlew :nix-server:rebuildAllServerPatches` |
| API feature patches | `./gradlew rebuildPaperApiFeaturePatches` |
| All API patches | `./gradlew rebuildPaperApiPatches` |
| All root Paper/API and single-file patches | `./gradlew rebuildPaperPatches` |

Custom Minecraft Java optimizations in this project use feature patches, so they normally end with `./gradlew :nix-server:rebuildMinecraftFeaturePatches`.

When task names are in doubt, inspect this checkout with `./gradlew tasks --all`; do not copy task names from older Paper documentation.

## Required Change Flow

1. Determine whether the target belongs to Minecraft, Paper server, Paper API, or a generated build file.
2. If the generated worktree is absent or stale, run `./gradlew applyAllPatches` from the outer repository root.
3. Search and edit the generated location for that layer.
4. From the owning generated Git worktree, inspect `git status`, `git log`, and relevant history. Root `git status` alone cannot show Minecraft worktree changes.
5. Make the smallest source change and add the required Nix patch annotations.
6. For a Minecraft feature patch, commit in the Minecraft worktree. One logical feature commit corresponds to one generated patch.
7. If revising an existing feature patch, amend or fix up its existing commit rather than adding a second patch for the same change.
8. Run the layer-specific rebuild task from the outer repository root.
9. Verify that the expected tracked patch changed and contains the intended diff and commit message. Do not report completion based only on modified generated Java files.

# Source Search Rules

- Root-level Grep and Glob searches can skip generated, gitignored, or nested worktrees. A root search with no result does not prove that Minecraft code does not exist.
- Search Minecraft Java by explicitly setting the search path to `nix-server/src/minecraft/java`. Using only an `include: "*.java"` filter does not bypass the ignored worktree.
- Search Minecraft resources by explicitly setting the search path to `nix-server/src/minecraft/resources`.
- Search Paper-added implementation and API code explicitly under `paper-server` and `paper-api` respectively.
- If `nix-server/src/minecraft/java`, `paper-server`, or `paper-api` is absent, first consider that generated worktrees have not been materialized; do not conclude that the source is absent from the project.
- For call-site or behavior analysis, search both the generated Minecraft tree and the relevant Paper tree. Patch files show Nix's delta, not the complete current source or all call sites.

# Minecraft Feature Commit Stack

`nix-server/src/minecraft/java` is a paperweight-managed Git history, not an independently maintained product repository. Its history is constructed approximately as:

```text
base/import commits
-> aggregated file-patch commit
-> one commit per feature patch, in patch order
```

- The `base` and `file` tags mark paperweight history boundaries.
- Nix Minecraft Java changes belong in feature commits after those boundaries; this project does not use per-file `sources` patches for custom Java changes.
- Each feature commit is exported to a numbered patch under `nix-server/minecraft-patches/features`.
- The commit subject determines the patch subject/name, and the commit body is embedded as the patch description.
- To revise an existing patch, locate its commit in this generated history and amend/fix up that commit before rebuilding.

# Coding Conventions

## Patch Surface

- Keep every Nix diff as small as possible. Do not reformat, reorder, rename, or clean up unrelated upstream code while implementing a patch.
- Preserve the surrounding upstream style unless the changed code requires otherwise.
- Do not add or remove imports in patched upstream files. Keep imports even if a Nix change makes them unused; reference newly needed classes by fully qualified name instead.

## Nix Annotations

Every Nix modification to upstream source must be identifiable without consulting the patch file.

Use a suffix for a single changed line:

```java
code; // Nix - <patch description>
```

Wrap a contiguous multi-line change with:

```java
// Nix start - <patch description>
changed code
// Nix end
```

- Use a concise description that normally matches the feature patch subject.
- Annotate only the lines owned by Nix. Do not include large untouched methods or surrounding upstream code inside a Nix block.
- Keep nearby changes in one annotation block when they form a single contiguous operation. Use separate blocks when unchanged upstream code lies between them.
- When replacing upstream code, place the markers tightly around the replacement rather than around the whole enclosing method or class.

## Feature Patch Commits

These rules apply to paperweight-managed generated worktrees, especially `nix-server/src/minecraft/java`:

- One logical feature commit corresponds to one feature patch. Do not split one optimization into multiple commits merely by file, and do not combine unrelated optimizations in one commit.
- For a new feature patch, commit the completed source change in the owning generated worktree before rebuilding patches.
- For an existing feature patch, locate its current commit and amend or fix up that commit. Do not append a second feature commit that only corrects the first one.
- Use a concise imperative commit subject. Paperweight uses the subject as the patch subject and derives the patch filename from it.
- The commit body must state what changed, how it works, the expected performance benefit, and any behavior, memory, or maintenance trade-offs. State explicitly when there is no meaningful trade-off.
- Inspect the generated commit and rebuilt `.patch` together; they must describe the same logical change.

Commits in the outer repository follow normal project Git practices. The one-commit-per-patch rule applies to generated feature-patch worktrees, not to unrelated outer-repository documentation or configuration changes.
