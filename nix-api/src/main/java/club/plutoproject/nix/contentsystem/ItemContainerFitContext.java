package club.plutoproject.nix.contentsystem;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;

/**
 * Context for checking whether an item can fit inside a container item.
 */
@ApiStatus.Experimental
@ApiStatus.NonExtendable
public interface ItemContainerFitContext extends DefaultResultBehavior<Boolean> {

    /**
     * Gets the live item stack being checked.
     *
     * @return the item stack
     */
    @Contract(pure = true)
    ItemStack itemStack();
}
