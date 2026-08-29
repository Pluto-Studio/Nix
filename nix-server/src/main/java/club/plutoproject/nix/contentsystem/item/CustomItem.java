package club.plutoproject.nix.contentsystem.item;

import org.jetbrains.annotations.ApiStatus;

import club.plutoproject.nix.contentsystem.hook.DefaultCall;
import club.plutoproject.nix.contentsystem.hook.HookDispatcher;
import club.plutoproject.nix.contentsystem.hook.InteractionResultAdapter;
import club.plutoproject.nix.contentsystem.hook.ItemHook;
import club.plutoproject.nix.contentsystem.hook.ItemHookHandler;
import club.plutoproject.nix.contentsystem.hook.ItemHooks;
import club.plutoproject.nix.contentsystem.hook.context.ItemAttackDamageBonusContextImpl;
import club.plutoproject.nix.contentsystem.hook.context.ItemBlockStateContextImpl;
import club.plutoproject.nix.contentsystem.hook.context.ItemCanDestroyBlockContextImpl;
import club.plutoproject.nix.contentsystem.hook.context.ItemContainerFitContextImpl;
import club.plutoproject.nix.contentsystem.hook.context.ItemCraftedContextImpl;
import club.plutoproject.nix.contentsystem.hook.context.ItemDestroyedAsEntityContextImpl;
import club.plutoproject.nix.contentsystem.hook.context.ItemFinishUseContextImpl;
import club.plutoproject.nix.contentsystem.hook.context.ItemHurtEntityContextImpl;
import club.plutoproject.nix.contentsystem.hook.context.ItemInteractLivingEntityContextImpl;
import club.plutoproject.nix.contentsystem.hook.context.ItemInventoryTickContextImpl;
import club.plutoproject.nix.contentsystem.hook.context.ItemMineBlockContextImpl;
import club.plutoproject.nix.contentsystem.hook.context.ItemReleaseUseContextImpl;
import club.plutoproject.nix.contentsystem.hook.context.ItemUseContextImpl;
import club.plutoproject.nix.contentsystem.hook.context.ItemUseOnBlockContextImpl;
import club.plutoproject.nix.contentsystem.hook.context.ItemUseTickContextImpl;
import club.plutoproject.nix.contentsystem.hook.result.ItemInteractionResult;
import club.plutoproject.nix.contentsystem.hook.result.ItemMineBlockResult;
import club.plutoproject.nix.contentsystem.hook.result.ItemReleaseUseResult;
import club.plutoproject.nix.contentsystem.hook.result.ItemUseResult;
import club.plutoproject.nix.contentsystem.projection.ProjectionModifier;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.craftbukkit.CraftEquipmentSlot;
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.craftbukkit.block.data.CraftBlockData;
import org.bukkit.craftbukkit.damage.CraftDamageSource;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.craftbukkit.util.CraftLocation;
import org.bukkit.craftbukkit.util.CraftMagicNumbers;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/**
 * The immutable NMS bridge item used by every registered Content System item.
 *
 * <p>Its component prototype is still the normal NMS item prototype. Nix only
 * adds the compatibility material, projection bindings, and fixed hook table
 * that vanilla {@link Item} does not carry.</p>
 */
@ApiStatus.Internal
public final class CustomItem extends Item {

    private static final ThreadLocal<Integer> CRAFTING_DEFAULT_DEPTH = ThreadLocal.withInitial(() -> 0);

    private final String key;
    private final Material vanillaMaterial;
    private final List<DefaultComponent> defaultComponents;
    private final List<ProjectionBinding> projectionBindings;
    private final Map<ItemHook<?, ?>, ItemHookHandler<?, ?>> hooks;

