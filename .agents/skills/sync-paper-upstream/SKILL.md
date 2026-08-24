---
name: sync-paper-upstream
description: Sync a Nix Paperweight fork to Paper's latest commit. Use when the user asks to update the pinned Paper revision, apply upstream patches, resolve resulting conflicts, or rebuild patches after an upstream sync.
compatibility: Nix repository using Paperweight 2.x and the generated-worktree patch workflow documented in AGENTS.md.
---

# Sync Paper Upstream

Treat the pinned Paper revision and tracked patches as durable state; generated worktrees are the conflict-resolution interface.

## 1. Preflight

Read the repository's `AGENTS.md`, then inspect the outer repository and every existing generated worktree:

```bash
git status --short --branch
for d in paper-server paper-api nix-server/src/minecraft/java nix-server/src/minecraft/resources; do
  [ -d "$d" ] && git -C "$d" status --short --branch
done
```

Stop and ask before overwriting unrelated changes. Identify the pin and upstream repository from the build configuration. Resolve Paper's current default-branch commit from the remote, normally:

```bash
git ls-remote https://github.com/PaperMC/Paper.git refs/heads/main
```

Completion criterion: the starting state is safe and both old and target revisions are known.

## 2. Move the Pin and Apply

Update only the Paper commit property, then run:

```bash
./gradlew applyAllPatches
```

A Git fetch exit code is a fetch failure, not a patch conflict; report its actual cause or retry after connectivity is restored.

Completion criterion: `applyAllPatches` succeeds, or every reported conflict has been located in its owning generated layer.

## 3. Resolve Conflicts in the Owning Layer

Preserve the latest upstream behavior outside the Nix intent. Keep Nix annotations tight and follow repository conventions.

### Feature-patch conflict

Resolve conflict markers in the generated worktree, stage the resolution there, and continue the interrupted patch application:

```bash
git -C <worktree> add <resolved-files>
GIT_EDITOR=true git -C <worktree> am --continue
```

Inspect the resulting commit and rebuild the narrow owning layer using the task listed in `AGENTS.md`. Existing patches remain existing commits; amend or continue them rather than creating correction patches.

### Single build-file conflict

Use Paperweight's partially patched file under `build/tmp/applyPaperSingleFilePatches/*/work/` as the base for the corresponding generated output (`nix-server/build.gradle.kts` or `nix-api/build.gradle.kts`). Incorporate rejected hunks against current upstream semantics, remove `.rej` artifacts, then run:

```bash
./gradlew rebuildPaperSingleFilePatches
```

Keep newly unavailable upstream project dependencies as commented original lines when that retains useful upstream context, with a Nix annotation immediately above.

Do not run broad API patch rebuilds while `paper-api` is stale or only partially materialized: Paperweight can serialize the stale tree as hundreds of unintended Nix file patches. If that occurs, remove only those newly generated untracked/staged file patches, reapply from a clean generated tree, and verify the API file-patch count returns to its prior state.

Run rebuild tasks separately when Gradle reports an implicit dependency caused by combining root and nested tasks.

Completion criterion: all conflicts are resolved, interrupted `git am` operations are complete, and each resolution has been serialized by its owning rebuild task.

## 4. Converge

Repeat `./gradlew applyAllPatches` until it succeeds. Then rebuild all durable patch layers, as separate invocations:

```bash
./gradlew :nix-server:rebuildAllServerPatches
./gradlew rebuildPaperPatches
```

Run `./gradlew applyAllPatches` once more to prove the rebuilt artifacts reproduce cleanly.

Completion criterion: the final apply succeeds from the rebuilt patches.

## 5. Audit

Inspect staged and unstaged outer diffs, including patch index/line-number churn:

```bash
git status --short --branch
git diff --stat
git diff --cached --stat
git diff
git diff --cached
```

Confirm all generated worktrees are clean. Search generated trees for conflict markers and the repository for `.rej`/`.orig` files. Account for every tracked change and verify no unexpected bulk file-patch directory was created.

Completion criterion: every outer change is intentional, no conflict artifacts remain, and all generated worktrees report clean status.

## 6. Commit and Push

Unless the user explicitly asks to leave the changes uncommitted or unpushed, stage the audited outer changes and commit them with this exact message:

```text
chore: sync upstream
```

Push the current branch to its configured upstream. If the push is rejected because the remote has diverged or any conflict would require integration, stop immediately and hand control to the user; leave pull, fetch-and-rebase, merge, conflict resolution, and force-push decisions to them.

After a successful push, confirm `git status --short --branch` is clean and synchronized.

Completion criterion: the commit exists remotely and the outer worktree is clean, or a rejected push has been reported without attempting integration.
