# Nix Content System

## Contents

- [1. Scope, goals, and boundaries](#1-scope-goals-and-boundaries)
  - [1.1 Scope](#11-scope)
  - [1.2 Goals](#12-goals)
  - [1.3 Compatibility representation model](#13-compatibility-representation-model)
- [2. Domain model and terminology](#2-domain-model-and-terminology)
  - [2.1 Terms](#21-terms)
- [3. Runtime item model](#3-runtime-item-model)
  - [3.1 Minecraft component model](#31-minecraft-component-model)
  - [3.2 Gameplay hooks](#32-gameplay-hooks)
- [4. Public API surface](#4-public-api-surface)
  - [4.1 Consolidated public API skeleton](#41-consolidated-public-api-skeleton)
  - [4.2 Public API namespace](#42-public-api-namespace)
  - [4.3 Registration lifecycle](#43-registration-lifecycle)
- [5. Runtime and Bukkit representations](#5-runtime-and-bukkit-representations)
- [6. Client Projection](#6-client-projection)
  - [6.1 Projection modifiers](#61-projection-modifiers)
  - [6.2 Current client-to-server authority model](#62-current-client-to-server-authority-model)
  - [6.3 Projection-aware remote state](#63-projection-aware-remote-state)
- [7. Persistence and recovery](#7-persistence-and-recovery)
  - [7.1 Persistent Form](#71-persistent-form)
  - [7.2 Recovery](#72-recovery)
- [8. Consolidated decisions](#8-consolidated-decisions)

## 1. Scope, goals, and boundaries

### 1.1 Scope

The Content System extends items only. Blocks and other content types are out of scope.

### 1.2 Goals

A plugin can register a namespaced Custom Item Type into the server's real item registry. While Nix is running, an item stack retains that type and may contain both vanilla and plugin-defined Data Components. Unmodified Minecraft clients, ordinary Bukkit plugins, and worlds opened without Nix must remain usable.

### 1.3 Compatibility representation model

The design distinguishes three compatibility boundaries instead of treating every representation as a projection:

```text
Runtime Item Stack (authoritative)
  ├─ Bukkit View
  ├─ Client Projection
  └─ Persistent Form
```

## 2. Domain model and terminology

### 2.1 Terms

#### 2.1.1 Custom Item Type

A plugin-owned, namespaced item type registered as a distinct server-side item type for the current server run.

#### 2.1.2 Runtime Item Stack

The authoritative in-memory stack. It retains its Custom Item Type and all effective vanilla and custom component state.

#### 2.1.3 Vanilla Material

The `org.bukkit.Material` selected by a Custom Item Type for all vanilla compatibility boundaries: the Bukkit type, Client Projection base, and Persistent Form item identity.

#### 2.1.4 Vanilla Component

A Data Component whose type and semantics are understood by an unmodified Minecraft client.

#### 2.1.5 Custom Component

A plugin-registered Paper `DataComponentType` used for server-side state. It may be attached to either a vanilla or Custom Item Stack. An unmodified client does not understand its type or value. Custom types use Paper's existing `DataComponentType.Valued<T>` or `DataComponentType.NonValued` and the existing Bukkit `ItemStack` component methods; there is no parallel `CustomDataComponentType` API.

#### 2.1.6 Bukkit View

A compatibility view over a Runtime Item Stack. It represents a Custom Item Type as its Vanilla Material through `getType()`, while custom identity remains available through `getItemType()`. Vanilla and Custom Components remain accessible through Paper's Data Component API. This is not a Client Projection.

#### 2.1.7 Client Projection

A transient vanilla-compatible stack derived when an item is sent to an unmodified client. Custom Components are omitted as top-level component types, while their persistent patch and the custom identity travel as opaque `nix:item` recovery metadata.

#### 2.1.8 Persistent Form

A vanilla-loadable stack written outside process memory. Vanilla Components use Minecraft's ordinary item component representation; custom identity and the persistent per-stack Custom Component patch use a Recovery Envelope.

#### 2.1.9 Recovery Envelope

Opaque Nix metadata nested in `minecraft:custom_data` in a Persistent Form or Client Projection. It is not present on a successfully recovered custom Runtime Item Stack. Client Projection may extend it with projection-only restoration data.

#### 2.1.10 Vanilla fallback

A vanilla stack using the Custom Item Type's Vanilla Material and the Vanilla Components found in its Persistent Form. Nix leaves an item in this form for the current load when its custom type or any present custom component cannot be resolved safely. The intact Recovery Envelope remains available for a future load.

## 3. Runtime item model

### 3.1 Minecraft component model

Modern Minecraft item behavior is a combination of the `Item` implementation, type-default Data Components, and the per-stack component patch:

```text
Item implementation
  + type-default components (prototype)
  + per-stack component patch
  = effective stack behavior and state
```

Data Components are not merely an arbitrary replacement for legacy item NBT. Some are descriptive data, while others directly compose behavior. In the current Minecraft 26.2 sources:

- `minecraft:apple` has default `FOOD` and `CONSUMABLE` components.
- `minecraft:diamond_sword` has default `TOOL`, `WEAPON`, attributes, durability, repair, and enchantability components.
- Removing `CONSUMABLE` from an apple's stack patch makes the effective stack non-consumable.

A Custom Item Type should therefore use vanilla components directly whenever they accurately express its server-side semantics. Custom Components are primarily for server-only state or semantics that vanilla cannot represent.

The configured Vanilla Material is representation-only and does not donate its type-default components to the Runtime Custom Item Type. The custom type begins with Minecraft's generic item defaults plus only the defaults explicitly declared by its registration builder. For example, choosing `DIAMOND_SWORD` does not implicitly add `TOOL`, `WEAPON`, durability, attributes, repair, or enchantability. Client Projection reconciles the explicit canonical state against the Vanilla Material prototype and emits removals for vanilla defaults absent at runtime.

### 3.2 Gameplay hooks

Custom Item Types are not limited to a generic NMS `Item` plus Bukkit event listeners. Their registration builder accepts stable, typed item hooks whose contexts/results expose Bukkit/Paper and Content System types rather than NMS. An internal bridge `Item` dispatches corresponding Minecraft behavior into an immutable hook table. Vanilla Components remain preferred for behavior they already model, while hooks cover behavior requiring code.

```java
builder.addHook(ItemHooks.ON_USE, context -> {
    ItemInteractionResult result = context.defaultBehavior();
    // Extend the component-driven default behavior.
    return result;
});
```

#### 3.2.1 Hook registration and callback model

`addHook` has map/set semantics, not list semantics: each Item Type has at most one callback for a given hook descriptor, and a later call for the same hook replaces the earlier callback. A missing callback directly runs the bridge item's vanilla default behavior. A callback may extend that behavior through `defaultBehavior()`/`runDefaultBehavior()`, or replace it by not calling the default. Like repeated Java `super` calls, every explicit default invocation executes the underlying behavior again; Content System does not impose a defensive once-only restriction.

Hook callbacks are shared for the lifetime of the registered Item Type. They may capture immutable configuration and plugin service references, but never per-stack, per-player, or per-invocation mutable state; all per-stack state belongs in Data Components.

#### 3.2.2 Hook catalog and compatibility boundary

The stable gameplay hook catalog is:

```text
Interaction
  ON_USE
  ON_USE_ON_BLOCK
  ON_INTERACT_LIVING_ENTITY

Active use lifecycle
  ON_USE_TICK
  ON_FINISH_USE
  ON_RELEASE_USE

Mining
  CAN_DESTROY_BLOCK
  DESTROY_SPEED
  IS_CORRECT_TOOL_FOR_DROPS
  ON_MINE_BLOCK

Combat
  ATTACK_DAMAGE_BONUS
  ON_HURT_ENTITY
  AFTER_HURT_ENTITY

Item lifecycle
  INVENTORY_TICK
  ON_CRAFTED
  ON_DESTROYED_AS_ITEM_ENTITY

Container capability
  CAN_FIT_INSIDE_CONTAINER_ITEMS
```

Slot-stacking overrides and custom damage-source selection are deferred until stable transaction and damage APIs are designed. Client presentation hooks such as name, tooltip, glint, durability bar, and use animation are deliberately absent; they use Vanilla Components and Projection Modifiers. Type properties such as crafting remainder remain registration builder data rather than callbacks.

#### 3.2.3 Interaction hooks

The three held-item interaction hooks share authoritative player, live Bukkit stack, and Bukkit `EquipmentSlot.HAND`/`OFF_HAND` fields through `HeldItemContext` without forcing the same result type. The consolidated API skeleton defines their exact interfaces.

`ItemInteractionResult` provides pass, fail, handled success, handled-without-swing consume, and try-empty-hand outcomes. A plugin-created `success()` always maps to server-swing success; Content System does not expose construction of NMS client-swing success because an unmodified client may not predict custom behavior. A result obtained from `defaultBehavior()` retains exact underlying vanilla swing semantics when propagated unchanged. `tryEmptyHand()` is valid for the block-use pipeline.

`ItemUseResult` extends `ItemInteractionResult`, so ON_USE handlers have the usual result operations without a wrapper layer. It adds immutable `success(ItemStack)` and `consume(ItemStack)` overloads for the one server path that consumes a transformed held stack.

The replacement is copied immediately and may be vanilla or custom. ON_USE_ON_BLOCK and ON_INTERACT_LIVING_ENTITY return only the base result type, so transformation factories are absent at compile time. Ordinary in-place changes to a live context stack require no transformation result. The API does not expose `withoutItemInteraction()` construction, although an unchanged default result can preserve that internal vanilla metadata.

`ON_USE_ON_BLOCK`'s `tryEmptyHand()` asks the interaction pipeline to continue with the clicked block's empty-hand behavior. Its `clickedBlock()` is live, while `interactionPoint()` is a defensive copy whose mutation cannot change the actual hit result.

#### 3.2.4 Active-use hooks

`ON_USE_TICK` exposes the non-player-capable Bukkit `LivingEntity`, live active stack, active hand, the exact pre-decrement remaining ticks, and elapsed used ticks. It is a void callback with an explicitly callable default operation. The hook remains at the vanilla `Item#onUseTick` position: consumable particles and sounds run first, and on the server a present `KINETIC_WEAPON` component executes instead of the Item hook, so Nix does not invoke `ON_USE_TICK` for that tick.

`ON_FINISH_USE` runs when an active use completes normally. Its context contains the `LivingEntity`, live active stack, and hand, and its default operation returns the generic Item's component-driven finish result. The callback directly returns a non-null Bukkit `ItemStack`: the context stack keeps in-place changes, another returned stack is snapshotted before installation, and `ItemStack.empty()` consumes it completely. The outer ItemStack pipeline applies `USE_REMAINDER` and use-cooldown component side effects after the hook result.

`ON_RELEASE_USE` runs when active use is released or interrupted. It exposes the `LivingEntity`, live active stack, hand, remaining ticks, and used ticks. Its explicit `ItemReleaseUseResult` replaces NMS's opaque boolean: `APPLY_AFTER_USE_EFFECTS` applies `USE_REMAINDER` and cooldown after the callback, while `SKIP_AFTER_USE_EFFECTS` does not. Either result still ends active use.

#### 3.2.5 Mining and block capability hooks

`CAN_DESTROY_BLOCK` mirrors the built-in Item capability using a `LivingEntity`, live stack, live Bukkit block, and captured `BlockData`, and directly returns boolean. It is not a security boundary or correct-tool-for-drops test. Nix preserves CraftBukkit/Paper behavior: false pre-cancels `BlockBreakEvent`, which another plugin may un-cancel. Dynamic server-only answers can produce client prediction rollback; callers that need the client to know a static creative-breaking restriction should encode it in the projected vanilla `TOOL` component instead.

`DESTROY_SPEED` receives only the live stack and captured `BlockData`, matching the built-in Item query, and returns a finite non-negative base float. Player attributes, effects, water, and airborne modifiers apply afterwards. Invalid callback results fall back to the last successfully completed default result, or execute the default once when the callback completed none. Client-visible static mining rules belong in the projected `TOOL` component; dynamic server-only speed may visibly diverge from client prediction.

`IS_CORRECT_TOOL_FOR_DROPS` reuses the same stack-plus-captured-block-data context and directly returns boolean. The answer governs both correct-tool loot eligibility and vanilla's `/30` versus `/100` destroy-progress divisor. It has no player, world, or position because the built-in Item query has none; static client-visible rules again belong in `TOOL`.

`ON_MINE_BLOCK` runs after the block has normally been removed. It receives the player, live main-hand stack, a defensively copied location, and pre-removal `BlockData`; it deliberately does not expose a misleading live block that is usually air by then. The explicit result only chooses whether to award the item-used statistic. Its default applies `TOOL.damagePerBlock` durability behavior and returns the corresponding statistic outcome; neither result changes the already-decided removal or loot eligibility.

#### 3.2.6 Combat hooks

`ATTACK_DAMAGE_BONUS` receives the attacker, victim, exact live weapon stack, damage-before-bonus, and Bukkit `DamageSource`, and returns a finite additive float; negative bonuses are valid. Nix adds a source-compatible Item overload carrying stack and attacker and changes the Player and Mob call sites to use it. The overload delegates to the existing vanilla signature, preserving existing subclasses and calculation order, while the custom bridge can read per-stack components.

`ON_HURT_ENTITY` and `AFTER_HURT_ENTITY` share attacker, victim, and live weapon context and are void callbacks after successful damage to a living victim. The first runs before enchantment post-attack effects. The latter runs afterwards and before `WEAPON.itemDamagePerAttack`, but only in vanilla paths that invoke it: the stack must have `WEAPON`, and the current Mob attack path does not call it. Failed or event-cancelled damage calls neither hook. Combat APIs consistently name the attacked entity `victim`; non-attack APIs such as living-entity interaction retain `target`.

#### 3.2.7 Lifecycle and container hooks

`INVENTORY_TICK` exposes the owning Bukkit `Entity` as `entity()`, live stack, and nullable equipment slot; null means ordinary inventory storage and no inventory index is synthesized. It is a server-only, once-per-tick void callback and a hot hook: callbacks must remain low-cost, non-blocking, and free of I/O.

`ON_CRAFTED` unifies player crafting and automated Crafter post-processing. It receives the live result stack, a positive crafted amount, and nullable player (`null` means automated). Mutations affect the delivered or dispensed result. Each path's default delegates its corresponding built-in method. The implementation normalizes dispersed call sites and suppresses the player method's internal post-process delegation from causing a duplicate hook invocation.

`ON_DESTROYED_AS_ITEM_ENTITY` is a void callback only when a dropped Bukkit `Item` dies from uncancelled damage, not despawn, pickup, merge, or command removal. It receives the not-yet-discarded entity, its live stack, and Bukkit `DamageSource`; destruction remains non-cancellable and discard follows the callback. Nix adds a compatible enriched Item overload that delegates to the old signature, preserving vanilla `BundleItem` and `BlockItem` overrides.

`CAN_FIT_INSIDE_CONTAINER_ITEMS` uses a stack-only result context and returns boolean. Nix adds a compatible stack-aware Item overload and changes the bundle and container-item-slot callers, allowing per-stack Custom Components while delegating vanilla items to the original type-level method. Dynamic server-only answers may require inventory resynchronization because the unmodified client evaluates its projected vanilla item.

#### 3.2.8 Execution and failure policy

Capability and numeric query hooks (`CAN_DESTROY_BLOCK`, `DESTROY_SPEED`, `IS_CORRECT_TOOL_FOR_DROPS`, `ATTACK_DAMAGE_BONUS`, and `CAN_FIT_INSIDE_CONTAINER_ITEMS`) receive live objects but are read-only by API contract: callbacks are synchronous, low-cost, non-blocking, free of I/O and side effects, and cannot assume one evaluation. This is a documentation-level contract, not a runtime copy, mutation check, or rollback mechanism. Side effects belong in action hooks or Bukkit events.

A non-fatal plugin callback exception or invalid callback result is rate-limited by Item/hook/registrant and falls back to the default behavior. If the callback completed one or more default calls, the last successfully completed default result is reused rather than adding another execution; otherwise fallback executes the default once. Action side effects performed before failure are not rolled back. An uncaught failure from the default behavior itself is distinguished from callback failure, is never retried or disguised as plugin fallback, and propagates as an internal server exception. One callback failure does not permanently disable it. Registrant identity is diagnostic metadata, not namespace ownership.

Gameplay hooks run synchronously at the corresponding built-in Paper/Minecraft Item call site and do not schedule, advance, delay, duplicate, or reorder Bukkit/Paper events. Event cancellation and state mutation therefore affect hook reachability and context exactly as they affect that built-in method. Enriched overloads retain the old call site. `ON_CRAFTED` intentionally unifies player and automated entry points, but each remains at its respective built-in crafting callback. Event-order examples document the inspected pinned version rather than define a separate Nix compatibility schedule; an upstream event move relative to the Item call is inherited. Callbacks never auto-hop unsupported asynchronous plugin calls onto the server thread and must not block it.

#### 3.2.9 Handler typing and context lifetime

Hook descriptors use one `ItemHook<C, R>` type with `ItemHookHandler<C, R>` for result callbacks. Void descriptors use `R = Void`, but Java callers bind a natural void lambda through an `addHook(ItemHook<C, Void>, Consumer<C>)` overload; the internal adapter supplies the null `Void` value. Other result types use the generic overload and remain compile-time checked. Primitive boolean and float results may box; specialization is deferred until profiling demonstrates a material cost.

Hook contexts are intended for synchronous callback-scoped use, but Content System does not invalidate them, clear captured references, inspect retention, or guard default calls after callback return. Retaining a context or invoking its default operation later is unsupported plugin behavior; Nix adds no runtime handling for it.

### 3.3 Canonical component example

For example, a healing apple should normally contain vanilla `FOOD` and `CONSUMABLE` components in memory rather than gaining them only during projection:

```java
builder
    .vanillaMaterial(Material.APPLE)
    .component(DataComponentTypes.FOOD, food)
    .component(DataComponentTypes.CONSUMABLE, consumable)
    .component(MyComponents.HEALING_STRENGTH, 4);
```

## 4. Public API surface

### 4.1 Consolidated public API skeleton

#### 4.1.1 Core registry and component builders

The following pseudo-Java fixes the public type relationships and method shapes; ordinary nullness, contract, and API-status annotations are omitted for readability. Registry writes retain Paper's compose/register/builder flow.

```java
public interface ContentSystem {
    static ItemType itemType(Key key) { /* API bridge */ }
    static <T> DataComponentType.Valued<T> valuedDataComponentType(Key key) { /* API bridge */ }
    static DataComponentType.NonValued nonValuedDataComponentType(Key key) { /* API bridge */ }

    void refreshItemProjections(Player viewer);

    final class RegistryEvents {
        public static final RegistryEventProvider<ItemType, ItemTypeRegistryEntry.Builder> ITEM;
        public static final RegistryEventProvider<DataComponentType, DataComponentTypeRegistryEntry.Builder> DATA_COMPONENT_TYPE;
    }
}

public interface ItemTypeRegistryEntry {
    interface Builder extends RegistryBuilder<ItemType> {
        Builder vanillaMaterial(Material material);
        <T> Builder component(DataComponentType.Valued<T> type, T value);
        Builder component(DataComponentType.NonValued type);
        <T> Builder project(DataComponentType.Valued<T> type, ProjectionModifier.Valued<T> modifier);
        Builder project(DataComponentType.NonValued type, ProjectionModifier.NonValued modifier);
        Builder suppressProjection(DataComponentType type);
        <C, R> Builder addHook(ItemHook<C, R> hook, ItemHookHandler<C, R> handler);
        <C> Builder addHook(ItemHook<C, Void> hook, Consumer<C> handler);
    }
}

public interface DataComponentTypeRegistryEntry {
    interface Builder extends RegistryBuilder<DataComponentType> {
        // Components are transient unless the matching persistence method is called.
        <T> Builder persistent(Codec<T> codec); // valued
        Builder persistent();                  // non-valued marker
        <T> Builder defaultProjection(ProjectionModifier.Valued<T> modifier);
        Builder defaultProjection(ProjectionModifier.NonValued modifier);
    }
}
```

#### 4.1.2 Typed declarations and registration example

Custom Components are transient by default. A Valued Custom Component becomes persistent through `persistent(codec)`. A Non-Valued Custom Component becomes persistent through no-argument `persistent()`; its presence/removal needs no plugin codec and is represented directly in the Recovery Envelope.

Declaration and registration use the same predeclared references:

```java
public final class MyComponents {
    public static final DataComponentType.Valued<Soulbound> SOULBOUND =
        ContentSystem.valuedDataComponentType(Key.key("example:soulbound"));
    public static final DataComponentType.NonValued UNTRADEABLE =
        ContentSystem.nonValuedDataComponentType(Key.key("example:untradeable"));
}

public final class MyProjections {
    public static final ProjectionModifier.Valued<Soulbound> SOULBOUND =
        (context, value, output) -> { /* project value */ };
    public static final ProjectionModifier.NonValued UNTRADEABLE =
        (context, output) -> { /* project marker */ };
}

public final class MyItems {
    public static final ItemType HEALING_APPLE =
        ContentSystem.itemType(Key.key("example:healing_apple"));
}

bootstrapContext.getLifecycleManager().registerEventHandler(
    ContentSystem.RegistryEvents.DATA_COMPONENT_TYPE.compose(),
    event -> {
        event.registry().register(MyComponents.SOULBOUND, builder -> builder
            .persistent(Soulbound.CODEC)
            .defaultProjection(MyProjections.SOULBOUND));
        event.registry().register(MyComponents.UNTRADEABLE, builder -> builder
            .persistent()
            .defaultProjection(MyProjections.UNTRADEABLE));
    }
);

bootstrapContext.getLifecycleManager().registerEventHandler(
    ContentSystem.RegistryEvents.ITEM.compose(),
    event -> event.registry().register(MyItems.HEALING_APPLE, builder -> builder
        .vanillaMaterial(Material.APPLE)
        .component(DataComponentTypes.FOOD, HEALING_FOOD)
        .component(DataComponentTypes.CONSUMABLE, HEALING_CONSUMABLE)
        .component(MyComponents.UNTRADEABLE)
        .project(MyComponents.SOULBOUND, MyProjections.SOULBOUND)
        .addHook(ItemHooks.ON_USE, context -> ItemUseResult.success()))
);
```

#### 4.1.3 Hook descriptors and default-composition contracts

Hook descriptors and default-composition contracts:

```java
public final class ItemHook<C, R> { /* closed descriptor */ }

@FunctionalInterface
public interface ItemHookHandler<C, R> {
    R handle(C context);
}

public interface DefaultResultBehavior<R> {
    R defaultBehavior();
}

public interface DefaultVoidBehavior {
    void runDefaultBehavior();
}

public final class ItemHooks {
    public static final ItemHook<ItemUseContext, ItemUseResult> ON_USE;
    public static final ItemHook<ItemUseOnBlockContext, ItemInteractionResult> ON_USE_ON_BLOCK;
    public static final ItemHook<ItemInteractLivingEntityContext, ItemInteractionResult> ON_INTERACT_LIVING_ENTITY;
    public static final ItemHook<ItemUseTickContext, Void> ON_USE_TICK;
    public static final ItemHook<ItemFinishUseContext, ItemStack> ON_FINISH_USE;
    public static final ItemHook<ItemReleaseUseContext, ItemReleaseUseResult> ON_RELEASE_USE;
    public static final ItemHook<ItemCanDestroyBlockContext, Boolean> CAN_DESTROY_BLOCK;
    public static final ItemHook<ItemBlockStateContext<Float>, Float> DESTROY_SPEED;
    public static final ItemHook<ItemBlockStateContext<Boolean>, Boolean> IS_CORRECT_TOOL_FOR_DROPS;
    public static final ItemHook<ItemMineBlockContext, ItemMineBlockResult> ON_MINE_BLOCK;
    public static final ItemHook<ItemAttackDamageBonusContext, Float> ATTACK_DAMAGE_BONUS;
    public static final ItemHook<ItemHurtEntityContext, Void> ON_HURT_ENTITY;
    public static final ItemHook<ItemHurtEntityContext, Void> AFTER_HURT_ENTITY;
    public static final ItemHook<ItemInventoryTickContext, Void> INVENTORY_TICK;
    public static final ItemHook<ItemCraftedContext, Void> ON_CRAFTED;
    public static final ItemHook<ItemDestroyedAsEntityContext, Void> ON_DESTROYED_AS_ITEM_ENTITY;
    public static final ItemHook<ItemContainerFitContext, Boolean> CAN_FIT_INSIDE_CONTAINER_ITEMS;
}
```

#### 4.1.4 Interaction contexts and results

Interaction contexts and results:

```java
public interface HeldItemContext {
    Player player();
    ItemStack itemStack();
    EquipmentSlot hand();
}

public interface ItemUseContext extends HeldItemContext, DefaultResultBehavior<ItemUseResult> {}

public interface ItemUseOnBlockContext extends HeldItemContext, DefaultResultBehavior<ItemInteractionResult> {
    Block clickedBlock();
    BlockFace clickedFace();
    Location interactionPoint();
    boolean insideBlock();
    boolean hitWorldBorder();
    boolean secondaryUseActive();
}

public interface ItemInteractLivingEntityContext extends HeldItemContext, DefaultResultBehavior<ItemInteractionResult> {
    LivingEntity target();
}

public interface ItemInteractionResult {
    static ItemInteractionResult pass() { /* API bridge */ }
    static ItemInteractionResult fail() { /* API bridge */ }
    static ItemInteractionResult success() { /* server swing */ }
    static ItemInteractionResult consume() { /* no swing */ }
    static ItemInteractionResult tryEmptyHand() { /* API bridge */ }
}

public interface ItemUseResult extends ItemInteractionResult {
    static ItemUseResult pass() { /* API bridge */ }
    static ItemUseResult fail() { /* API bridge */ }
    static ItemUseResult success() { /* server swing */ }
    static ItemUseResult success(ItemStack transformedHeldItem) { /* snapshot */ }
    static ItemUseResult consume() { /* no swing */ }
    static ItemUseResult consume(ItemStack transformedHeldItem) { /* snapshot */ }
}
```

#### 4.1.5 Active-use, mining, combat, lifecycle, and capability contexts

Active-use, mining, combat, lifecycle, and capability contexts:

```java
public interface ItemUseTickContext extends DefaultVoidBehavior {
    LivingEntity entity();
    ItemStack itemStack();
    EquipmentSlot hand();
    int remainingTicks();
    int usedTicks();
}

public interface ItemFinishUseContext extends DefaultResultBehavior<ItemStack> {
    LivingEntity entity();
    ItemStack itemStack();
    EquipmentSlot hand();
}

public interface ItemReleaseUseContext extends DefaultResultBehavior<ItemReleaseUseResult> {
    LivingEntity entity();
    ItemStack itemStack();
    EquipmentSlot hand();
    int remainingTicks();
    int usedTicks();
}

public enum ItemReleaseUseResult {
    APPLY_AFTER_USE_EFFECTS,
    SKIP_AFTER_USE_EFFECTS
}

public interface ItemCanDestroyBlockContext extends DefaultResultBehavior<Boolean> {
    LivingEntity entity();
    ItemStack itemStack();
    Block block();
    BlockData blockData();
}

public interface ItemBlockStateContext<R> extends DefaultResultBehavior<R> {
    ItemStack itemStack();
    BlockData blockData();
}

public interface ItemMineBlockContext extends DefaultResultBehavior<ItemMineBlockResult> {
    Player player();
    ItemStack itemStack();
    Location blockLocation();
    BlockData minedBlockData();
}

public enum ItemMineBlockResult {
    AWARD_ITEM_USED_STAT,
    SKIP_ITEM_USED_STAT
}

public interface ItemAttackDamageBonusContext extends DefaultResultBehavior<Float> {
    LivingEntity attacker();
    Entity victim();
    ItemStack weapon();
    float damageBeforeBonus();
    DamageSource damageSource();
}

public interface ItemHurtEntityContext extends DefaultVoidBehavior {
    LivingEntity attacker();
    LivingEntity victim();
    ItemStack weapon();
}

public interface ItemInventoryTickContext extends DefaultVoidBehavior {
    Entity entity();
    ItemStack itemStack();
    @Nullable EquipmentSlot equipmentSlot();
}

public interface ItemCraftedContext extends DefaultVoidBehavior {
    ItemStack itemStack();
    int craftedAmount();
    @Nullable Player player();
    default boolean automated() { return this.player() == null; }
}

public interface ItemDestroyedAsEntityContext extends DefaultVoidBehavior {
    org.bukkit.entity.Item itemEntity();
    ItemStack itemStack();
    DamageSource damageSource();
}

public interface ItemContainerFitContext extends DefaultResultBehavior<Boolean> {
    ItemStack itemStack();
}
```

#### 4.1.6 Projection API

Projection API:

```java
public interface ProjectionModifier {
    @FunctionalInterface
    interface Valued<T> extends ProjectionModifier {
        void apply(ProjectionContext context, T value, ProjectionOutput output);
    }

    @FunctionalInterface
    interface NonValued extends ProjectionModifier {
        void apply(ProjectionContext context, ProjectionOutput output);
    }
}

public interface ProjectionContext {
    Player viewer();
    ProjectionSource source();
}

public interface ProjectionSource {
    ItemType itemType();
    int amount();
    <T> @Nullable T getData(DataComponentType.Valued<T> type);
    boolean hasData(DataComponentType type);
}

public interface ProjectionOutput {
    Material vanillaMaterial();
    <T> @Nullable T get(DataComponentType.Valued<T> type);
    boolean has(DataComponentType type);
    <T> void set(DataComponentType.Valued<T> type, T value);
    void set(DataComponentType.NonValued type);
    void unset(DataComponentType type);
}
```

### 4.2 Public API namespace

All Content System additions and their server implementations use the project-owned package namespace:

```java
club.plutoproject.nix.contentsystem
```

This includes `ContentSystem`, custom registry entry APIs, and projection APIs. Existing Bukkit/Paper classes gain methods referring to these types where integration requires it; Content System types are not placed into an upstream-owned package merely to avoid imports.

### 4.3 Registration lifecycle

Custom Item Types and Custom Component Types are registered only during Paper's existing `PluginBootstrap` lifecycle. Nix does not introduce a second bootstrap phase.

Paper runs plugin bootstrappers before built-in registries are frozen. `JavaPlugin#onLoad()` occurs later: worlds are not yet prepared, but the item and Data Component Type registries are already frozen and datapack loading has occurred. Nix will not reopen those registries during `onLoad()`.

Registration is immutable for the remainder of the server run. Plugin disable or reload does not unregister types. As with a Paper registry entry already materialized before freeze, bootstrap content remains server-lifetime: Custom Component codecs, Projection Modifiers, and gameplay handlers continue to execute after the registering plugin is disabled. Paper removes lifecycle registration handlers on disable but does not remove entries from the currently loaded frozen registry; Nix's static Item and Data Component Type registries are not rebuilt by ordinary datapack reload. Runtime failures caused by plugin-owned services being torn down use the normal callback failure policy.

Only two Content System event providers expose mutation:

```java
ContentSystem.RegistryEvents.ITEM
ContentSystem.RegistryEvents.DATA_COMPONENT_TYPE
```

They write to the real NMS `ITEM` and `DATA_COMPONENT_TYPE` registries during bootstrap. Paper's ordinary `Registry<ItemType>` and `Registry<DataComponentType>` remain read-only query views, but automatically expose the resulting entries through their normal wrappers. Nix does not add `RegistryEvents.ITEM` or `RegistryEvents.DATA_COMPONENT_TYPE` to the upstream-owned Paper API. Component registration atomically installs its NMS type, Paper value adapter, and Content System persistence/projection metadata.

The Content System does not add a plugin-descriptor namespace field or infer namespace ownership. Registration accepts keys independently of the registering plugin's name, allowing cooperating plugins to share a namespace. The `minecraft` namespace is reserved for Mojang content and `nix` is reserved for Content System internals; plugins may otherwise use any syntactically valid namespace. Duplicate registry keys are a fatal bootstrap registration error.

Illustrative registration shape:

```java
public final class MyBootstrap implements PluginBootstrap {
    @Override
    public void bootstrap(BootstrapContext context) {
        context.getLifecycleManager().registerEventHandler(
            ContentSystem.RegistryEvents.ITEM.compose(),
            event -> event.registry().register(
                MyItems.HEALING_APPLE,
                builder -> builder
                    .vanillaMaterial(Material.APPLE)
                    .component(DataComponentTypes.FOOD, food)
                    .component(DataComponentTypes.CONSUMABLE, consumable)
            )
        );
    }
}
```

`onLoad()` may retrieve registered types and initialize plugin-owned services referenced indirectly by bootstrap callbacks, but Nix provides no runtime hook mutation or binding API. Hook callbacks are set only on the bootstrap Item Type registration builder; runtime `setHook`, `removeHook`, and `rebindHook` operations do not exist.

#### 4.3.1 DFU codec API

Persistent Custom Data Components use `com.mojang.serialization.Codec<T>` directly. Codec is supplied by Mojang's separately published DataFixerUpper library rather than by NMS, so the Content System does not reproduce or wrap its combinator API:

```java
public record Soulbound(UUID owner, int level) {
    private static final Codec<UUID> UUID_CODEC = Codec.STRING.xmap(
        UUID::fromString,
        UUID::toString
    );

    public static final Codec<Soulbound> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        UUID_CODEC.fieldOf("owner").forGetter(Soulbound::owner),
        Codec.intRange(0, 100).optionalFieldOf("level", 1).forGetter(Soulbound::level)
    ).apply(instance, Soulbound::new));
}
```

The Nix API publishes an API dependency on the exact DataFixerUpper version used by its pinned Minecraft server (10.0.21 for the currently inspected 26.2 build). Plugins compile against that provided dependency and must not shade or relocate an incompatible private DFU copy. Nix updates the exposed DFU version together with its server dependency. The Content System consumes the codec with internal Minecraft dynamic ops, including `NbtOps`, but does not expose NMS ops classes in its own signatures.

#### 4.3.2 Typed Data Component references

Paper's `Registry<DataComponentType>` necessarily erases a custom valued component's Java value type. Plugins therefore predeclare key-backed references that already implement Paper's existing component interfaces:

```java
public final class MyComponents {
    public static final DataComponentType.Valued<Soulbound> SOULBOUND =
        ContentSystem.valuedDataComponentType(Key.key("example", "soulbound"));

    public static final DataComponentType.NonValued UNTRADEABLE =
        ContentSystem.nonValuedDataComponentType(Key.key("example", "untradeable"));

    private MyComponents() {
    }
}
```

Bootstrap registers the same reference:

```java
event.registry().register(
    MyComponents.SOULBOUND,
    builder -> builder
        .persistent(Soulbound.CODEC)
        .defaultProjection(SoulboundProjections.LORE)
);
```

Registration binds the reference to the real NMS registry holder and Paper adapter. Afterwards it works directly with existing Bukkit methods and preserves `T` without casts:

```java
stack.setData(MyComponents.SOULBOUND, value);
Soulbound value = stack.getData(MyComponents.SOULBOUND);
```

Using an unbound reference before its bootstrap registration completes fails with a clear lifecycle error. Registry lookup by raw key remains available but cannot recover the erased Java value type safely.

#### 4.3.3 Typed Item Type references

Custom Item Types use the same key-backed declaration pattern with Paper's existing, non-parallel `ItemType`:

```java
public final class MyItems {
    public static final ItemType HEALING_APPLE =
        ContentSystem.itemType(Key.key("my_plugin", "healing_apple"));

    private MyItems() {
    }
}
```

Bootstrap registers that same reference, as shown above. After binding, its normal Paper APIs create real custom Runtime Item Stacks and expose type metadata:

```java
ItemStack stack = MyItems.HEALING_APPLE.createItemStack();
stack.getItemType().equals(MyItems.HEALING_APPLE); // true
stack.getType();                                  // Material.APPLE
```

Using an unbound Item Type reference to create a stack or query registered properties fails with a lifecycle error. `ItemType` requires no new parallel custom-type interface.

#### 4.3.4 Content System service

`ContentSystem` is a server-owned runtime service rather than a static utility singleton:

```java
ContentSystem contentSystem = Bukkit.getServer().getContentSystem();
```

The instance owns server-lifetime projection, recovery, diagnostics, cache invalidation, and registry-access behavior. Static nested providers under `ContentSystem.RegistryEvents` describe bootstrap registration hooks only; they do not own runtime state and remain usable before the runtime `Server` service is available. Their exact signatures are centralized in the consolidated API skeleton.

## 5. Runtime and Bukkit representations

A Runtime Item Stack remains a real custom stack in server memory:

```text
type: my_plugin:healing_apple
components:
  minecraft:food
  minecraft:consumable
  my_plugin:healing_strength
```

The Bukkit `ItemStack` is the single public stack abstraction for both vanilla and custom items. It remains backed by the internal Runtime/NMS Item Stack; Nix does not add a parallel `NixItemStack` facade or require conversions between stack APIs.

A custom stack needs a new registry-backed item-type accessor because the existing Material accessor remains a compatibility view:

```java
ItemStack stack = ...;

stack.getItemType().getKey();                  // my_plugin:healing_apple
stack.getType();                               // Material.APPLE compatibility
stack.getData(DataComponentTypes.FOOD);        // Vanilla Component
stack.getData(MyComponents.HEALING_STRENGTH);  // Custom Component
```

For a vanilla apple, `getItemType().getKey()` is `minecraft:apple` and `getType()` is also `Material.APPLE`. The proposed `getItemType()` returns Paper's existing registry-backed `ItemType`, which already implements `Keyed`; a separate bare key accessor is unnecessary.

Bukkit mutations operate directly on the Runtime Item Stack. Plugin-registered component types are ordinary Paper `DataComponentType.Valued<T>` or `DataComponentType.NonValued` instances, so the existing `getData`, `setData`, `hasData`, `unsetData`, and `resetData` methods work unchanged. Registration installs the internal NMS type, public Paper registry wrapper, and value adapter atomically.

Custom valued components follow Paper's existing immutable-value invariant. ItemStack/component-map copies may share value references, hash computation may cache their state, and `getData()` does not deep-copy arbitrary values. Component values, including nested collections, must therefore be deeply immutable with stable semantic `equals`/`hashCode`; updates replace the whole value through `setData()`. The Content System does not codec-round-trip every get/set, especially because transient components have no codec.

Calling Bukkit `ItemStack#setType(Material)` on a Custom Item Stack always converts its item type to the corresponding vanilla registry item, even when the argument equals the stack's compatibility `getType()`/Vanilla Material. This deliberately bypasses CraftItemStack's ordinary same-Material early return for custom types. Type-default Vanilla and Custom Components from the old Custom Item Type naturally disappear, while the stack's ordinary per-instance Vanilla and Custom Component patch is retained and reconciled against the new type prototype. The amount is preserved and `Material.AIR` clears the stack.

Bukkit `ItemStack#clone()` creates an independent Runtime Item Stack that retains the real Custom Item Type and both Vanilla and Custom Components. API types do not expose the internal NMS backing.

## 6. Client Projection

Client Projection occurs only at the outbound client boundary. It may vary by viewer, for example to account for resource-pack availability, but it must not vary by packet or display scenario for the same viewer. For a given viewer, Runtime Item Stack state, projection revision, and captured operation context, projection is deterministic.

The Vanilla Material supplies the base item identity. Effective Vanilla Components from the Runtime Item Stack are automatically reconciled against that base's default components:

```text
Runtime has component, base does not  → add it
Runtime lacks component, base has it  → add a removal patch entry
Both have equal values                → rely on the base default
Both have different values            → override with the runtime value
Custom Component                       → omit by default
```

This comparison must use effective components, not merely copy the custom stack's existing patch, because the custom type and Vanilla Material have different prototypes.

Example: if Bukkit removes `CONSUMABLE` from a healing apple whose Vanilla Material is `APPLE`, projection must include the equivalent of `!minecraft:consumable`; otherwise the client's apple prototype would incorrectly restore consumability.

Vanilla Components require no per-item projector code merely to survive the boundary. Explicit modifier logic is reserved for intentionally mapping custom semantics into a vanilla approximation that the client can express.

### 6.1 Projection modifiers

A Custom Item Type may explicitly bind a reusable modifier to a Custom Component Type. The modifier runs when that custom component has an effective value and mutates only the projected Vanilla Components; it does not mutate the Runtime Item Stack. The consolidated API skeleton is the sole definition of the modifier, context, source, and output interfaces.

Illustrative binding:

```java
builder
    .vanillaMaterial(Material.PAPER)
    .component(MyComponents.EVENT_ITEM_YEAR, 2026)
    .project(MyComponents.EVENT_ITEM_YEAR, EventItemProjections.YEAR_LORE);
```

A plugin-registered Data Component Type may also declare one default modifier in its Content System registration metadata so that it can project when attached to a vanilla item or to a Custom Item Type with no override:

```java
componentBuilder.defaultProjection(SoulboundProjections.DEFAULT_LORE);
```

For each effective Custom Component value, modifier resolution is exclusive and follows an item-definition tri-state:

```text
item-specific project(component, modifier)
  → use that override only

item-specific suppressProjection(component)
  → run no modifier

no item-specific setting
  → use the component registration's default when present
  → otherwise run no modifier
```

An item-specific binding overrides rather than composes with the component default. It remains useful when different Custom Item Types need different client representations of reusable state. Ordinary vanilla item types have no item-specific binding and therefore use the component default when present.

Data Component storage has no stable iteration-order contract. Projection therefore executes inherited component-default modifiers first in lexicographic Data Component Type key order, followed by item-specific modifiers in the explicit order in which the Custom Item Type builder binds them. Each modifier reads the output produced by earlier modifiers. A later `set` of the same component wins, while read-modify-write composition naturally preserves earlier output before adding later output. No numeric priority or global modifier ordering is used.

```java
.project(MyComponents.EVENT_ITEM_YEAR, YEAR_LORE)
.project(MyComponents.EVENT_EXPIRES_AT, EXPIRY_LORE)
```

If both modifiers read the current lore and append one line, the resulting order is canonical lore, year line, then expiry line.

Projection is a hot synchronous path used for outbound stacks and projected remote-state/hash comparison. Modifiers must be bounded, non-blocking, and free of I/O. Cheap allocation and in-memory lookups are acceptable; database, filesystem, network, waiting, and expensive computation are not. The same projected result should be reused for packet output and remote-state recording.

Each modifier executes in its own framework-managed transaction. `ProjectionOutput` records a small undo log containing the value of every Vanilla Component immediately before that modifier first writes it. If the modifier throws or its touched output fails validation, Nix restores those values, removes restoration entries introduced only by that failed modifier, reports the failure with rate limiting keyed to its item type and binding, and continues with later modifiers. Successfully committed earlier modifiers remain visible to later ones. Fatal VM failures are not part of this recovery contract.

```text
canonical output
  → modifier A commits
  → modifier B partially writes, then fails
  → roll back only modifier B
  → modifier C continues from modifier A's output
```

The transaction is internal to Nix; plugin code receives only `ProjectionOutput`. It does not require copying the whole projected stack. Blocking or non-terminating modifier code cannot be made safe by rollback and remains a plugin contract violation.

Client Projection includes the same `nix:item` Recovery Envelope used by the Persistent Form. This lets a full projected stack returned by the client, notably through creative-mode slot packets, recover its custom identity without a connection-scoped projection-token ledger. The client does not interpret the envelope; it only preserves and returns opaque `CUSTOM_DATA`.

Envelope encoding must be canonical and deterministic. Runtime stacks with equal custom identity and persistent Custom Component patches must produce equal envelope values, so the metadata does not prevent otherwise equal stacks from stacking. Different custom identities or persistent Custom Component state should remain unequal.

The projection builder automatically records the canonical value of each Vanilla Component immediately before the first modifier writes or unsets it. Client Projection extends `nix:item` with this minimal restoration patch:

```snbt
"nix:item": {
  item: "example_plugin:event_item",
  components: {
    "example_plugin:event_item_year": 2026
  },
  projection_restore: {
    "minecraft:lore": ["Original lore"]
  }
}
```

Later writes to the same component do not replace its recorded canonical value. If a complete projected stack returns through the creative-mode path, recovery applies the restoration patch so synthetic modifier output is not baked into the Runtime Item Stack. This avoids requiring an inverse function from each modifier. `projection_restore` is generated into Client Projection only; it is not part of the Persistent Form.

Nix does not keep this patch beside every server inventory item. In normal survival interactions the authoritative Runtime Item Stack never leaves server memory. In creative mode, the projected stack itself carries everything required for restoration. The only per-viewer server state needed is the ordinary ephemeral remote presentation state used for synchronization and hash comparison.

### 6.2 Current client-to-server authority model

The normal interaction model remains server-authoritative and does not require a general reverse-projection pass. In the current Minecraft 26.2 protocol, `ServerboundSetCreativeModeSlotPacket` is the only standard serverbound packet carrying a complete `ItemStack`. The server handles it only for players with infinite-material/creative authority. Survival inventory clicks return item identity, count, and component hashes through `HashedStack`; the server uses these to track and validate the client's remote view, not to replace authoritative slot contents. Item use and other survival interactions identify a hand, slot, target, or operation while the server reads the authoritative stack it already owns.

Consequently, an ordinary survival client cannot replace custom item state merely by editing the outbound Recovery Envelope. A creative client can return an edited envelope, but it can already construct arbitrary vanilla item stacks and components. Regardless of game mode, all returned envelope data is structurally untrusted and must pass normal recovery validation before use.

Nix does not sign or encrypt the envelope. Its contents are client-readable, and creative-authority clients may forge them. Custom item data must not serve as an authorization credential or contain secrets.

### 6.3 Projection-aware remote state

Container synchronization must compare the client's remote state against Client Projection, not against the Runtime Item Stack. `HashedStack` includes the item holder, count, hashes of added component values, and removed component types. A client therefore returns hashes for the Vanilla Material and projected component patch, which intentionally differ from the custom type and component patch retained by the server.

If `RemoteSlot` compares that hash directly with the Runtime Item Stack, it will repeatedly conclude that the client is out of sync and resend the slot. Packet output and remote slot/carried-item state must therefore share the same per-viewer projection service.

Projection does not need to run for every slot on every interaction. Vanilla `RemoteSlot` normally uses one full `remoteStack` both as the last server snapshot and as the expected client representation because those representations are identical. Nix separates those responsibilities:

```java
final class ProjectionAwareRemoteSlot {
    ItemStack lastRuntimeStack;       // canonical input used last time
    ItemStack remoteStack;            // exact projected stack sent/accepted
    long lastProjectionRevision;

    @Nullable HashedStack expectedRemoteHash; // computed lazily when useful
    @Nullable HashedStack clientClaim;
}
```

When sending a slot, the server projects once and uses that exact result for both the packet and `remoteStack`; it does not need to hash every outgoing stack eagerly. On a later synchronization check:

```text
runtime unchanged + viewer projection revision unchanged
  → remoteStack remains the expected client representation
  → compare a client claim against it, lazily hashing/caching if needed
  → do not run modifiers

runtime changed or projection revision changed
  → project the new runtime value once
  → compare it with remoteStack or the client claim
  → send only if the projected representation changed or the claim is wrong
  → update lastRuntimeStack, remoteStack, and the recorded revision
```

Client click hashes describe the client's predicted post-click state, so a hash cached for the pre-click outgoing stack cannot validate a slot whose authoritative state actually changed. Such changed slots require a new projection of the server's post-click Runtime Item Stack. A container click normally changes only a small number of slots and the cursor. Slots whose canonical state did not change can reuse `remoteStack` and its lazily computed expected hash. Minecraft's existing per-player component-value hash cache remains applicable.

A runtime change that produces an equal projection updates `lastRuntimeStack` without sending a packet. This matters for server-only or otherwise projection-irrelevant state.

Each player already owns separate container synchronization and `RemoteSlot` state, so per-viewer projection does not require tracking recovery patches for every inventory globally. It requires only per-visible-slot canonical/projected snapshots, optional hashes, and a per-viewer projection revision. This state is ephemeral and discarded with the menu or connection.

Context changes that do not mutate a Runtime Item Stack use explicit invalidation:

```java
Bukkit.getServer()
    .getContentSystem()
    .refreshItemProjections(Player viewer);
```

Refreshing increments the viewer's projection revision, invalidates projected remote state, and resynchronizes currently visible inventory, container, cursor, and equipment representations. Closed storage is not scanned; it projects normally when next viewed.

The architectural requirement is that packet encoding, projected hashing, remote-state comparison, and invalidation share one deterministic presentation service; projection cannot exist only as a last-moment `ItemStack` wire-codec rewrite.

## 7. Persistence and recovery

### 7.1 Persistent Form

Persistence is not Client Projection, although both use the same Vanilla Material identity.

Minecraft 26.2 encodes an `ItemStack` through a codec with `id`, `count`, and an optional `components` patch. With `NbtOps`, those fields are actual NBT tags. A persistent stack is conceptually:

```snbt
{
  id: "minecraft:apple",
  count: 1,
  components: {
    "minecraft:custom_name": "Healing Apple",
    "minecraft:custom_data": {
      "some_plugin:data": {...},
      "nix:item": {
        item: "my_plugin:healing_apple",
        components: {
          "my_plugin:charges": 2
        }
      }
    }
  }
}
```

The envelope field names and value encoding are internal persistence details rather than public API, but the reserved subtree contains:

- an optional Custom Item Type key;
- the persistent per-stack Custom Component patch.

Presence of the inner `item` field means the Persistent Form represents a Custom Item Type. Its absence means the outer vanilla item identity remains authoritative and only persistent Custom Components are being carried.

Type-default Custom Component values are not duplicated. This mirrors vanilla stack patch semantics and allows an updated type default to affect existing stacks that did not override it. A vanilla Item Stack carrying Custom Components omits the custom item identity field and persists only the persistent portion of its Custom Component patch.

Vanilla Components remain in the outer ordinary component patch and are authoritative on recovery. The envelope is authoritative only for custom identity and persistent Custom Component state. Nix does not store a projection baseline or perform a three-way merge.

Custom Components cannot remain as ordinary top-level entries in the Persistent Form. Without Nix their registry keys and codecs are unavailable, so vanilla cannot reliably decode the item component patch. Nesting their encoded values under the known `minecraft:custom_data` component keeps them opaque and vanilla-loadable.

The envelope occupies only the reserved `nix:item` subtree. Existing unrelated `CUSTOM_DATA`, including plugin data, must be preserved.

A persistent plugin-registered `DataComponentType.Valued<T>` supplies a public DataFixerUpper codec. `persistent(codec)` means its per-stack set/removal patch participates in `nix:item`; it does not mean the custom type is serialized as an outer vanilla component. A Non-Valued type uses no-argument `persistent()` because Nix only needs to encode marker presence/removal. Without the matching persistence call, either component kind is transient and omitted from Persistent Form and Client Projection recovery metadata. Its per-stack value, marker, or removal patch is lost across save/load and creative round-trips, although a Custom Item Type's transient default naturally reappears when that type is reconstructed.

### 7.2 Recovery

Conceptual recovery flow:

```text
1. Decode the outer vanilla item identity and Vanilla Components normally.
2. Look for CUSTOM_DATA["nix:item"].
3. Validate envelope shape, key syntax, and size limits.
4. If a custom item identity is present, resolve the Custom Item Type; otherwise retain the decoded vanilla type.
5. Validate and decode every present Custom Component patch entry.
6. If all required data is resolvable, construct or retain the selected Runtime Item Stack type.
7. Transfer the authoritative outer Vanilla Component state.
8. Apply the Custom Component patch as top-level runtime components.
9. Remove only the reserved recovery subtree from runtime CUSTOM_DATA.
```

A missing envelope `components` field or an omitted custom component is valid and means an empty/no override patch. It does not block recovery.

Recovery is all-or-nothing when data is present but cannot be interpreted safely. Any of the following leaves the stack as a vanilla fallback for that load:

- the Custom Item Type is not registered;
- a present Custom Component Type is not registered;
- a present custom value fails to decode;
- the envelope is malformed.

The fallback retains the complete Recovery Envelope. Nix must not partially recover and then overwrite undecodable state on the next save. Diagnostics should be rate-limited and must not mutate the item.

An unresolved fallback retains `nix:item` when sent to a client as well. This preserves future recoverability if a full client-side copy returns to the server.

## 8. Consolidated decisions

### 8.1 Representation, compatibility, and projection

1. One Vanilla Material is shared by Bukkit, Client Projection, and Persistent Form.
2. Vanilla Components are persisted through Minecraft's ordinary component patch; only custom identity and the persistent per-stack Custom Component patch enter the Recovery Envelope.
3. There is no persistence projection baseline or three-way merge.
4. Content registration reuses Paper `PluginBootstrap`; `onLoad()` cannot register new types.
5. Registered content is immutable for the server run.
6. Recovery is all-or-nothing for present but unknown or invalid custom component data.
7. Client Projection carries the deterministic Recovery Envelope instead of using connection-scoped projection tokens.
8. Projection may vary by viewer but not by packet/display scenario for the same viewer.
9. Projection resolution selects at most one modifier per effective Custom Component: an item-specific override, explicit item-specific suppression, otherwise the component registration's default; modifiers are synchronous, bounded, and non-blocking.
10. Component-default modifiers execute first in lexicographic component-key order, followed by item-specific modifiers in explicit item-builder binding order; later direct writes win.
11. A failing Projection Modifier is transactionally rolled back in isolation, rate-limited in diagnostics, and does not prevent later modifiers from running.
12. Client Projection carries an automatically captured restoration patch for Vanilla Components touched by modifiers; no per-inventory recovery side table or modifier inverse function is required.
13. Remote synchronization records separate per-viewer canonical input and projected `remoteStack` snapshots, with expected hashes computed lazily when useful.
14. Modifiers re-run only when canonical runtime state changes or projection context is invalidated; a changed canonical state that projects identically does not send a packet.
15. Plugins explicitly refresh a player's projections when external or viewer context changes without mutating the Runtime Item Stack.
16. Recovery Envelopes are not signed or encrypted; they are client-readable and untrusted.
17. Bukkit `ItemStack` is the sole public stack abstraction for vanilla and custom items; there is no separate `NixItemStack` facade.
18. Custom stacks expose their actual registry-backed `ItemType` separately from the compatibility `Material`, and Bukkit `clone()` preserves custom identity in an independent Runtime Item Stack.

### 8.2 API and registration

19. Fork-specific registration providers are grouped under `ContentSystem.RegistryEvents` rather than branded as `NixRegistryEvents`, while registered values use Paper API types.
20. `ContentSystem` is a single server-owned runtime service exposed by `Server#getContentSystem()`; its nested registry event providers are static bootstrap descriptors only.
21. Any Bukkit `setType(Material)` call on a custom stack selects that vanilla registry item, including when the Material equals its compatibility Vanilla Material; old type defaults disappear and the per-stack Vanilla/Custom Component patch remains.
22. Custom Components are orthogonal to Custom Item Types and may be attached to ordinary vanilla Item Stacks; their durable form uses the same Recovery Envelope without a custom item identity.
23. The Recovery Envelope is stored at `minecraft:custom_data["nix:item"]`; an inner `item` field is present only for a Custom Item Type.
24. Both valued and non-valued Custom Components are transient by default; valued types opt into persistence with `persistent(codec)`, marker types with `persistent()`, and only persistent per-stack patches enter the Recovery Envelope.
25. Custom components reuse Paper's existing `DataComponentType` registry wrappers and Bukkit `ItemStack` methods; there is no parallel `CustomDataComponentType` hierarchy or stack-method overload set.
26. Content System additions live under `club.plutoproject.nix.contentsystem`, while persistent component registrations directly accept DataFixerUpper `Codec<T>`; the Nix API provides the exact DFU version used by its pinned server rather than wrapping it.
27. Only `ContentSystem.RegistryEvents.ITEM` and `.DATA_COMPONENT_TYPE` expose bootstrap writes; the underlying NMS registries receive the entries while Paper registry APIs remain read-only views.
28. Plugins predeclare typed key-backed Paper `DataComponentType` references and register those same references during bootstrap, preserving valued-component generics without casts or nullable static assignment.
29. Custom Item Types use the same predeclared key-backed Paper `ItemType` reference model; no parallel custom item type API is introduced.
30. Custom valued components preserve Paper's existing deeply immutable value invariant; mutation replaces the value through `setData()` rather than mutating an object returned by `getData()`.
31. Content registration does not declare or enforce per-plugin namespace ownership; plugins may coordinate arbitrary namespaces, while `minecraft` and `nix` are reserved and duplicate keys fail bootstrap.
32. `vanillaMaterial` controls compatibility representations only and does not inherit that vanilla item's default components into the Runtime Custom Item Type.

### 8.3 Gameplay hooks

33. Custom Item behavior uses a stable typed hook dispatch table on the Item Type registration builder rather than a `CustomItem` class; the internal NMS bridge delegates without exposing NMS classes.
34. `addHook` sets one callback per hook and later calls replace earlier ones; missing hooks run vanilla defaults, while every explicit context default call executes again like a Java `super` call.
35. `ItemHooks` is a curated stable gameplay surface rather than a one-to-one mirror of version-specific NMS `Item` methods; client presentation belongs to components and projection.
36. The first hook catalog covers interaction, active use, mining, combat, item lifecycle, and container-fit capability; slot stacking and custom damage-source selection are deferred.
37. `ON_USE` exposes player, live authoritative stack, and Bukkit hand; plugin-created interaction success always uses a server swing, while an unchanged default result preserves vanilla's internal swing semantics.
38. `ItemUseResult` extends `ItemInteractionResult` and alone exposes an immediately copied transformed held stack for `ON_USE`; other interaction hooks return the base result type.
39. `ON_USE_ON_BLOCK` shares a held-item context with `ON_USE`, adds live block, face, copied hit location, inside/border/secondary-use flags, and supports try-empty-hand continuation.
40. `ON_USE_TICK` exposes `LivingEntity`, live active stack, hand, pre-decrement remaining ticks, and used ticks, while retaining vanilla component precedence: server-side `KINETIC_WEAPON` replaces the Item hook.
41. `ON_FINISH_USE` directly returns a non-null final Bukkit stack; external results are snapshotted and vanilla remainder/cooldown side effects run afterwards.
42. `ON_RELEASE_USE` exposes the entity, live stack, hand, and tick counters and returns an explicit apply/skip-after-use-effects enum rather than NMS's opaque boolean.
43. `CAN_DESTROY_BLOCK` preserves the built-in Item and Paper event behavior rather than becoming an absolute veto; client-visible static restrictions belong in the `TOOL` component.
44. `DESTROY_SPEED` uses stack plus captured block data and a finite non-negative base float; later player modifiers remain vanilla and static client-visible rules use `TOOL`.
45. `IS_CORRECT_TOOL_FOR_DROPS` reuses the stack/block-data query context and returns boolean affecting both loot eligibility and destroy-progress divisor.
46. `ON_MINE_BLOCK` receives player, live main-hand stack, location and pre-removal block-data snapshots, and returns an explicit award/skip-item-used-stat result after removal.
47. `ATTACK_DAMAGE_BONUS` adds a compatible enriched Item overload and two call-site changes so hooks receive exact weapon, attacker, victim, pre-bonus damage, and Bukkit damage source; finite negative bonuses are allowed.
48. Combat contexts consistently call the attacked entity `victim`; `ON_HURT_ENTITY` and `AFTER_HURT_ENTITY` share attacker/victim/live-weapon context and preserve vanilla success, component, and Mob-path conditions.
49. `INVENTORY_TICK` uses `entity()` rather than owner, exposes live stack and nullable equipment slot, and is a server-only low-cost hot callback.
50. `ON_CRAFTED` covers player and automated Crafter paths through live result, positive amount, and nullable player, with exactly one hook invocation and the matching built-in default.
51. `ON_DESTROYED_AS_ITEM_ENTITY` adds a compatible damage-source overload and exposes the dying item entity, live stack, and source without making destruction cancellable.
52. `CAN_FIT_INSIDE_CONTAINER_ITEMS` adds a compatible stack-aware overload and updates both callers so a boolean capability can read per-stack components.
53. Query hooks use live context objects under a documented read-only, repeatable, low-cost contract; implementation does not copy objects or detect mutations.
54. Non-fatal callback failures use a rate-limited default fallback without action rollback or permanent disablement; the last completed default is reused, or one default is run if none completed, while uncaught default failure propagates and is never retried.
55. Gameplay hooks guarantee built-in Item call-site parity, not a separate Nix event schedule; enriched overloads preserve timing, crafting keeps each source entry point, and callbacks remain synchronous.
56. All descriptors use `ItemHook<C, R>`; result handlers use the generic callback, while `R = Void` binds `Consumer<C>` through an `addHook` overload, and primitive specialization awaits profiling.
57. Default behavior calls are not once-guarded; each invocation executes again, matching repeated Java `super` calls.
58. Contexts are callback-scoped by contract only; Nix adds no runtime invalidation or delayed-default-call handling.
59. Registry entries and their codecs, projection modifiers, and gameplay handlers remain active until server shutdown even if the registering plugin is disabled; hot unload is unsupported.
60. Hook tables are fixed by the bootstrap Item Type builder; runtime set/remove/rebind APIs are absent, while plugin-owned service indirection remains the plugin's responsibility.

### 8.4 Projection and persistence API

61. Projection modifiers mirror Paper component kinds as sibling `ProjectionModifier.Valued<T>` and `.NonValued` functional interfaces.
62. Both component kinds default transient; valued persistence requires a DFU codec, while non-valued persistence uses an explicit no-argument marker method.
