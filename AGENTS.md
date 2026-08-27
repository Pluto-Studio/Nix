Follow this workflow in order. A change is complete only after its generated-worktree commit has been rebuilt into the tracked patch layer.

## 1. Understand the state model

The repository stores three pieces of durable state: the pinned Paper revision, tracked Nix patches, and the outer repository's paperweight configuration. Generated worktrees are where you edit the source:

```text
pinned Paper + tracked Nix patches
    -- ./gradlew applyAllPatches --> generated worktrees
    -- generated-worktree commits + layer rebuild --> tracked Nix patches
```

- Edit generated source in its owning worktree. Commit the change there, then rebuild the tracked patch before you call it complete.
- The outer Git repository tracks patches and configuration, not gitignored generated source.
- Do not normally edit `.patch` files directly. Let paperweight serialize commits from the generated worktree.
- `applyAllPatches` materializes or reconstructs generated projects. Reapplying may discard uncommitted edits or edits not rebuilt into patches.

## 2. Choose the owning layer

Identify the owning layer before editing:

| Code | Generated location | Durable location |
| --- | --- | --- |
| Minecraft sources | `nix-server/src/minecraft/java` | `nix-server/minecraft-patches/features/*.patch` |
| Minecraft resources | `nix-server/src/minecraft/resources` | `nix-server/minecraft-patches/resources/*.patch` |
| Paper server additions | `paper-server` | `nix-server/paper-patches/features/*.patch` or `files/` |
| Paper API | `paper-api` | `nix-api/paper-patches/features/*.patch` or `files/` |
| Server build file | `nix-server/build.gradle.kts` | `nix-server/build.gradle.kts.patch` |
| API build file | `nix-api/build.gradle.kts` | `nix-api/build.gradle.kts.patch` |

## 3. Materialize and inspect the generated worktree

If the owning generated worktree is absent or stale, run `./gradlew applyAllPatches` at the outer repository root.

Before editing, inspect `git status` and the relevant `git log` inside the owning generated worktree. The outer repository's status cannot show changes in generated worktrees.

### Search source

- Searches from the repository root may skip generated, gitignored, or nested worktrees. A missing result does not prove that the source is absent.
- Search these paths directly: Minecraft sources in `nix-server/src/minecraft/java`, resources in `nix-server/src/minecraft/resources`, Paper server code in `paper-server`, and API code in `paper-api`. An `include: "*.java"` filter does not bypass ignored worktrees.
- If a generated path is missing, check whether the patches have been applied before looking elsewhere.
- To analyze callers or behavior, search both the Minecraft and relevant Paper trees. Patches contain only the Nix delta, not the complete source or all callers.

## 4. Edit generated source

Make the smallest necessary change in the generated location and follow the conventions below.

### Keep the patch small

- Keep Nix diffs minimal. Do not reformat, reorder, rename, or clean up unrelated upstream code.
- Preserve the surrounding upstream style unless the change requires otherwise.
- Do not add or remove imports in patched upstream files. Leave imports unused by Nix changes in place. Use fully qualified names for new classes.

### Mark Nix changes

Mark every line Nix changes in upstream source. For a one-line change:

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
- Mark only Nix-owned lines. Keep markers tight around replacements, not untouched methods or classes.
- Use one block for each contiguous operation. Split blocks when unchanged upstream code falls between them.

### Place custom source correctly

- Place each new server class under `nix-server/src/main/` and each new API class under `nix-api/src/main/`.
- All new classes must use `club.plutoproject.nix` as their root package name.
- Patches may contain only modifications to the NMS code. Do not put code we wrote and that is not protected by Mojang copyright in a patch. Put it in the appropriate `src/main/` source tree instead.

## 5. Record the generated-worktree commit

Paperweight rebuilds each generated worktree and its Git history from upstream and tracked patches. Each feature patch corresponds to one commit. Rebuild tasks serialize those commits back to `.patch` files.

- For an existing patch, locate its commit and amend or fix it up. Do not add a separate correction patch.
- For a new feature patch, create one logical commit at the correct point in that worktree's history. Do not split one change by file or combine unrelated changes.
- Use a concise imperative subject. The subject becomes the patch subject and filename. The body becomes the patch description.
- The body must explain what changed, how it works, the expected performance impact, and any behavior, memory, or maintenance trade-offs. If none are meaningful, say so.
- In `nix-server/src/minecraft/java`, `base` and `file` tags mark history boundaries. Put custom source changes after those tags. They export to `nix-server/minecraft-patches/features`. Do not use per-file `sources` patches.

Commits in the outer repository follow normal Git practices. These generated-worktree commit rules do not apply to unrelated outer configuration or documentation.

## 6. Rebuild the patch

Run the narrowest matching rebuild task from the outer repository root. This repository uses paperweight 2.x tasks. Do not use the old generic `applyPatches` or `rebuildPatches` tasks. There is no single `rebuildAllPatches` task.

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

Minecraft source changes normally use feature patches and `:nix-server:rebuildMinecraftFeaturePatches`. If you are unsure, run `./gradlew tasks --all`. Do not rely on older Paper documentation.

## 7. Verify completion

- Confirm that the expected tracked patch contains the intended diff and commit message.
- Check status in both the owning generated worktree and the outer repository.
- Modified generated source is not enough. The durable tracked patch must contain the change.
