package club.plutoproject.nix.contentsystem;

import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;

/**
 * Context for a successful attack against a living victim.
 */
@ApiStatus.Experimental
@ApiStatus.NonExtendable
public interface ItemHurtEntityContext extends DefaultVoidBehavior {

    /**
     * Gets the attacking entity.
     *
     * @return the attacker
     */
    @Contract(pure = true)
    LivingEntity attacker();

    /**
     * Gets the attacked living entity.
     *
     * @return the victim
     */
    @Contract(pure = true)
    LivingEntity victim();

    /**
     * Gets the exact live weapon stack.
     *
     * @return the weapon stack
     */
    @Contract(pure = true)
    ItemStack weapon();
}