    public CustomItem(
        final ResourceKey<Item> key,
        final Material vanillaMaterial,
        final List<DefaultComponent> defaultComponents,
        final List<ProjectionBinding> projectionBindings,
        final Map<ItemHook<?, ?>, ItemHookHandler<?, ?>> hooks
    ) {
        super(properties(key, defaultComponents));
        reapplyExplicitDefaults(key, defaultComponents);
        this.key = key.identifier().toString();
        this.vanillaMaterial = Objects.requireNonNull(vanillaMaterial, "vanillaMaterial");
        this.defaultComponents = List.copyOf(defaultComponents);
        this.projectionBindings = List.copyOf(projectionBindings);
        this.hooks = Map.copyOf(hooks);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Item.Properties properties(final ResourceKey<Item> key, final List<DefaultComponent> defaults) {
        final Item.Properties properties = new Item.Properties().setId(key);
        for (final DefaultComponent component : defaults) {
            properties.component((DataComponentType) component.type(), component.value());
        }
        return properties;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void reapplyExplicitDefaults(final ResourceKey<Item> key, final List<DefaultComponent> defaults) {
        // Item.Properties appends generated name and model components after its explicit components.
        BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.add(key, (components, context, ignoredKey) -> {
            for (final DefaultComponent component : defaults) {
                components.set((DataComponentType) component.type(), component.value());
            }
        });
    }

    public String key() {
        return this.key;
    }

    public Material vanillaMaterial() {
        return this.vanillaMaterial;
    }

    public List<DefaultComponent> defaultComponents() {
        return this.defaultComponents;
    }

    public List<ProjectionBinding> projectionBindings() {
        return this.projectionBindings;
    }

    @SuppressWarnings("unchecked")
    public <C, R> ItemHookHandler<C, R> handler(
        final ItemHook<C, R> hook
    ) {
        return (ItemHookHandler<C, R>) this.hooks.get(hook);
    }

    public Map<ItemHook<?, ?>, ItemHookHandler<?, ?>> hooks() {
        return this.hooks;
    }

    public static boolean isCustom(final Item item) {
        return item instanceof CustomItem;
    }

    public static Material vanillaMaterial(final Item item) {
        return item instanceof CustomItem custom ? custom.vanillaMaterial() : CraftMagicNumbers.getMaterial(item);
    }

    @Override
    public InteractionResult use(final Level level, final net.minecraft.world.entity.player.Player player, final InteractionHand hand) {
        final Player bukkitPlayer = (Player) player.getBukkitEntity();
        final org.bukkit.inventory.ItemStack liveStack = CraftItemStack.asCraftMirror(player.getItemInHand(hand));
        final DefaultCall<ItemUseResult> defaults = new DefaultCall<>(() -> {
            final InteractionResult result = super.use(level, player, hand);
            return new DefaultCall.DefaultValue<>(InteractionResultAdapter.fromVanillaUse(result), result);
        });
        final ItemUseContextImpl context = new ItemUseContextImpl(
            bukkitPlayer,
            liveStack,
            CraftEquipmentSlot.getHand(hand),
            defaults
        );
        final ItemUseResult result = HookDispatcher.dispatch(
            this,
            ItemHooks.ON_USE,
            context,
            defaults,
            false,
            Objects::nonNull
        );
        return InteractionResultAdapter.toNmsInteraction(result, defaults);
    }

    @Override
    public InteractionResult useOn(final UseOnContext context) {
        if (context.getPlayer() == null) {
            return super.useOn(context);
        }
        final net.minecraft.world.entity.player.Player nmsPlayer = context.getPlayer();
        final Player bukkitPlayer = (Player) nmsPlayer.getBukkitEntity();
        final org.bukkit.inventory.ItemStack liveStack = CraftItemStack.asCraftMirror(context.getItemInHand());
        final Block clickedBlock = CraftBlock.at(context.getLevel(), context.getClickedPos());
        final Location point = CraftLocation.toBukkit(context.getClickLocation(), context.getLevel()).clone();
        final DefaultCall<ItemInteractionResult> defaults = new DefaultCall<>(() -> {
            final InteractionResult result = super.useOn(context);
            return new DefaultCall.DefaultValue<>(InteractionResultAdapter.fromVanillaInteraction(result), result);
        });
        final ItemUseOnBlockContextImpl hookContext = new ItemUseOnBlockContextImpl(
            bukkitPlayer,
            liveStack,
            CraftEquipmentSlot.getHand(context.getHand()),
            defaults,
            clickedBlock,
            CraftBlock.notchToBlockFace(context.getClickedFace()),
            point,
            context.isInside(),
            !context.getLevel().getWorldBorder().isWithinBounds(context.getClickedPos()),
            context.isSecondaryUseActive()
        );
        final ItemInteractionResult result = HookDispatcher.dispatch(
            this,
            ItemHooks.ON_USE_ON_BLOCK,
            hookContext,
            defaults,
            false,
            Objects::nonNull
        );
        return InteractionResultAdapter.toNmsInteraction(result, defaults);
    }

    @Override
    public InteractionResult interactLivingEntity(final ItemStack itemStack, final net.minecraft.world.entity.player.Player player, final LivingEntity target, final InteractionHand hand) {
        final DefaultCall<ItemInteractionResult> defaults = new DefaultCall<>(() -> {
            final InteractionResult result = super.interactLivingEntity(itemStack, player, target, hand);
            return new DefaultCall.DefaultValue<>(InteractionResultAdapter.fromVanillaInteraction(result), result);
        });
        final ItemInteractLivingEntityContextImpl context = new ItemInteractLivingEntityContextImpl(
            (Player) player.getBukkitEntity(),
            CraftItemStack.asCraftMirror(itemStack),
            CraftEquipmentSlot.getHand(hand),
            defaults,
            (org.bukkit.entity.LivingEntity) target.getBukkitEntity()
        );
        final ItemInteractionResult result = HookDispatcher.dispatch(
            this,
            ItemHooks.ON_INTERACT_LIVING_ENTITY,
            context,
            defaults,
            false,
            Objects::nonNull
        );
        return InteractionResultAdapter.toNmsInteraction(result, defaults);
    }

    @Override
    public void onUseTick(final Level level, final LivingEntity livingEntity, final ItemStack itemStack, final int ticksRemaining) {
        final DefaultCall<Void> defaults = new DefaultCall<>(() -> {
            super.onUseTick(level, livingEntity, itemStack, ticksRemaining);
            return new DefaultCall.DefaultValue<>(null, null);
        });
        final ItemUseTickContextImpl context = new ItemUseTickContextImpl(
            (org.bukkit.entity.LivingEntity) livingEntity.getBukkitEntity(),
            CraftItemStack.asCraftMirror(itemStack),
            CraftEquipmentSlot.getSlot(livingEntity.getUsedItemHand().asEquipmentSlot()),
            ticksRemaining,
            livingEntity.getTicksUsingItem(),
            defaults
        );
        HookDispatcher.dispatch(
            this,
            ItemHooks.ON_USE_TICK,
            context,
            defaults,
            true,
            ignored -> true
        );
    }

    @Override
    public ItemStack finishUsingItem(final ItemStack itemStack, final Level level, final LivingEntity entity) {
        final org.bukkit.inventory.ItemStack liveStack = CraftItemStack.asCraftMirror(itemStack);
        final DefaultCall<org.bukkit.inventory.ItemStack> defaults = new DefaultCall<>(() -> {
            final ItemStack result = super.finishUsingItem(itemStack, level, entity);
            return new DefaultCall.DefaultValue<>(CraftItemStack.asCraftMirror(result), result);
        });
        final ItemFinishUseContextImpl context = new ItemFinishUseContextImpl(
            (org.bukkit.entity.LivingEntity) entity.getBukkitEntity(),
            liveStack,
            CraftEquipmentSlot.getSlot(entity.getUsedItemHand().asEquipmentSlot()),
            defaults
        );
        final org.bukkit.inventory.ItemStack result = HookDispatcher.dispatch(
            this,
            ItemHooks.ON_FINISH_USE,
            context,
            defaults,
            false,
            Objects::nonNull
        );
        if (defaults.returned(result) && defaults.nativeResultFor(result) instanceof ItemStack nativeResult) {
            return nativeResult;
        }
        if (result == liveStack) {
            return itemStack;
        }
        return CraftItemStack.asNMSCopy(result);
    }

    @Override
    public boolean releaseUsing(final ItemStack itemStack, final Level level, final LivingEntity entity, final int remainingTime) {
        final int usedTicks = entity.getTicksUsingItem();
        final DefaultCall<ItemReleaseUseResult> defaults = new DefaultCall<>(() -> {
            final boolean result = super.releaseUsing(itemStack, level, entity, remainingTime);
            return new DefaultCall.DefaultValue<>(
                result
                    ? ItemReleaseUseResult.APPLY_AFTER_USE_EFFECTS
                    : ItemReleaseUseResult.SKIP_AFTER_USE_EFFECTS,
                result
            );
        });
        final ItemReleaseUseContextImpl context = new ItemReleaseUseContextImpl(
            (org.bukkit.entity.LivingEntity) entity.getBukkitEntity(),
            CraftItemStack.asCraftMirror(itemStack),
            CraftEquipmentSlot.getSlot(entity.getUsedItemHand().asEquipmentSlot()),
            remainingTime,
            usedTicks,
            defaults
        );
        final ItemReleaseUseResult result = HookDispatcher.dispatch(
            this,
            ItemHooks.ON_RELEASE_USE,
            context,
            defaults,
            false,
            Objects::nonNull
        );
        if (defaults.returned(result) && defaults.nativeResultFor(result) instanceof Boolean nativeResult) {
            return nativeResult;
        }
        return result == ItemReleaseUseResult.APPLY_AFTER_USE_EFFECTS;
    }

    @Override
    public boolean canDestroyBlock(final ItemStack itemStack, final BlockState state, final Level level, final BlockPos pos, final LivingEntity user) {
        final DefaultCall<Boolean> defaults = new DefaultCall<>(() -> {
            final boolean result = super.canDestroyBlock(itemStack, state, level, pos, user);
            return new DefaultCall.DefaultValue<>(result, result);
        });
        final ItemCanDestroyBlockContextImpl context = new ItemCanDestroyBlockContextImpl(
            (org.bukkit.entity.LivingEntity) user.getBukkitEntity(),
            CraftItemStack.asCraftMirror(itemStack),
            CraftBlock.at(level, pos),
            copyBlockData(state),
            defaults
        );
        return HookDispatcher.dispatch(
            this,
            ItemHooks.CAN_DESTROY_BLOCK,
            context,
            defaults,
            false,
            value -> value != null
        );
    }

    @Override
    public float getDestroySpeed(final ItemStack itemStack, final BlockState state) {
        final DefaultCall<Float> defaults = new DefaultCall<>(() -> {
            final float result = super.getDestroySpeed(itemStack, state);
            return new DefaultCall.DefaultValue<>(result, result);
        });
        final ItemBlockStateContextImpl<Float> context = new ItemBlockStateContextImpl<>(
            CraftItemStack.asCraftMirror(itemStack),
            copyBlockData(state),
            defaults
        );
        return HookDispatcher.dispatch(
            this,
            ItemHooks.DESTROY_SPEED,
            context,
            defaults,
            false,
            value -> value != null && Float.isFinite(value) && value >= 0.0F
        );
    }

    @Override
    public boolean isCorrectToolForDrops(final ItemStack itemStack, final BlockState state) {
        final DefaultCall<Boolean> defaults = new DefaultCall<>(() -> {
            final boolean result = super.isCorrectToolForDrops(itemStack, state);
            return new DefaultCall.DefaultValue<>(result, result);
        });
        final ItemBlockStateContextImpl<Boolean> context = new ItemBlockStateContextImpl<>(
            CraftItemStack.asCraftMirror(itemStack),
            copyBlockData(state),
            defaults
        );
        return HookDispatcher.dispatch(
            this,
            ItemHooks.IS_CORRECT_TOOL_FOR_DROPS,
            context,
            defaults,
            false,
            value -> value != null
        );
    }

    @Override
    public boolean mineBlock(final ItemStack itemStack, final Level level, final BlockState state, final BlockPos pos, final LivingEntity owner) {
        final DefaultCall<ItemMineBlockResult> defaults = new DefaultCall<>(() -> {
            final boolean result = super.mineBlock(itemStack, level, state, pos, owner);
            return new DefaultCall.DefaultValue<>(
                result
                    ? ItemMineBlockResult.AWARD_ITEM_USED_STAT
                    : ItemMineBlockResult.SKIP_ITEM_USED_STAT,
                result
            );
        });
        final ItemMineBlockContextImpl context = new ItemMineBlockContextImpl(
            (Player) owner.getBukkitEntity(),
            CraftItemStack.asCraftMirror(itemStack),
            CraftLocation.toBukkit(pos, level).clone(),
            copyBlockData(state),
            defaults
        );
        final ItemMineBlockResult result = HookDispatcher.dispatch(
            this,
            ItemHooks.ON_MINE_BLOCK,
            context,
            defaults,
            false,
            Objects::nonNull
        );
        if (defaults.returned(result) && defaults.nativeResultFor(result) instanceof Boolean nativeResult) {
            return nativeResult;
        }
        return result == ItemMineBlockResult.AWARD_ITEM_USED_STAT;
    }

    @Override
    public void hurtEnemy(final ItemStack itemStack, final LivingEntity mob, final LivingEntity attacker) {
        final DefaultCall<Void> defaults = new DefaultCall<>(() -> {
            super.hurtEnemy(itemStack, mob, attacker);
            return new DefaultCall.DefaultValue<>(null, null);
        });
        final ItemHurtEntityContextImpl context = new ItemHurtEntityContextImpl(
            (org.bukkit.entity.LivingEntity) attacker.getBukkitEntity(),
            (org.bukkit.entity.LivingEntity) mob.getBukkitEntity(),
            CraftItemStack.asCraftMirror(itemStack),
            defaults
        );
        HookDispatcher.dispatch(
            this,
            ItemHooks.ON_HURT_ENTITY,
            context,
            defaults,
            true,
            ignored -> true
        );
    }

    @Override
    public void postHurtEnemy(final ItemStack itemStack, final LivingEntity mob, final LivingEntity attacker) {
        final DefaultCall<Void> defaults = new DefaultCall<>(() -> {
            super.postHurtEnemy(itemStack, mob, attacker);
            return new DefaultCall.DefaultValue<>(null, null);
        });
        final ItemHurtEntityContextImpl context = new ItemHurtEntityContextImpl(
            (org.bukkit.entity.LivingEntity) attacker.getBukkitEntity(),
            (org.bukkit.entity.LivingEntity) mob.getBukkitEntity(),
            CraftItemStack.asCraftMirror(itemStack),
            defaults
        );
        HookDispatcher.dispatch(
            this,
            ItemHooks.AFTER_HURT_ENTITY,
            context,
            defaults,
            true,
            ignored -> true
        );
    }

    @Override
    public void inventoryTick(final ItemStack itemStack, final ServerLevel level, final Entity owner, final @Nullable EquipmentSlot slot) {
        final DefaultCall<Void> defaults = new DefaultCall<>(() -> {
            super.inventoryTick(itemStack, level, owner, slot);
            return new DefaultCall.DefaultValue<>(null, null);
        });
        final ItemInventoryTickContextImpl context = new ItemInventoryTickContextImpl(
            owner.getBukkitEntity(),
            CraftItemStack.asCraftMirror(itemStack),
            slot == null ? null : CraftEquipmentSlot.getSlot(slot),
            defaults
        );
        HookDispatcher.dispatch(
            this,
            ItemHooks.INVENTORY_TICK,
            context,
            defaults,
            true,
            ignored -> true
        );
    }

    @Override
    public void onCraftedBy(final ItemStack itemStack, final net.minecraft.world.entity.player.Player player) {
        this.onCraftedBy(itemStack, player, 1);
    }

    @Override
    public void onCraftedBy(final ItemStack itemStack, final net.minecraft.world.entity.player.Player player, final int craftedAmount) {
        this.dispatchCrafted(itemStack, craftedAmount, (Player) player.getBukkitEntity(), () -> {
            final int previous = CRAFTING_DEFAULT_DEPTH.get();
            CRAFTING_DEFAULT_DEPTH.set(previous + 1);
            try {
                super.onCraftedBy(itemStack, player);
            } finally {
                restoreCraftingDepth(previous);
            }
        });
    }

    @Override
    public void onCraftedPostProcess(final ItemStack itemStack, final Level level) {
        if (CRAFTING_DEFAULT_DEPTH.get() > 0) {
            super.onCraftedPostProcess(itemStack, level);
            return;
        }
        this.onCraftedPostProcess(itemStack, level, 1);
    }

    @Override
    public void onCraftedPostProcess(final ItemStack itemStack, final Level level, final int craftedAmount) {
        this.dispatchCrafted(itemStack, craftedAmount, null, () -> super.onCraftedPostProcess(itemStack, level));
    }

    private void dispatchCrafted(
        final ItemStack itemStack,
        final int craftedAmount,
        final Player player,
        final Runnable defaultOperation
    ) {
        final DefaultCall<Void> defaults = new DefaultCall<>(() -> {
            defaultOperation.run();
            return new DefaultCall.DefaultValue<>(null, null);
        });
        final ItemCraftedContextImpl context = new ItemCraftedContextImpl(
            CraftItemStack.asCraftMirror(itemStack),
            Math.max(1, craftedAmount),
            player,
            defaults
        );
        HookDispatcher.dispatch(
            this,
            ItemHooks.ON_CRAFTED,
            context,
            defaults,
            true,
            ignored -> true
        );
    }

    @Override
    public void onDestroyed(final ItemEntity itemEntity) {
        super.onDestroyed(itemEntity);
    }

    @Override
    public void onDestroyed(final ItemEntity itemEntity, final DamageSource damageSource) {
        this.onDestroyed(itemEntity, itemEntity.getItem(), damageSource);
    }

    public void onDestroyed(final ItemEntity itemEntity, final ItemStack itemStack, final DamageSource damageSource) {
        final DefaultCall<Void> defaults = new DefaultCall<>(() -> {
            super.onDestroyed(itemEntity);
            return new DefaultCall.DefaultValue<>(null, null);
        });
        final ItemDestroyedAsEntityContextImpl context = new ItemDestroyedAsEntityContextImpl(
            (org.bukkit.entity.Item) itemEntity.getBukkitEntity(),
            CraftItemStack.asCraftMirror(itemStack),
            new CraftDamageSource(damageSource),
            defaults
        );
        HookDispatcher.dispatch(
            this,
            ItemHooks.ON_DESTROYED_AS_ITEM_ENTITY,
            context,
            defaults,
            true,
            ignored -> true
        );
    }

    @Override
    public boolean canFitInsideContainerItems() {
        return super.canFitInsideContainerItems();
    }

    @Override
    public boolean canFitInsideContainerItems(final ItemStack itemStack) {
        final DefaultCall<Boolean> defaults = new DefaultCall<>(() -> {
            final boolean result = super.canFitInsideContainerItems();
            return new DefaultCall.DefaultValue<>(result, result);
        });
        final ItemContainerFitContextImpl context = new ItemContainerFitContextImpl(CraftItemStack.asCraftMirror(itemStack), defaults);
        return HookDispatcher.dispatch(
            this,
            ItemHooks.CAN_FIT_INSIDE_CONTAINER_ITEMS,
            context,
            defaults,
            false,
            value -> value != null
        );
    }

    @Override
    public float getAttackDamageBonus(final Entity victim, final float damage, final DamageSource damageSource) {
        return super.getAttackDamageBonus(victim, damage, damageSource);
    }

    @Override
    public float getAttackDamageBonus(
        final ItemStack weapon,
        final LivingEntity attacker,
        final Entity victim,
        final float damage,
        final DamageSource damageSource
    ) {
        final DefaultCall<Float> defaults = new DefaultCall<>(() -> {
            final float result = super.getAttackDamageBonus(victim, damage, damageSource);
            return new DefaultCall.DefaultValue<>(result, result);
        });
        final ItemAttackDamageBonusContextImpl context = new ItemAttackDamageBonusContextImpl(
            (org.bukkit.entity.LivingEntity) attacker.getBukkitEntity(),
            victim.getBukkitEntity(),
            CraftItemStack.asCraftMirror(weapon),
            damage,
            new CraftDamageSource(damageSource),
            defaults
        );
        return HookDispatcher.dispatch(
            this,
            ItemHooks.ATTACK_DAMAGE_BONUS,
            context,
            defaults,
            false,
            value -> value != null && Float.isFinite(value)
        );
    }

    private static BlockData copyBlockData(final BlockState state) {
        return CraftBlockData.createData(state).clone();
    }

    private static void restoreCraftingDepth(final int previous) {
        if (previous == 0) {
            CRAFTING_DEFAULT_DEPTH.remove();
        } else {
            CRAFTING_DEFAULT_DEPTH.set(previous);
        }
    }

    public record DefaultComponent(DataComponentType<?> type, Object value) {
        public DefaultComponent {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(value, "value");
        }
    }

    public record ProjectionBinding(
        DataComponentType<?> component,
        ProjectionModifier modifier,
        boolean suppressed
    ) {
        public ProjectionBinding {
            Objects.requireNonNull(component, "component");
            if (suppressed && modifier != null) {
                throw new IllegalArgumentException("A suppressed projection binding cannot have a modifier");
            }
        }
    }
}
