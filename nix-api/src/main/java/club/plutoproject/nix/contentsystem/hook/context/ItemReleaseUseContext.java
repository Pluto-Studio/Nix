package club.plutoproject.nix.contentsystem.hook.context;

import club.plutoproject.nix.contentsystem.hook.DefaultResultBehavior;
import club.plutoproject.nix.contentsystem.hook.result.ItemReleaseUseResult;

import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;

/**
 * Context for an active item use that is released or interrupted.
 */
@ApiStatus.Experimental
@ApiStatus.NonExtendable
public interface ItemReleaseUseContext extends DefaultResultBehavior<ItemReleaseUseResult> {

    /**
     * Gets the living entity using the item.
     *
     * @return the using entity
     */
    @Contract(pure = true)
    LivingEntity entity();

    /**
     * Gets the live active stack.
     *
     * @return the active stack
     */
    @Contract(pure = true)
    ItemStack itemStack();

    /**
     * Gets the active hand.
     *
     * @return the hand slot
     */
    @Contract(pure = true)
    EquipmentSlot hand();

    /**
     * Gets the remaining use ticks.
     *
     * @return the remaining ticks
     */
    @Contract(pure = true)
    int remainingTicks();

    /**
     * Gets the number of ticks for which the item has been used.
     *
     * @return the elapsed use ticks
     */
    @Contract(pure = true)
    int usedTicks();
}
