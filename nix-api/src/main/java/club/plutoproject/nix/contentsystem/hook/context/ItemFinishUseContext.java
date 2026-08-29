package club.plutoproject.nix.contentsystem.hook.context;

import club.plutoproject.nix.contentsystem.hook.DefaultResultBehavior;

import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;

/**
 * Context for an active item use that finishes normally.
 */
@ApiStatus.Experimental
@ApiStatus.NonExtendable
public interface ItemFinishUseContext extends DefaultResultBehavior<ItemStack> {

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
}
