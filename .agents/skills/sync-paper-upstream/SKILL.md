---
name: sync-paper-upstream
description: Sync this fork to Paper's latest commit.
disable-model-invocation: true
---

# Sync Paper upstream

Update the pinned Paper revision, reapply the Nix patches, resolve conflicts in generated worktrees, rebuild the tracked patches, then audit and push the result.

## 1. Check the starting state

Read `AGENTS.md`. Then inspect the outer repository and every existing generated worktree:

```bash
git status --short --branch
for d in paper-server paper-api nix-server/src/minecraft/java nix-server/src/minecraft/resources; do
  [ -d "$d" ] && git -C "$d" status --short --branch
done
```

Stop before overwriting unrelated local changes. Find the pinned Paper revision and upstream repository in the build configuration. Resolve the current commit on Paper's default branch, normally with:

```bash
git ls-remote https://github.com/PaperMC/Paper.git refs/heads/main
```

Proceed only when the starting state is safe and both the old and target revisions are known.

## 2. Update the pin and apply patches

Change only the Paper commit property. Then run:

```bash
./gradlew applyAllPatches
```

A failed Git fetch is a fetch failure, not a patch conflict. Report the actual cause or retry after connectivity is restored.

`applyAllPatches` must either succeed or identify every conflict in its owning generated worktree before you edit anything.

## 3. Resolve conflicts in the owning layer

Keep current upstream behavior wherever it does not conflict with Nix changes. Keep Nix annotations tight and follow the repository conventions in `AGENTS.md`. Do not edit tracked `.patch` files directly. Make changes in the generated worktree, then rebuild the patch.

### Feature-patch conflicts

Resolve conflict markers in the generated worktree, stage the resolved files, and continue the interrupted patch application:

```bash
git -C <worktree> add <resolved-files>
GIT_EDITOR=true git -C <worktree> am --continue
```

Inspect the resulting commit. Rebuild only its owning layer with the task listed in `AGENTS.md`. Existing patches must remain existing commits, so amend or continue them instead of adding correction patches.

### Single build-file conflicts

Use the Paperweight partially patched file under `build/tmp/applyPaperSingleFilePatches/*/work/` as the base for the matching generated file, either `nix-server/build.gradle.kts` or `nix-api/build.gradle.kts`. Apply rejected hunks using current upstream semantics, remove `.rej` files, and run:

```bash
./gradlew rebuildPaperSingleFilePatches
```

When an upstream project dependency is no longer available, keep its original line as a comment if it preserves useful context. Add a Nix annotation immediately above it.

Do not run broad API patch rebuilds while `paper-api` is stale or only partly materialized. Paperweight can serialize that stale tree as hundreds of unintended Nix file patches. If that happens, remove only the newly generated untracked or staged file patches, reapply from a clean generated tree, and confirm that the API file-patch count has returned to its previous value.

Run rebuild tasks separately when Gradle reports an implicit dependency caused by combining root and nested tasks.

Before continuing, make sure every conflict is resolved, every interrupted `git am` operation is complete, and each resolution has been serialized by its owning rebuild task.

## 4. Reapply and converge

Repeat the following command until it succeeds:

```bash
./gradlew applyAllPatches
```

Then rebuild all durable patch layers in separate invocations:

```bash
./gradlew :nix-server:rebuildAllServerPatches
./gradlew rebuildPaperPatches
```

Run `./gradlew applyAllPatches` once more. The final apply must succeed from the rebuilt patches.

## 5. Audit the result

Inspect staged and unstaged changes in the outer repository, including patch index and line-number churn:

```bash
git status --short --branch
git diff --stat
git diff --cached --stat
git diff
git diff --cached
```

Confirm that every generated worktree is clean. Search the generated trees for conflict markers and the repository for `.rej` and `.orig` files. Account for every tracked change. Make sure no unexpected bulk file-patch directory was created.

Every remaining change must be intentional, no conflict artifacts may remain, and all generated worktrees must be clean.

## 6. Commit and push

Unless the user explicitly asks to leave changes uncommitted or unpushed, stage the audited outer changes and create a commit with this exact message:

```text
chore: sync upstream
```

Push the current branch to its configured upstream. If the push is rejected because the remote has diverged or integration is required, stop immediately. Do not pull, fetch and rebase, merge, resolve conflicts, or force-push. Hand those decisions to the user.

After a successful push, confirm that `git status --short --branch` reports a clean, synchronized worktree.
