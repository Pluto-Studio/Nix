package club.plutoproject.nix.contentsystem.hook.context;

import org.jetbrains.annotations.ApiStatus;
import club.plutoproject.nix.contentsystem.hook.DefaultCall;
import club.plutoproject.nix.contentsystem.hook.context.ItemFinishUseContext;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

@ApiStatus.Internal
public final class ItemFinishUseContextImpl implements ItemFinishUseContext {
    private final LivingEntity entity;
    private final ItemStack itemStack;
    private final EquipmentSlot hand;
    private final DefaultCall<ItemStack> defaults;

    public ItemFinishUseContextImpl(
        final LivingEntity entity,
        final ItemStack itemStack,
        final EquipmentSlot hand,
        final DefaultCall<ItemStack> defaults
    ) {
        this.entity = entity;
        this.itemStack = itemStack;
        this.hand = hand;
        this.defaults = defaults;
    }

    @Override
    public LivingEntity entity() {
        return this.entity;
    }

    @Override
    public ItemStack itemStack() {
        return this.itemStack;
    }

    @Override
    public EquipmentSlot hand() {
        return this.hand;
    }

    @Override
    public ItemStack defaultBehavior() {
        return this.defaults.call();
    }
}
