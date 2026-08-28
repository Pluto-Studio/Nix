package club.plutoproject.nix.contentsystem.hook.context;

import club.plutoproject.nix.contentsystem.hook.DefaultVoidBehavior;

import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;

/**
 * Context for a dropped item entity destroyed by damage.
 */
@ApiStatus.Experimental
@ApiStatus.NonExtendable
public interface ItemDestroyedAsEntityContext extends DefaultVoidBehavior {

    /**
     * Gets the item entity that is about to be discarded.
     *
     * @return the item entity
     */
    @Contract(pure = true)
    Item itemEntity();

    /**
     * Gets the live stack carried by the item entity.
     *
     * @return the item stack
     */
    @Contract(pure = true)
    ItemStack itemStack();

    /**
     * Gets the Bukkit damage source.
     *
     * @return the damage source
     */
    @Contract(pure = true)
    DamageSource damageSource();
}
