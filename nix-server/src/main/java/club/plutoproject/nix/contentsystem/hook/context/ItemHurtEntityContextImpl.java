package club.plutoproject.nix.contentsystem.hook.context;

import org.jetbrains.annotations.ApiStatus;
import club.plutoproject.nix.contentsystem.hook.DefaultCall;
import club.plutoproject.nix.contentsystem.hook.context.ItemHurtEntityContext;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;

@ApiStatus.Internal
public final class ItemHurtEntityContextImpl implements ItemHurtEntityContext {
    private final LivingEntity attacker;
    private final LivingEntity victim;
    private final ItemStack weapon;
    private final DefaultCall<Void> defaults;

    public ItemHurtEntityContextImpl(
        final LivingEntity attacker,
        final LivingEntity victim,
        final ItemStack weapon,
        final DefaultCall<Void> defaults
    ) {
        this.attacker = attacker;
        this.victim = victim;
        this.weapon = weapon;
        this.defaults = defaults;
    }

    @Override
    public LivingEntity attacker() {
        return this.attacker;
    }

    @Override
    public LivingEntity victim() {
        return this.victim;
    }

    @Override
    public ItemStack weapon() {
        return this.weapon;
    }

    @Override
    public void runDefaultBehavior() {
        this.defaults.call();
    }
}
