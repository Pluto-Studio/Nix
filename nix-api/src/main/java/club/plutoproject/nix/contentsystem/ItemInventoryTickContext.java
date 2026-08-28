package club.plutoproject.nix.contentsystem;

import org.bukkit.entity.Entity;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.Nullable;

/**
 * Context for an item tick in an inventory.
 */
@ApiStatus.Experimental
@ApiStatus.NonExtendable
public interface ItemInventoryTickContext extends DefaultVoidBehavior {

    /**
     * Gets the owning entity.
     *
     * @return the entity
     */
    @Contract(pure = true)
    Entity entity();

    /**
     * Gets the live inventory stack.
     *
     * @return the item stack
     */
    @Contract(pure = true)
    ItemStack itemStack();

    /**
     * Gets the equipment slot, or {@code null} for ordinary inventory storage.
     *
     * @return the equipment slot or {@code null}
     */
    @Contract(pure = true)
    @Nullable EquipmentSlot equipmentSlot();
}
