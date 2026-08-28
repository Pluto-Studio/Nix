package club.plutoproject.nix.contentsystem.hook.context;

import org.jetbrains.annotations.ApiStatus;
import club.plutoproject.nix.contentsystem.hook.DefaultCall;
import club.plutoproject.nix.contentsystem.hook.context.ItemAttackDamageBonusContext;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;

@ApiStatus.Internal
public final class ItemAttackDamageBonusContextImpl implements ItemAttackDamageBonusContext {
    private final LivingEntity attacker;
    private final Entity victim;
    private final ItemStack weapon;
    private final float damageBeforeBonus;
    private final DamageSource damageSource;
    private final DefaultCall<Float> defaults;

    public ItemAttackDamageBonusContextImpl(
        final LivingEntity attacker,
        final Entity victim,
        final ItemStack weapon,
        final float damageBeforeBonus,
        final DamageSource damageSource,
        final DefaultCall<Float> defaults
    ) {
        this.attacker = attacker;
        this.victim = victim;
        this.weapon = weapon;
        this.damageBeforeBonus = damageBeforeBonus;
        this.damageSource = damageSource;
        this.defaults = defaults;
    }

    @Override
    public LivingEntity attacker() {
        return this.attacker;
    }

    @Override
    public Entity victim() {
        return this.victim;
    }

    @Override
    public ItemStack weapon() {
        return this.weapon;
    }

    @Override
    public float damageBeforeBonus() {
        return this.damageBeforeBonus;
    }

    @Override
    public DamageSource damageSource() {
        return this.damageSource;
    }

    @Override
    public Float defaultBehavior() {
        return this.defaults.call();
    }
}
