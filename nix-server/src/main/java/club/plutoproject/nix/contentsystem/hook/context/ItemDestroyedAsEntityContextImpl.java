package club.plutoproject.nix.contentsystem.hook.context;

import org.jetbrains.annotations.ApiStatus;
import club.plutoproject.nix.contentsystem.hook.DefaultCall;
import club.plutoproject.nix.contentsystem.hook.context.ItemDestroyedAsEntityContext;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;

@ApiStatus.Internal
public final class ItemDestroyedAsEntityContextImpl implements ItemDestroyedAsEntityContext {
    private final Item itemEntity;
    private final ItemStack itemStack;
    private final DamageSource damageSource;
    private final DefaultCall<Void> defaults;

    public ItemDestroyedAsEntityContextImpl(
        final Item itemEntity,
        final ItemStack itemStack,
        final DamageSource damageSource,
        final DefaultCall<Void> defaults
    ) {
        this.itemEntity = itemEntity;
        this.itemStack = itemStack;
        this.damageSource = damageSource;
        this.defaults = defaults;
    }

    @Override
    public Item itemEntity() {
        return this.itemEntity;
    }

    @Override
    public ItemStack itemStack() {
        return this.itemStack;
    }

    @Override
    public DamageSource damageSource() {
        return this.damageSource;
    }

    @Override
    public void runDefaultBehavior() {
        this.defaults.call();
    }
}
