package club.plutoproject.nix.contentsystem.hook.context;

import org.jetbrains.annotations.ApiStatus;
import club.plutoproject.nix.contentsystem.hook.DefaultCall;
import club.plutoproject.nix.contentsystem.hook.result.ItemReleaseUseResult;
import club.plutoproject.nix.contentsystem.hook.context.ItemReleaseUseContext;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

@ApiStatus.Internal
public final class ItemReleaseUseContextImpl implements ItemReleaseUseContext {
    private final LivingEntity entity;
    private final ItemStack itemStack;
    private final EquipmentSlot hand;
    private final int remainingTicks;
    private final int usedTicks;
    private final DefaultCall<ItemReleaseUseResult> defaults;

    public ItemReleaseUseContextImpl(
        final LivingEntity entity,
        final ItemStack itemStack,
        final EquipmentSlot hand,
        final int remainingTicks,
        final int usedTicks,
        final DefaultCall<ItemReleaseUseResult> defaults
    ) {
        this.entity = entity;
        this.itemStack = itemStack;
        this.hand = hand;
        this.remainingTicks = remainingTicks;
        this.usedTicks = usedTicks;
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
    public int remainingTicks() {
        return this.remainingTicks;
    }

    @Override
    public int usedTicks() {
        return this.usedTicks;
    }

    @Override
    public ItemReleaseUseResult defaultBehavior() {
        return this.defaults.call();
    }
}
