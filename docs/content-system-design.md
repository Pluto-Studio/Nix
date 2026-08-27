# Nix content system

## Contents

- [1. Scope, goals, and boundaries](#1-scope-goals-and-boundaries)
  - [1.1 Scope](#11-scope)
  - [1.2 Goals](#12-goals)
  - [1.3 Compatibility model](#13-compatibility-model)
- [2. Domain model and terminology](#2-domain-model-and-terminology)
  - [2.1 Terms](#21-terms)
- [3. Runtime item model](#3-runtime-item-model)
  - [3.1 Minecraft component model](#31-minecraft-component-model)
  - [3.2 Gameplay hooks](#32-gameplay-hooks)
- [4. Public API](#4-public-api)
  - [4.1 Public API sketch](#41-public-api-sketch)
  - [4.2 Public API namespace](#42-public-api-namespace)
  - [4.3 Registration lifecycle](#43-registration-lifecycle)
- [5. Runtime and Bukkit representations](#5-runtime-and-bukkit-representations)
- [6. Client projection](#6-client-projection)
  - [6.1 Projection modifiers](#61-projection-modifiers)
  - [6.2 Current client authority model](#62-current-client-authority-model)
  - [6.3 Projection-aware remote state](#63-projection-aware-remote-state)
- [7. Persistence and recovery](#7-persistence-and-recovery)
  - [7.1 Persistent form](#71-persistent-form)
  - [7.2 Recovery](#72-recovery)
- [8. Decision summary](#8-decision-summary)

## 1. Scope, goals, and boundaries

### 1.1 Scope

The Content System extends items only. Blocks and other content types are out of scope.

### 1.2 Goals

Plugins register namespaced Custom Item Types in the real server item registry. While Nix runs, stacks retain their type and can hold both vanilla and plugin-defined Data Components. The result must still work with unmodified Minecraft clients, ordinary Bukkit plugins, and worlds opened without Nix.

### 1.3 Compatibility model

These are three different compatibility boundaries, not three projections:

```text
Runtime Item Stack (authoritative)
  ├─ Bukkit View
  ├─ Client Projection
  └─ Persistent Form
```

## 2. Domain model and terminology

### 2.1 Terms

#### 2.1.1 Custom Item Type

A plugin-owned namespaced item type registered as a distinct server-side type for one server run.

#### 2.1.2 Runtime Item Stack

The in-memory source of truth. It retains its Custom Item Type and all effective vanilla and custom component state.

#### 2.1.3 Vanilla Material

The `org.bukkit.Material` that a Custom Item Type uses for the Bukkit type, Client Projection base, and Persistent Form identity.

#### 2.1.4 Vanilla Component

A Data Component whose type and semantics are understood by an unmodified Minecraft client.

#### 2.1.5 Custom Component

A plugin-registered Paper `DataComponentType` used for server-side state. It may be attached to either a vanilla or Custom Item Stack. An unmodified client does not understand its type or value. Custom types use Paper's existing `DataComponentType.Valued<T>` or `DataComponentType.NonValued` and the existing Bukkit `ItemStack` component methods; there is no parallel `CustomDataComponentType` API.

#### 2.1.6 Bukkit View

The compatibility view of a Runtime Item Stack. `getType()` reports a Custom Item Type as its Vanilla Material, while `getItemType()` keeps its custom identity available. Paper's Data Component API exposes Vanilla and Custom Components. This is not Client Projection.

#### 2.1.7 Client Projection

A temporary vanilla-compatible stack sent to an unmodified client. Custom Components are omitted as top-level types. Their persistent patch and the custom identity travel as opaque `nix:item` recovery metadata.

#### 2.1.8 Persistent Form

A vanilla-loadable stack written outside process memory. Vanilla Components use Minecraft's normal item-component representation. Custom identity and the persistent per-stack Custom Component patch use a Recovery Envelope.

#### 2.1.9 Recovery Envelope

Opaque Nix metadata under `minecraft:custom_data` in a Persistent Form or Client Projection. A successfully recovered custom Runtime Item Stack does not retain it. Client Projection may add restoration data that exists only for projection.

#### 2.1.10 Vanilla fallback

A vanilla stack that uses the Custom Item Type's Vanilla Material and the Vanilla Components from its Persistent Form. Nix uses this form for the current load when it cannot safely resolve the custom type or any present custom component. It keeps the full Recovery Envelope for a later load.

## 3. Runtime item model

### 3.1 Minecraft component model

Modern Minecraft item behavior is a combination of the `Item` implementation, type-default Data Components, and the per-stack component patch:

```text
Item implementation
  + type-default components (prototype)
  + per-stack component patch
  = effective stack behavior and state
```

Data Components are more than arbitrary replacements for legacy item NBT. Some describe data. Others control behavior. In the current Minecraft 26.2 sources:

- `minecraft:apple` has default `FOOD` and `CONSUMABLE` components.
- `minecraft:diamond_sword` has default `TOOL`, `WEAPON`, attributes, durability, repair, and enchantability components.
- Removing `CONSUMABLE` from an apple's stack patch makes the effective stack non-consumable.

Use vanilla components when they accurately express server-side behavior. Reserve Custom Components for server-only state or behavior that vanilla cannot represent.

Vanilla Material only controls representation. It does not donate its type-default components to the Runtime Custom Item Type. The custom type starts with Minecraft's generic item defaults and the defaults declared by its registration builder. Choosing `DIAMOND_SWORD`, for example, does not add `TOOL`, `WEAPON`, durability, attributes, repair, or enchantability. Client Projection compares the explicit runtime state with the Vanilla Material prototype and removes vanilla defaults that are absent at runtime.

### 3.2 Gameplay hooks

Every Custom Item Type is represented in the NMS item registry by a Nix-owned `ContentSystemItem extends Item`. The instance is immutable after registration and owns the type-level state that vanilla ItemStack does not carry: its Vanilla Material compatibility mapping, item-specific projection override/suppression configuration and ordering, immutable gameplay-hook table, and registrant diagnostic identity. Type-default Vanilla and Custom Components remain in the ordinary inherited Item component prototype. Component-level codecs and default Projection Modifiers remain global Data Component Type metadata because Custom Components can also be attached to vanilla items. No per-stack state is stored on `ContentSystemItem`.

The registration builder accepts stable typed hooks. Their contexts and results use Bukkit, Paper, and Content System types, never NMS. `ContentSystemItem` overrides the matching NMS Item virtual methods and dispatches to its immutable hook table. Use Vanilla Components for behavior they already model. Use hooks when code is needed.

```java
builder.addHook(ItemHooks.ON_USE, context -> {
    ItemInteractionResult result = context.defaultBehavior();
    // Extend the component-driven default behavior.
    return result;
});
```

#### 3.2.1 Hook registration and callback model

`addHook` behaves like a map, not a list. An Item Type has one callback per hook descriptor, and a later call replaces it. With no callback, the bridge item runs its vanilla default behavior. A callback can extend that behavior through `defaultBehavior()` or `runDefaultBehavior()`, or replace it by skipping the default. Like repeated Java `super` calls, each explicit default call runs the underlying behavior again. Content System does not prevent it.

Hook callbacks are shared for the registered Item Type's lifetime. They may capture immutable configuration and plugin service references. They must not capture mutable per-stack, per-player, or per-invocation state. Put per-stack state in Data Components.

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

Slot-stacking overrides and custom damage-source selection wait for stable transaction and damage APIs. There are no client-presentation hooks for name, tooltip, glint, durability bar, or use animation. Use Vanilla Components and Projection Modifiers for those. Type properties such as crafting remainder stay on the registration builder rather than becoming callbacks.

#### 3.2.3 Interaction hooks

The three held-item interaction hooks share the authoritative player, live Bukkit stack, and Bukkit `EquipmentSlot.HAND` or `OFF_HAND` through `HeldItemContext`, but can return different result types. The API sketch defines their interfaces.

`ItemInteractionResult` provides pass, fail, handled success, handled-without-swing consume, and try-empty-hand outcomes. A plugin-created `success()` always maps to server-swing success; Content System does not expose construction of NMS client-swing success because an unmodified client may not predict custom behavior. A result obtained from `defaultBehavior()` retains exact underlying vanilla swing semantics when propagated unchanged. `tryEmptyHand()` is valid for the block-use pipeline.

`ItemUseResult` extends `ItemInteractionResult`, so ON_USE handlers have the usual result operations without a wrapper layer. It adds immutable `success(ItemStack)` and `consume(ItemStack)` overloads for the one server path that consumes a transformed held stack.

The replacement is copied immediately and can be vanilla or custom. ON_USE_ON_BLOCK and ON_INTERACT_LIVING_ENTITY return the base result type, so they have no transformation factories. In-place changes to a live context stack need no transformation result. The API does not construct `withoutItemInteraction()`, although an unchanged default result can retain that internal vanilla metadata.

`ON_USE_ON_BLOCK`'s `tryEmptyHand()` asks the interaction pipeline to continue with the clicked block's empty-hand behavior. Its `clickedBlock()` is live, while `interactionPoint()` is a defensive copy whose mutation cannot change the actual hit result.

#### 3.2.4 Active-use hooks

`ON_USE_TICK` exposes the Bukkit `LivingEntity`, including entities that are not players, the live active stack, active hand, exact pre-decrement remaining ticks, and elapsed used ticks. It is a void callback with a callable default operation. It stays at vanilla's `Item#onUseTick` position. Consumable particles and sounds run first. On the server, a present `KINETIC_WEAPON` component runs instead of the Item hook, so Nix skips `ON_USE_TICK` for that tick.

`ON_FINISH_USE` runs when an active use completes normally. Its context contains the `LivingEntity`, live active stack, and hand, and its default operation returns the generic Item's component-driven finish result. The callback directly returns a non-null Bukkit `ItemStack`: the context stack keeps in-place changes, another returned stack is snapshotted before installation, and `ItemStack.empty()` consumes it completely. The outer ItemStack pipeline applies `USE_REMAINDER` and use-cooldown component side effects after the hook result.

`ON_RELEASE_USE` runs when active use is released or interrupted. It exposes the `LivingEntity`, live active stack, hand, remaining ticks, and used ticks. `ItemReleaseUseResult` replaces NMS's opaque boolean. `APPLY_AFTER_USE_EFFECTS` applies `USE_REMAINDER` and cooldown after the callback. `SKIP_AFTER_USE_EFFECTS` does not. Both end active use.

#### 3.2.5 Mining and block capability hooks

`CAN_DESTROY_BLOCK` mirrors the built-in Item capability. It receives a `LivingEntity`, live stack, live Bukkit block, and captured `BlockData`, then returns boolean. It is not a security boundary or a correct-tool-for-drops test. Nix keeps CraftBukkit and Paper behavior. `false` pre-cancels `BlockBreakEvent`, and another plugin may un-cancel it. Dynamic server-only answers can roll back client prediction. Encode a static creative-breaking restriction in projected vanilla `TOOL` when the client needs to see it.

`DESTROY_SPEED` receives the live stack and captured `BlockData`, matching the built-in Item query, and returns a finite, non-negative base float. Player attributes, effects, water, and airborne modifiers apply afterwards. Invalid results reuse the last successfully completed default result. If the callback completed none, Nix runs the default once. Put static client-visible mining rules in projected `TOOL`. Dynamic server-only speed can diverge from client prediction.

`IS_CORRECT_TOOL_FOR_DROPS` reuses the same stack-plus-captured-block-data context and directly returns boolean. The answer governs both correct-tool loot eligibility and vanilla's `/30` versus `/100` destroy-progress divisor. It has no player, world, or position because the built-in Item query has none; static client-visible rules again belong in `TOOL`.

`ON_MINE_BLOCK` runs after normal block removal. It receives the player, live main-hand stack, a copied location, and pre-removal `BlockData`. It does not expose a live block, which is usually air by then. Its result only chooses whether to award the item-used statistic. The default applies `TOOL.damagePerBlock` durability behavior and returns the matching statistic outcome. Neither result changes removal or loot eligibility.

#### 3.2.6 Combat hooks

`ATTACK_DAMAGE_BONUS` receives the attacker, victim, exact live weapon stack, damage-before-bonus, and Bukkit `DamageSource`, and returns a finite additive float; negative bonuses are valid. Nix adds a source-compatible Item overload carrying stack and attacker and changes the Player and Mob call sites to use it. The overload delegates to the existing vanilla signature, preserving existing subclasses and calculation order, while the custom bridge can read per-stack components.

`ON_HURT_ENTITY` and `AFTER_HURT_ENTITY` share attacker, victim, and live weapon context and are void callbacks after successful damage to a living victim. The first runs before enchantment post-attack effects. The latter runs afterwards and before `WEAPON.itemDamagePerAttack`, but only in vanilla paths that invoke it: the stack must have `WEAPON`, and the current Mob attack path does not call it. Failed or event-cancelled damage calls neither hook. Combat APIs consistently name the attacked entity `victim`; non-attack APIs such as living-entity interaction retain `target`.

#### 3.2.7 Lifecycle and container hooks

`INVENTORY_TICK` exposes the owning Bukkit `Entity` as `entity()`, the live stack, and a nullable equipment slot. `null` means ordinary inventory storage. It does not invent an inventory index. This server-only callback runs once per tick, so keep it cheap, non-blocking, and free of I/O.

`ON_CRAFTED` unifies player crafting and automated Crafter post-processing. It receives the live result stack, a positive crafted amount, and nullable player (`null` means automated). Mutations affect the delivered or dispensed result. Each path's default delegates its corresponding built-in method. The implementation normalizes dispersed call sites and suppresses the player method's internal post-process delegation from causing a duplicate hook invocation.

`ON_DESTROYED_AS_ITEM_ENTITY` is a void callback only when a dropped Bukkit `Item` dies from uncancelled damage, not despawn, pickup, merge, or command removal. It receives the not-yet-discarded entity, its live stack, and Bukkit `DamageSource`; destruction remains non-cancellable and discard follows the callback. Nix adds a compatible enriched Item overload that delegates to the old signature, preserving vanilla `BundleItem` and `BlockItem` overrides.

`CAN_FIT_INSIDE_CONTAINER_ITEMS` uses a stack-only result context and returns boolean. Nix adds a compatible stack-aware Item overload and changes the bundle and container-item-slot callers, allowing per-stack Custom Components while delegating vanilla items to the original type-level method. Dynamic server-only answers may require inventory resynchronization because the unmodified client evaluates its projected vanilla item.

#### 3.2.8 Execution and failure policy

Capability and numeric query hooks (`CAN_DESTROY_BLOCK`, `DESTROY_SPEED`, `IS_CORRECT_TOOL_FOR_DROPS`, `ATTACK_DAMAGE_BONUS`, and `CAN_FIT_INSIDE_CONTAINER_ITEMS`) receive live objects but are read-only by contract. Callbacks are synchronous, cheap, non-blocking, and free of I/O and side effects. They cannot assume a single evaluation. Nix documents this rule but does not copy objects, detect mutations, or roll them back. Put side effects in action hooks or Bukkit events.

A non-fatal plugin callback exception or invalid callback result is rate-limited by Item/hook/registrant and falls back to the default behavior. If the callback completed one or more default calls, the last successfully completed default result is reused rather than adding another execution; otherwise fallback executes the default once. Action side effects performed before failure are not rolled back. An uncaught failure from the default behavior itself is distinguished from callback failure, is never retried or disguised as plugin fallback, and propagates as an internal server exception. One callback failure does not permanently disable it. Registrant identity is diagnostic metadata, not namespace ownership.

Gameplay hooks run synchronously through `ContentSystemItem` at the corresponding built-in Paper/Minecraft Item virtual call and do not schedule, advance, delay, duplicate, or reorder Bukkit/Paper events. Call sites do not contain Content System type checks. Where an existing Item signature lacks required context, Nix adds a source-compatible enriched virtual overload: vanilla Item delegates to the old signature, the relevant caller invokes the enriched overload at the same position, and `ContentSystemItem` overrides it. Event cancellation and state mutation therefore affect hook reachability and context exactly as they affect that built-in method. `ON_CRAFTED` intentionally unifies player and automated entry points, but each remains at its respective built-in crafting callback. Event-order examples document the inspected pinned version rather than define a separate Nix compatibility schedule; an upstream event move relative to the Item call is inherited. Callbacks never auto-hop unsupported asynchronous plugin calls onto the server thread and must not block it.

#### 3.2.9 Handler typing and context lifetime

All hook descriptors use `ItemHook<C, R>`, with `ItemHookHandler<C, R>` for result callbacks. Void descriptors use `R = Void`, while Java callers use a normal void lambda through `addHook(ItemHook<C, Void>, Consumer<C>)`. An internal adapter supplies the null `Void` value. Other results use the generic overload and remain compile-time checked. Boolean and float results may box. Do not specialize them unless profiling shows a real cost.

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

## 4. Public API

### 4.1 Public API sketch

#### 4.1.1 Registry and component builders

This pseudo-Java shows the public type relationships and method shapes. It leaves out routine nullness, contract, and API-status annotations. Registry writes use Paper's existing lifecycle-handler and compose/register/builder flow with `TypedKey` registration.

```java
public interface ContentSystem {
    void refreshItemProjections(Player viewer);
}

public final class RegistryEvents {
    public static final RegistryEventProvider<ItemType, ItemTypeRegistryEntry.Builder> ITEM;
    public static final RegistryEventProvider<DataComponentType, DataComponentTypeRegistryEntry.Builder> DATA_COMPONENT_TYPE;
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
        // A kind must be selected; repeated selections use the last call.
        // Components are transient unless the matching persistence method is called.
        Builder valued();
        Builder nonValued();
        <T> Builder persistent(Codec<T> codec); // valued
        Builder persistent();                  // non-valued marker
        <T> Builder defaultProjection(ProjectionModifier.Valued<T> modifier);
        Builder defaultProjection(ProjectionModifier.NonValued modifier);
    }
}
```

#### 4.1.2 Typed keys and registration example

Custom Components are transient by default. A Valued Custom Component becomes persistent through `persistent(codec)`. A Non-Valued Custom Component becomes persistent through no-argument `persistent()`; its presence/removal needs no plugin codec and is represented directly in the Recovery Envelope. Component registration must select a kind with `valued()` or `nonValued()`; repeated kind selections use the builder-wide last-write-wins rule.

Registration keys use Paper's existing `RegistryKey#typedKey` factory rather than the private helpers inside generated vanilla key-holder classes:

```java
public final class MyComponents {
    public static final TypedKey<DataComponentType> SOULBOUND =
        RegistryKey.DATA_COMPONENT_TYPE.typedKey(Key.key("example:soulbound"));
    public static final TypedKey<DataComponentType> UNTRADEABLE =
        RegistryKey.DATA_COMPONENT_TYPE.typedKey(Key.key("example:untradeable"));

    @SuppressWarnings("unchecked")
    public static DataComponentType.Valued<Soulbound> soulbound() {
        DataComponentType type = Registry.DATA_COMPONENT_TYPE.getOrThrow(SOULBOUND);
        if (!(type instanceof DataComponentType.Valued<?> valued)) {
            throw new IllegalStateException();
        }
        return (DataComponentType.Valued<Soulbound>) valued;
    }

    public static DataComponentType.NonValued untradeable() {
        DataComponentType type = Registry.DATA_COMPONENT_TYPE.getOrThrow(UNTRADEABLE);
        if (!(type instanceof DataComponentType.NonValued marker)) {
            throw new IllegalStateException();
        }
        return marker;
    }
}

public final class MyProjections {
    public static final ProjectionModifier.Valued<Soulbound> SOULBOUND =
        (context, value, output) -> { /* project value */ };
    public static final ProjectionModifier.NonValued UNTRADEABLE =
        (context, output) -> { /* project marker */ };
}

public final class MyItems {
    public static final TypedKey<ItemType> HEALING_APPLE =
        RegistryKey.ITEM.typedKey(Key.key("example:healing_apple"));

    public static ItemType healingApple() {
        return Registry.ITEM.getOrThrow(HEALING_APPLE);
    }
}

bootstrapContext.getLifecycleManager().registerEventHandler(
    RegistryEvents.DATA_COMPONENT_TYPE.compose(),
    event -> {
        event.registry().register(MyComponents.SOULBOUND, builder -> builder
            .valued()
            .persistent(Soulbound.CODEC)
            .defaultProjection(MyProjections.SOULBOUND));
        event.registry().register(MyComponents.UNTRADEABLE, builder -> builder
            .nonValued()
            .persistent()
            .defaultProjection(MyProjections.UNTRADEABLE));
    }
);

bootstrapContext.getLifecycleManager().registerEventHandler(
    RegistryEvents.ITEM.compose(),
    event -> event.registry().register(MyItems.HEALING_APPLE, builder -> builder
        .vanillaMaterial(Material.APPLE)
        .component(DataComponentTypes.FOOD, HEALING_FOOD)
        .component(DataComponentTypes.CONSUMABLE, HEALING_CONSUMABLE)
        .component(MyComponents.untradeable())
        .project(MyComponents.soulbound(), MyProjections.SOULBOUND)
        .addHook(ItemHooks.ON_USE, context -> ItemUseResult.success()))
);
```

#### 4.1.3 Hook descriptors and default behavior

Hook descriptors and default behavior:

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

#### 4.1.5 Active use, mining, combat, lifecycle, and capability contexts

Active use, mining, combat, lifecycle, and capability contexts:

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

#### 4.1.7 ItemStack Item Type API

Item Type additions and retained Material compatibility methods:

```java
public class ItemStack {
    static ItemStack of(Material type);
    static ItemStack of(Material type, int amount);
    static ItemStack of(ItemType type);
    static ItemStack of(ItemType type, int amount);

    Material getType();
    ItemType getItemType();

    @Deprecated
    void setType(Material type);

    ItemStack withType(Material type);
    ItemStack withType(ItemType type);
}
```

There is no `setType(ItemType)` overload because the mutating `setType` API is already deprecated. Existing non-deprecated Material signatures remain supported and are not newly deprecated merely because an Item Type counterpart exists.

### 4.2 Public API namespace

Content System additions and their server implementations use this project-owned package:

```java
club.plutoproject.nix.contentsystem
```

This includes `ContentSystem`, custom registry-entry APIs, and projection APIs. Bukkit and Paper classes gain methods that refer to these types where needed. Content System types do not move into upstream-owned packages just to avoid imports.

### 4.3 Registration lifecycle

Register Custom Item Types and Custom Component Types during Paper's existing `PluginBootstrap` lifecycle. Nix has no second bootstrap phase.

Paper runs plugin bootstrappers before built-in registries are frozen. `JavaPlugin#onLoad()` occurs later: worlds are not yet prepared, but the item and Data Component Type registries are already frozen and datapack loading has occurred. Nix will not reopen those registries during `onLoad()`.

Registration stays fixed for the rest of the server run. Disabling or reloading a plugin does not unregister its types. Bootstrap content remains live for the server lifetime. Custom Component codecs, Projection Modifiers, and gameplay handlers can still run after the registering plugin is disabled. Paper removes lifecycle registration handlers on disable, but does not remove entries from the loaded frozen registry. An ordinary datapack reload does not rebuild Nix's static Item and Data Component Type registries. If a plugin tears down a service that a callback still uses, normal callback failure handling applies.

Paper exposes only two Content System write points:

```java
RegistryEvents.ITEM
RegistryEvents.DATA_COMPONENT_TYPE
```

Both are `RegistryEventProvider` instances with Paper's add-only registry support. Their compose handlers call `WritableRegistry.register(TypedKey<T>, Consumer<B>)` synchronously in Paper lifecycle priority order, before the matching static registry freezes. They cannot mutate vanilla definitions or entries another handler is registering. Plugins create keys with `RegistryKey.ITEM.typedKey(key)` and `RegistryKey.DATA_COMPONENT_TYPE.typedKey(key)`. The private `create` helpers in generated vanilla key-holder classes are not API.

The providers write to the real NMS `ITEM` and `DATA_COMPONENT_TYPE` registries during bootstrap. Paper's `Registry<ItemType>` and `Registry<DataComponentType>` stay read-only query views over the resulting values. Component registration atomically installs the NMS type, Paper value adapter, and Content System persistence and projection metadata.

The Content System neither adds a namespace field to the plugin descriptor nor infers ownership from the registering plugin. Plugins can share a namespace. `minecraft` remains reserved for Mojang content and `nix` for Content System internals. Other syntactically valid namespaces are allowed. Duplicate keys fail bootstrap registration. Registration after freeze is rejected. A Custom Item Type must provide a Vanilla Material. There is no implicit default.

Registration is atomic. A failed Item Type registration leaves no NMS item, Paper wrapper, or Content System item metadata. A failed Data Component Type registration leaves no NMS component type, Paper adapter, persistence metadata, or default Projection Modifier. An exception thrown by the builder consumer propagates out of registration without committing the entry. Once the cause is corrected, the same key may be registered successfully in the same mutable registration phase.

Registration builders use setter semantics: repeated component-kind selection, `vanillaMaterial`, type-default `component`, valued `persistent(codec)`, and `defaultProjection` calls retain the last supplied value for that setting. Projection override/suppression replacement and ordering are defined separately by the projection contract.

Illustrative registration shape:

```java
public final class MyBootstrap implements PluginBootstrap {
    @Override
    public void bootstrap(BootstrapContext context) {
        context.getLifecycleManager().registerEventHandler(
            RegistryEvents.ITEM.compose(),
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

Persistent Custom Data Components use `com.mojang.serialization.Codec<T>` directly. Mojang publishes Codec in the separate DataFixerUpper library, not in NMS. Content System does not reproduce or wrap its combinator API:

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

The Nix API provides the same DataFixerUpper version as the pinned Minecraft server. The inspected 26.2 build uses 10.0.21. Plugins compile against that dependency and must not shade or relocate an incompatible private DFU copy. Nix updates the exposed DFU version with its server dependency. Content System uses the codec with internal Minecraft dynamic ops, including `NbtOps`, but never exposes NMS ops classes in its signatures.

#### 4.3.2 Data Component keys and typed lookup

Plugins predeclare ordinary Paper keys for Custom Component registration:

```java
public static final TypedKey<DataComponentType> SOULBOUND =
    RegistryKey.DATA_COMPONENT_TYPE.typedKey(Key.key("example", "soulbound"));
```

A `TypedKey<DataComponentType>` identifies a component-type registry entry, but cannot retain its valued or non-valued kind or Java value type `T`. Registration chooses the kind with `valued()` or `nonValued()`. Omitting it is an error. Repeated selections, including a switch between kinds, keep the last kind.

After registration, `Registry.DATA_COMPONENT_TYPE` returns the real registry-backed `DataComponentType`. A plugin checks its kind and performs the single unchecked cast needed to recover its own valued type:

```java
@SuppressWarnings("unchecked")
public static DataComponentType.Valued<Soulbound> soulbound() {
    DataComponentType type = Registry.DATA_COMPONENT_TYPE.getOrThrow(SOULBOUND);
    if (!(type instanceof DataComponentType.Valued<?> valued)) {
        throw new IllegalStateException();
    }
    return (DataComponentType.Valued<Soulbound>) valued;
}
```

Java erasure means the server cannot prove that the plugin's asserted `T` is correct. The plugin must keep it consistent with its values, codec, Projection Modifiers, and ItemStack calls. Nix does not expose Paper's private vanilla `DataComponentTypes` initialization helpers or add another generic component-key token. Once resolved, the real component type works with the existing Bukkit ItemStack component methods.

#### 4.3.3 Item Type keys and lookup

Custom Item Types use ordinary Paper typed keys:

```java
public static final TypedKey<ItemType> HEALING_APPLE =
    RegistryKey.ITEM.typedKey(Key.key("my_plugin", "healing_apple"));
```

Bootstrap registers that key through `RegistryEvents.ITEM`. Afterwards the ordinary Paper registry returns the real `ItemType`, whose normal API creates custom Runtime Item Stacks and exposes type metadata:

```java
ItemType healingApple = Registry.ITEM.getOrThrow(HEALING_APPLE);
ItemStack stack = healingApple.createItemStack();
stack.getItemType().equals(healingApple); // true
stack.getType();                         // Material.APPLE
```

No unbound `ItemType` implementation or parallel custom item type interface is introduced.

#### 4.3.4 Content System service

`ContentSystem` is a server-owned runtime service rather than a static utility singleton:

```java
ContentSystem contentSystem = Bukkit.getServer().getContentSystem();
```

The instance owns server-lifetime projection, recovery, diagnostics, cache invalidation, and registry-access behavior. Bootstrap registration is owned by Paper's static `RegistryEvents` providers and does not depend on this runtime service.

## 5. Runtime and Bukkit representations

A Runtime Item Stack remains a real custom stack in server memory:

```text
type: my_plugin:healing_apple
components:
  minecraft:food
  minecraft:consumable
  my_plugin:healing_strength
```

Bukkit `ItemStack` is the only public stack type for vanilla and custom items. It stays backed by the internal Runtime/NMS Item Stack. Nix adds no `NixItemStack` wrapper and no conversion between stack APIs.

Custom stacks need a registry-backed item-type accessor because the Material accessor remains a compatibility view:

```java
ItemStack stack = ...;

stack.getItemType().getKey();                  // my_plugin:healing_apple
stack.getType();                               // Material.APPLE compatibility
stack.getData(DataComponentTypes.FOOD);        // Vanilla Component
stack.getData(MyComponents.HEALING_STRENGTH);  // Custom Component
```

For a vanilla apple, `getItemType().getKey()` is `minecraft:apple` and `getType()` is also `Material.APPLE`. The proposed `getItemType()` returns Paper's existing registry-backed `ItemType`, which already implements `Keyed`; a separate bare key accessor is unnecessary.

Bukkit mutations change the Runtime Item Stack directly. Plugin-registered component types are Paper `DataComponentType.Valued<T>` or `DataComponentType.NonValued`, so `getData`, `setData`, `hasData`, `unsetData`, and `resetData` work unchanged. Registration atomically installs the NMS type, public Paper registry wrapper, and value adapter.

Custom valued components follow Paper's immutable-value rule. ItemStack and component-map copies can share value references, hash computation can cache state, and `getData()` does not deep-copy arbitrary values. Component values, including nested collections, must be deeply immutable with stable semantic `equals` and `hashCode`. Update them by replacing the whole value with `setData()`. Content System does not codec-round-trip every get or set, particularly because transient components have no codec.

Item type identity, comparison, and mutation use registry-backed `ItemType`, never the compatibility Material from `getType()`. On a non-empty stack, `getItemType()` resolves the NMS item through the Paper item registry. The null handle of an empty stack returns `ItemType.AIR`. `getType()` then gives the compatibility view. A `minecraft` Item Type maps to its Material, while a Custom Item Type returns its configured Vanilla Material. AIR needs no special mapping because it is a vanilla Item Type.

The two `ItemStack.of(Material, ...)` factories resolve `Material#asItemType()` and delegate to their `ItemType` counterparts. `of(ItemType)` and `of(ItemType, int)` create the actual vanilla or custom type, default the amount to one where omitted, and retain the existing positive-amount validation.

`withType(ItemType)` converts a stack without mutating it. If the target is the current Item Type, it still returns an independent equivalent stack. Otherwise it creates the target vanilla or Custom Item Type, preserves the amount and per-stack Vanilla and Custom Component patch, then reconciles the patch with the target prototype. Old unpatched defaults disappear and target defaults take effect. `ItemType.AIR` returns an empty stack. `withType(Material)` resolves the vanilla Item Type and delegates to this overload.

The deprecated `setType(Material)` remains for compatibility but gains no Item Type overload. It resolves the Material's vanilla Item Type and uses the same ItemType-based internal mutation path. On a Custom Item Stack it therefore selects the vanilla registry Item Type even when the argument equals the stack's compatibility `getType()`/Vanilla Material. It preserves amount and the ordinary per-stack component patch, removes old unpatched defaults, and clears the stack for `Material.AIR`.

Within CraftItemStack, actual type equality, mutation, copying, similarity, and type-based dispatch use Item Type identity. Material remains valid only where the public compatibility API or legacy MaterialData specifically requires it.

Custom ItemMeta handling uses a narrow `ContentSystemItem` branch rather than converting the whole ItemFactory API to Item Type overloads. `getItemMeta()` always constructs the ordinary generic ItemMeta from the custom stack's current component patch, independent of its Vanilla Material; `getItemType().getItemMetaClass()` is `ItemMeta.class`. `setItemMeta()` accepts an ordinary generic ItemMeta and applies Paper's normal component-backed replacement semantics. Any specialized ItemMeta, such as book, potion, armor, map, or block-state meta, is inapplicable to every `ContentSystemItem`: `setItemMeta()` returns false and leaves the stack unchanged. Untyped `editMeta` operates on the generic meta, while typed edits requesting a specialized meta return false. Vanilla items retain their existing ItemMeta behavior.

Bukkit `ItemStack#clone()` creates an independent Runtime Item Stack that retains the real Custom Item Type and both Vanilla and Custom Components. Mutating the clone's amount, components, or type does not affect the original. API types do not expose the internal NMS backing.

Runtime similarity and merge eligibility use actual Item Type plus effective component state. A Custom Item Stack is not similar to a vanilla stack of its Vanilla Material or to another Custom Item Type sharing that material. Differences in effective Vanilla or Custom Components prevent similarity and merging. Amount is ignored by `isSimilar` but remains part of `equals`.

## 6. Client projection

Client Projection runs only when Nix sends an item to a client. It can vary by viewer, such as for resource-pack availability, but not by packet or display scenario for that viewer. Given the viewer, Runtime Item Stack state, projection revision, and captured operation context, it is deterministic.

The Vanilla Material supplies the base item identity. Effective Vanilla Components from the Runtime Item Stack are automatically reconciled against that base's default components:

```text
Runtime has component, base does not  → add it
Runtime lacks component, base has it  → add a removal patch entry
Both have equal values                → rely on the base default
Both have different values            → override with the runtime value
Custom Component                       → omit by default
```

Compare effective components. Do not copy only the custom stack's patch, because the custom type and Vanilla Material have different prototypes.

Example: if Bukkit removes `CONSUMABLE` from a healing apple whose Vanilla Material is `APPLE`, projection must include the equivalent of `!minecraft:consumable`; otherwise the client's apple prototype would incorrectly restore consumability.

Vanilla Components cross the boundary without per-item projection code. Use modifiers only to map custom semantics to a vanilla approximation the client can express.

### 6.1 Projection modifiers

A Custom Item Type can bind a reusable modifier to a Custom Component Type. This item-specific configuration is immutable state on its `ContentSystemItem`. The modifier runs when the custom component has an effective value. It changes only projected Vanilla Components, never the Runtime Item Stack. The API sketch defines the modifier, context, source, and output interfaces.

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

An item-specific binding replaces the component default. That matters when Custom Item Types need different client representations of the same reusable state. Vanilla item types have no item-specific binding, so they use the component default when one exists. Repeated `project` and `suppressProjection` calls for a component keep the last setting. That setting takes the position of its last call in item-specific execution order.

Data Component storage has no stable iteration order. Projection runs inherited component-default modifiers first, in lexicographic Data Component Type key order. It then runs item-specific modifiers in builder binding order. Each modifier reads the output left by earlier ones. A later `set` for the same component wins. Read-modify-write code can preserve earlier output before adding to it. There is no numeric priority or global ordering.

```java
.project(MyComponents.EVENT_ITEM_YEAR, YEAR_LORE)
.project(MyComponents.EVENT_EXPIRES_AT, EXPIRY_LORE)
```

If both modifiers read the current lore and append one line, the resulting order is canonical lore, year line, then expiry line.

Projection is synchronous and hot. Nix uses it for outbound stacks and projected remote-state and hash comparison. Modifiers must finish promptly, avoid blocking, and do no I/O. Small allocations and in-memory lookups are fine. Database, filesystem, network, waiting, and expensive computation are not. Reuse the same projected result for packet output and remote-state recording.

Each modifier gets its own Nix-managed transaction. `ProjectionOutput` records the value of every Vanilla Component immediately before that modifier first writes it. If the modifier throws or leaves invalid output, Nix restores those values, removes restoration entries that only the failed modifier introduced, reports the failure with rate limiting by item type and binding, then runs later modifiers. Earlier committed modifiers remain visible. Fatal VM failures are outside this rule.

```text
canonical output
  → modifier A commits
  → modifier B partially writes, then fails
  → roll back only modifier B
  → modifier C continues from modifier A's output
```

The transaction stays inside Nix. Plugin code receives only `ProjectionOutput`, and Nix does not copy the whole projected stack. Rollback cannot make blocking or non-terminating modifier code safe. That violates the plugin contract.

Client Projection includes the same `nix:item` Recovery Envelope used by the Persistent Form. This lets a full projected stack returned by the client, notably through creative-mode slot packets, recover its custom identity without a connection-scoped projection-token ledger. The client does not interpret the envelope; it only preserves and returns opaque `CUSTOM_DATA`. Projection preserves all unrelated Runtime `CUSTOM_DATA` and does not mutate the Runtime Stack. For a recognized Custom Runtime Stack, Nix owns and canonically replaces the reserved `nix:item` subtree rather than merging plugin-supplied contents into it.

Envelope encoding must be deterministic. Runtime stacks with equal custom identity and persistent Custom Component patches must produce equal envelope values, so metadata does not stop equal stacks from stacking. Different custom identities or persistent Custom Component state must remain unequal.

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

### 6.2 Current client authority model

The usual interaction model stays server-authoritative, so Nix needs no general reverse-projection pass. In the inspected Minecraft 26.2 protocol, `ServerboundSetCreativeModeSlotPacket` is the only standard serverbound packet with a complete `ItemStack`. The server handles it only for players with infinite-material or creative authority. Survival inventory clicks return item identity, count, and component hashes in `HashedStack`. The server uses them to track and validate the client's remote view, not to replace an authoritative slot. Item use and other survival interactions identify a hand, slot, target, or operation. The server reads the stack it already owns.

An ordinary survival client cannot replace custom item state by editing the outbound Recovery Envelope. A creative client can return an edited envelope, but it can already create arbitrary vanilla stacks and components. In every game mode, returned envelope data is structurally untrusted and must pass normal recovery validation.

Nix does not sign or encrypt the envelope. Its contents are client-readable, and creative-authority clients may forge them. Custom item data must not serve as an authorization credential or contain secrets.

### 6.3 Projection-aware remote state

Container synchronization must compare client remote state with Client Projection, not the Runtime Item Stack. `HashedStack` contains the item holder, count, hashes of added component values, and removed component types. The client returns hashes for the Vanilla Material and projected component patch. Those intentionally differ from the custom type and component patch the server keeps.

If `RemoteSlot` compares that hash directly with the Runtime Item Stack, it will repeatedly conclude that the client is out of sync and resend the slot. Packet output and remote slot/carried-item state must therefore share the same per-viewer projection service.

Projection does not need to run for every slot on every interaction. Vanilla `RemoteSlot` normally uses one full `remoteStack` both as the last server snapshot and as the expected client representation because those representations are identical. Nix separates those responsibilities:

```java
final class ProjectionAwareRemoteSlot {
    ItemStack lastRuntimeStack;       // canonical input used last time
    ItemStack remoteStack;            // exact projected stack sent/accepted
    long lastProjectionRevision;

    @Nullable HashedStack clientClaim; // latest client-predicted representation
}
```

When sending a slot, the server projects once and uses that exact result for both the packet and `remoteStack`. A received client claim is validated directly with `clientClaim.matches(remoteStack, hasher)`; Nix does not construct or retain a second server-side `HashedStack` for the same representation. On a later synchronization check:

```text
runtime unchanged + viewer projection revision unchanged
  → remoteStack remains the expected client representation
  → validate the client claim directly against remoteStack
  → do not run modifiers

runtime changed or projection revision changed
  → project the new runtime value once
  → compare it with remoteStack and validate any client claim against the new projection
  → send only if the projected representation changed or the claim is wrong
  → update lastRuntimeStack, remoteStack, and the recorded revision
```

Client click hashes describe the client's predicted post-click state, so a claim for the pre-click outgoing stack cannot validate a slot whose authoritative state actually changed. Such changed slots require a new projection of the server's post-click Runtime Item Stack. A container click normally changes only a small number of slots and the cursor. Slots whose canonical state did not change validate their claims against the existing `remoteStack`. A matching claim is promoted to that full projected remote state; a mismatch causes the projected stack to be sent and the claim to be discarded. Minecraft's existing per-player component-value hash cache remains applicable while `HashedStack.matches` hashes projected component values.

When a runtime change has the same projection, Nix updates `lastRuntimeStack` without sending a packet. This covers server-only and other projection-irrelevant state.

Each player already owns separate container synchronization and `RemoteSlot` state, so per-viewer projection does not require tracking recovery patches for every inventory globally. It requires only per-visible-slot canonical/projected snapshots, the latest client claim when present, and a per-viewer projection revision. This state is ephemeral and discarded with the menu or connection.

Context changes that do not mutate a Runtime Item Stack use explicit invalidation:

```java
Bukkit.getServer()
    .getContentSystem()
    .refreshItemProjections(Player viewer);
```

Refreshing increments the viewer's projection revision, invalidates projected remote state, and resynchronizes currently visible inventory, container, cursor, and equipment representations. Closed storage is not scanned; it projects normally when next viewed.

Packet encoding, projected hashing, remote-state comparison, and invalidation must use one deterministic presentation service. A last-moment `ItemStack` wire-codec rewrite is not enough.

## 7. Persistence and recovery

### 7.1 Persistent form

Persistence is separate from Client Projection, even though both use the same Vanilla Material identity.

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

Envelope field names and value encoding are internal persistence details, not public API. The envelope has no schema-version field. Recovery ignores unknown fields. After a successful recovery, they disappear with the removed reserved subtree. That subtree contains:

- an optional Custom Item Type key;
- the persistent per-stack Custom Component patch.

Presence of the inner `item` field means the Persistent Form represents a Custom Item Type. Its absence means the outer vanilla item identity remains authoritative and only persistent Custom Components are being carried.

Nix does not duplicate type-default Custom Component values. Like vanilla stack patches, this lets an updated type default affect stacks that do not override it. A vanilla Item Stack with Custom Components omits custom item identity and persists only the persistent part of its Custom Component patch.

Vanilla Components remain in the outer ordinary component patch and are authoritative on recovery. The envelope is authoritative only for custom identity and persistent Custom Component state. Nix does not store a projection baseline or perform a three-way merge.

Custom Components cannot remain top-level Persistent Form entries. Without Nix, vanilla lacks their registry keys and codecs, so it cannot reliably decode the component patch. Their encoded values live under the known `minecraft:custom_data` component, where vanilla keeps them opaque and loadable.

The envelope occupies only the reserved `nix:item` subtree. Existing unrelated `CUSTOM_DATA`, including plugin data, must be preserved.

A persistent plugin-registered `DataComponentType.Valued<T>` supplies a public DataFixerUpper codec. `persistent(codec)` means its per-stack set/removal patch participates in `nix:item`; it does not mean the custom type is serialized as an outer vanilla component. A Non-Valued type uses no-argument `persistent()` because Nix only needs to encode marker presence/removal. Without the matching persistence call, either component kind is transient and omitted from Persistent Form and Client Projection recovery metadata. Its per-stack value, marker, or removal patch is lost across save/load and creative round-trips, although a Custom Item Type's transient default naturally reappears when that type is reconstructed.

If a persistent valued component codec reports an encoding `DataResult` error, including one with a partial value, or throws a non-fatal exception, serialization of that ItemStack fails without producing a successful partial Persistent Form. Nix does not omit the component or silently downgrade the item to preserve the rest. Fatal VM failures are not caught.

### 7.2 Recovery

Conceptual recovery flow:

```text
1. Decode the outer vanilla item identity and Vanilla Components normally.
2. Look for CUSTOM_DATA["nix:item"].
3. Validate envelope shape, key syntax, and size limits.
4. If a custom item identity is present, resolve it to a registered `ContentSystemItem`; otherwise retain the decoded vanilla type.
5. Validate and decode every present Custom Component patch entry.
6. If all required data is resolvable, construct or retain the selected Runtime Item Stack type.
7. Transfer the authoritative outer Vanilla Component state.
8. Apply the Custom Component patch as top-level runtime components.
9. Remove only the reserved recovery subtree from runtime CUSTOM_DATA.
```

A missing envelope `components` field or an omitted custom component is valid and means an empty/no override patch. Unknown fields do not block recovery. Neither affects the interpretation of known fields.

Before calling plugin codecs, recovery applies fixed limits. The `nix:item` subtree may use at most 1 MiB by `Tag#sizeInBytes()`, contain at most 256 Custom Component patch entries, and nest at most 64 levels. Keys use ordinary Adventure and Minecraft syntax and length validation. Violating a limit takes the normal complete fallback.

Recovery is all-or-nothing when data is present but cannot be interpreted safely. A codec `DataResult` error, including one carrying a partial value, and a non-fatal exception thrown by a codec are both decode failures; partial values are never accepted, while fatal VM failures propagate. Any of the following leaves the stack as a vanilla fallback for that load:

- the Custom Item Type is not registered;
- a present Custom Component Type is not registered;
- a present custom value fails to decode;
- the envelope is malformed.

The fallback retains the complete Recovery Envelope. Nix must not partially recover it and overwrite undecodable state on the next save. Recovery diagnostics are rate-limited by the relevant item or component key and failure category. Different keys and categories report independently. The same failure collapses within its window and can report again later. Successful recovery produces no failure diagnostic. Diagnostics do not mutate the item.

An unresolved fallback retains `nix:item` when sent to a client as well. This preserves future recoverability if a full client-side copy returns to the server.

## 8. Decision summary

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
13. Remote synchronization records separate per-viewer canonical input and projected `remoteStack` snapshots; client `HashedStack` claims are validated directly against the projected stack without retaining a duplicate expected hash.
14. Modifiers re-run only when canonical runtime state changes or projection context is invalidated; a changed canonical state that projects identically does not send a packet.
15. Plugins explicitly refresh a player's projections when external or viewer context changes without mutating the Runtime Item Stack.
16. Recovery Envelopes are not signed or encrypted; they are client-readable and untrusted.
17. Bukkit `ItemStack` is the sole public stack abstraction for vanilla and custom items; there is no separate `NixItemStack` facade.
18. Custom stacks expose their actual registry-backed `ItemType` separately from the compatibility `Material`, and Bukkit `clone()` preserves custom identity in an independent Runtime Item Stack.

### 8.2 API and registration

19. Item Type and Data Component Type registration use `RegistryEvents.ITEM` and `.DATA_COMPONENT_TYPE`, Paper's ordinary prioritized compose events and writable-registry API.
20. `ContentSystem` is a single server-owned runtime service exposed by `Server#getContentSystem()`; bootstrap registration is independent of that runtime service.
21. ItemStack identity and conversion are ItemType-based: `getItemType()`, `of(ItemType, ...)`, and `withType(ItemType)` expose actual vanilla or custom types; existing Material factories and `withType(Material)` delegate through vanilla Item Types, while deprecated `setType(Material)` keeps no Item Type overload and always selects the Material's vanilla type.
22. Custom Components are orthogonal to Custom Item Types and may be attached to ordinary vanilla Item Stacks; their durable form uses the same Recovery Envelope without a custom item identity.
23. The Recovery Envelope is stored at `minecraft:custom_data["nix:item"]`; an inner `item` field is present only for a Custom Item Type.
24. Both valued and non-valued Custom Components are transient by default; valued types opt into persistence with `persistent(codec)`, marker types with `persistent()`, and only persistent per-stack patches enter the Recovery Envelope.
25. Custom components reuse Paper's existing `DataComponentType` registry wrappers and Bukkit `ItemStack` methods; there is no parallel `CustomDataComponentType` hierarchy or stack-method overload set. Every Custom Item Type uses generic `ItemMeta` regardless of Vanilla Material, and specialized ItemMeta is inapplicable.
26. Content System additions live under `club.plutoproject.nix.contentsystem`, while persistent component registrations directly accept DataFixerUpper `Codec<T>`; the Nix API provides the exact DFU version used by its pinned server rather than wrapping it.
27. Only `RegistryEvents.ITEM` and `.DATA_COMPONENT_TYPE` expose bootstrap writes; they support compose additions but not entry-add mutation, and the underlying NMS registries receive entries keyed by Paper `TypedKey` while Paper registry APIs remain read-only views.
28. Plugins predeclare `TypedKey<DataComponentType>` values through `RegistryKey.DATA_COMPONENT_TYPE.typedKey(key)`. Registration explicitly selects valued or non-valued kind; after registration the plugin checks that kind and owns the unchecked cast that reasserts a valued component's erased Java type.
29. Custom Item Types are registered with `TypedKey<ItemType>` values created through `RegistryKey.ITEM.typedKey(key)` and are retrieved as real registry-backed `ItemType` values after registration; no unbound or parallel item type API is introduced.
30. Custom valued components preserve Paper's existing deeply immutable value invariant; mutation replaces the value through `setData()` rather than mutating an object returned by `getData()`.
31. Content registration does not declare or enforce per-plugin namespace ownership; plugins may coordinate arbitrary namespaces, while `minecraft` and `nix` are reserved and duplicate keys fail bootstrap.
32. `vanillaMaterial` controls compatibility representations only and does not inherit that vanilla item's default components or specialized ItemMeta into the Runtime Custom Item Type; the mapping is immutable type-level state on `ContentSystemItem`.

### 8.3 Gameplay hooks

33. Every Custom Item Type is an internal `ContentSystemItem` whose immutable type-level definition contains item-specific projection and hook configuration; it dispatches stable typed hooks through NMS Item virtual overrides without exposing NMS classes or adding custom checks at callers.
34. `addHook` sets one callback per hook and later calls replace earlier ones; missing hooks run vanilla defaults, while every explicit context default call executes again like a Java `super` call.
35. `ItemHooks` is a small stable gameplay API, not a one-to-one mirror of version-specific NMS `Item` methods. Client presentation belongs in components and projection.
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
55. Gameplay hooks guarantee built-in Item virtual-call parity, not a separate Nix event schedule; context-deficient signatures gain compatible enriched overloads that vanilla delegates and `ContentSystemItem` overrides, crafting keeps each source entry point, and callbacks remain synchronous.
56. All descriptors use `ItemHook<C, R>`. Result handlers use the generic callback, while `R = Void` binds `Consumer<C>` through an `addHook` overload. Do not specialize boolean and float results unless profiling shows a real cost.
57. Default behavior calls are not once-guarded; each invocation executes again, matching repeated Java `super` calls.
58. Contexts are callback-scoped by contract only; Nix adds no runtime invalidation or delayed-default-call handling.
59. Registry entries and their codecs, projection modifiers, and gameplay handlers remain active until server shutdown even if the registering plugin is disabled; hot unload is unsupported.
60. Hook tables are fixed by the bootstrap Item Type builder; runtime set/remove/rebind APIs are absent, while plugin-owned service indirection remains the plugin's responsibility.

### 8.4 Projection and persistence API

61. Projection modifiers mirror Paper component kinds as sibling `ProjectionModifier.Valued<T>` and `.NonValued` functional interfaces.
62. Both component kinds default transient; valued persistence requires a DFU codec, while non-valued persistence uses an explicit no-argument marker method.
