# Project Overview

- A Minecraft Paper server fork optimized via Workload-Guided Optimization (WGO), driven by real profiling data under specific workload scenarios.
- Uses a patch-based workflow instead of directly distributing upstream (Paper/Minecraft) source. Managed via Paper’s `paperweight` for patch application, source generation, and patch rebuilding.

# Workflow Rules

- Parallelize tasks when appropriate.
- Minecraft internals involve deep call stacks and complex object graphs. When evaluating whether a change breaks vanilla behavior, trace call sites thoroughly and understand real usage, not just the local method/class.
- No unit tests. Do not run tests.

# Repository Structure

- Custom patches to Minecraft or API live under:
  - `nix-api`
  - `nix-server`

- Upstream Paper-added server/API code (e.g. Bukkit, CraftBukkit) lives under:
  - `paper-server`
  - `paper-api`  
  These are generated via `paperweight` Gradle tasks and may not exist until patches are applied.

- Patch storage:
  - `nix-api` / `nix-server`
    - `minecraft-patches` → patches against Minecraft source
    - `paper-patches` → patches against Paper-added layers

- Minecraft source:
  - `nix-server/src/minecraft/java`
  - `nix-server/src/minecraft/resources`  
  These are separate git submodules and gitignored.  
  Searching from repo root may skip them — explicitly include these paths when needed.

- Patch types:
  - `feature`:
    - Generated from commits in the Minecraft subrepo.
    - Applied back onto the subrepo during patch apply.
    - Any modification requires rebuilding patches, or changes will be lost.
  - `resource`: same behavior as `feature`.
  - `source`:
    - File-level aggregation (e.g. `A.java.patch` contains full file diff).
    - Not commit-based.
    - Not used in this project — only use feature patches.

- Patch naming:
  - Derived from commit message.
  - Commit description is embedded into the patch.

# Coding Guidelines

- Minimize patch surface area. Only modify what is necessary.
- Do not add or remove imports.
  - If an import becomes unused, keep it.
  - If a new class is needed, use the fully qualified name instead of adding an import.

- Patch annotations are required:
  - Multi-line changes:
    ```
    // Nix start - <description>
    ...
    // Nix end
    ```
  - Single-line change:
    ```
    code // Nix - <description>
    ```

- Annotation rules:
  - Include a concise description (usually patch title).
  - Keep the annotated region minimal.
  - Do not wrap large untouched blocks.
  - If multiple nearby changes belong to the same logical flow, group them under one block instead of splitting excessively.

- Commit rules (within subrepo):
  - One commit = one patch.
  - Do not spam commits.
  - If a patch needs changes, amend the existing commit, do not create a new one.

- Patch commits' description must include:
  - What was changed
  - How it was changed
  - Performance benefit
  - Trade-offs (if any)

- Outside the subrepo: normal commit practices apply.