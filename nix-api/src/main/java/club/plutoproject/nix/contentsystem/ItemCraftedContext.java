package club.plutoproject.nix.contentsystem;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.Nullable;

/**
 * Context for an item produced by crafting or an automated crafter.
 */
@ApiStatus.Experimental
@ApiStatus.NonExtendable
public interface ItemCraftedContext extends DefaultVoidBehavior {

    /**
     * Gets the live result stack.
     *
     * @return the crafted stack
     */
    @Contract(pure = true)
    ItemStack itemStack();

    /**
     * Gets the number of items crafted.
     *
     * @return the positive crafted amount
     */
    @Contract(pure = true)
    int craftedAmount();

    /**
     * Gets the crafting player, or {@code null} for automated crafting.
     *
     * @return the player or {@code null}
     */
    @Contract(pure = true)
    @Nullable Player player();

    /**
     * Gets whether this invocation is automated.
     *
     * @return {@code true} when no player is associated with the invocation
     */
    @Contract(pure = true)
    default boolean automated() {
        return this.player() == null;
    }
}
