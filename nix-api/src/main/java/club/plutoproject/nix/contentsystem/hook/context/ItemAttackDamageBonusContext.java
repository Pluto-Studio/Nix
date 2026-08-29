package club.plutoproject.nix.contentsystem.hook.context;

import club.plutoproject.nix.contentsystem.hook.DefaultResultBehavior;

import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;

/**
 * Context for calculating an item's additive attack damage bonus.
 */
@ApiStatus.Experimental
@ApiStatus.NonExtendable
public interface ItemAttackDamageBonusContext extends DefaultResultBehavior<Float> {

    /**
     * Gets the attacking entity.
     *
     * @return the attacker
     */
    @Contract(pure = true)
    LivingEntity attacker();

    /**
     * Gets the attacked entity.
     *
     * @return the victim
     */
    @Contract(pure = true)
    Entity victim();

    /**
     * Gets the exact live weapon stack.
     *
     * @return the weapon stack
     */
    @Contract(pure = true)
    ItemStack weapon();

    /**
     * Gets the damage before this item's bonus is applied.
     *
     * @return the pre-bonus damage
     */
    @Contract(pure = true)
    float damageBeforeBonus();

    /**
     * Gets the Bukkit damage source for the attack.
     *
     * @return the damage source
     */
    @Contract(pure = true)
    DamageSource damageSource();
}
