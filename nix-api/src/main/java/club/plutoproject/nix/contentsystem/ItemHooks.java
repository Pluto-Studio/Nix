package club.plutoproject.nix.contentsystem;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.ApiStatus;

/**
 * The stable gameplay hook descriptors available to custom item types.
 */
@ApiStatus.Experimental
public final class ItemHooks {

    /** Hook for using an item without a block target. */
    public static final ItemHook<ItemUseContext, ItemUseResult> ON_USE = ItemHook.create();
    /** Hook for using an item on a block. */
    public static final ItemHook<ItemUseOnBlockContext, ItemInteractionResult> ON_USE_ON_BLOCK = ItemHook.create();
    /** Hook for interacting with a living entity. */
    public static final ItemHook<ItemInteractLivingEntityContext, ItemInteractionResult> ON_INTERACT_LIVING_ENTITY = ItemHook.create();
    /** Hook called during an active item use. */
    public static final ItemHook<ItemUseTickContext, Void> ON_USE_TICK = ItemHook.create();
    /** Hook called when an active item use finishes. */
    public static final ItemHook<ItemFinishUseContext, ItemStack> ON_FINISH_USE = ItemHook.create();
    /** Hook called when an active item use is released or interrupted. */
    public static final ItemHook<ItemReleaseUseContext, ItemReleaseUseResult> ON_RELEASE_USE = ItemHook.create();
    /** Capability hook for destroying a block. */
    public static final ItemHook<ItemCanDestroyBlockContext, Boolean> CAN_DESTROY_BLOCK = ItemHook.create();
    /** Query hook for the base block-destruction speed. */
    public static final ItemHook<ItemBlockStateContext<Float>, Float> DESTROY_SPEED = ItemHook.create();
    /** Query hook for correct-tool-for-drops behavior. */
    public static final ItemHook<ItemBlockStateContext<Boolean>, Boolean> IS_CORRECT_TOOL_FOR_DROPS = ItemHook.create();
    /** Hook called after a block is mined. */
    public static final ItemHook<ItemMineBlockContext, ItemMineBlockResult> ON_MINE_BLOCK = ItemHook.create();
    /** Query hook for an attack damage bonus. */
    public static final ItemHook<ItemAttackDamageBonusContext, Float> ATTACK_DAMAGE_BONUS = ItemHook.create();
    /** Hook called after a living victim is successfully hurt. */
    public static final ItemHook<ItemHurtEntityContext, Void> ON_HURT_ENTITY = ItemHook.create();
    /** Hook called after post-attack effects for a living victim. */
    public static final ItemHook<ItemHurtEntityContext, Void> AFTER_HURT_ENTITY = ItemHook.create();
    /** Hook called when an item is ticked in an inventory. */
    public static final ItemHook<ItemInventoryTickContext, Void> INVENTORY_TICK = ItemHook.create();
    /** Hook called when an item is crafted or dispensed by a crafter. */
    public static final ItemHook<ItemCraftedContext, Void> ON_CRAFTED = ItemHook.create();
    /** Hook called when a dropped item entity is destroyed by damage. */
    public static final ItemHook<ItemDestroyedAsEntityContext, Void> ON_DESTROYED_AS_ITEM_ENTITY = ItemHook.create();
    /** Query hook for whether an item can fit inside a container item. */
    public static final ItemHook<ItemContainerFitContext, Boolean> CAN_FIT_INSIDE_CONTAINER_ITEMS = ItemHook.create();

    private ItemHooks() {
    }
}
