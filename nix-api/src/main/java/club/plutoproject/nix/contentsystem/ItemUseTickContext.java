package club.plutoproject.nix.contentsystem;

import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;

/**
 * Context for one tick of an active item use.
 */
@ApiStatus.Experimental
@ApiStatus.NonExtendable
public interface ItemUseTickContext extends DefaultVoidBehavior {

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
     * Gets the remaining use ticks before the vanilla decrement.
     *
     * @return the pre-decrement remaining ticks
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
