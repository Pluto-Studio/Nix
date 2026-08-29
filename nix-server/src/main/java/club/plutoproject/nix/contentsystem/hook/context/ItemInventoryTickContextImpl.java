package club.plutoproject.nix.contentsystem.hook.context;

import org.jetbrains.annotations.ApiStatus;
import club.plutoproject.nix.contentsystem.hook.DefaultCall;
import club.plutoproject.nix.contentsystem.hook.context.ItemInventoryTickContext;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

@ApiStatus.Internal
public final class ItemInventoryTickContextImpl implements ItemInventoryTickContext {
    private final Entity entity;
    private final ItemStack itemStack;
    private final @Nullable EquipmentSlot equipmentSlot;
    private final DefaultCall<Void> defaults;

    public ItemInventoryTickContextImpl(
        final Entity entity,
        final ItemStack itemStack,
        final @Nullable EquipmentSlot equipmentSlot,
        final DefaultCall<Void> defaults
    ) {
        this.entity = entity;
        this.itemStack = itemStack;
        this.equipmentSlot = equipmentSlot;
        this.defaults = defaults;
    }

    @Override
    public Entity entity() {
        return this.entity;
    }

    @Override
    public ItemStack itemStack() {
        return this.itemStack;
    }

    @Override
    public @Nullable EquipmentSlot equipmentSlot() {
        return this.equipmentSlot;
    }

    @Override
    public void runDefaultBehavior() {
        this.defaults.call();
    }
}
