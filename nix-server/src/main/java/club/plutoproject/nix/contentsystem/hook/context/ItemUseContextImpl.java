package club.plutoproject.nix.contentsystem.hook.context;

import org.jetbrains.annotations.ApiStatus;
import club.plutoproject.nix.contentsystem.hook.DefaultCall;
import club.plutoproject.nix.contentsystem.hook.result.ItemUseResult;
import club.plutoproject.nix.contentsystem.hook.context.ItemUseContext;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

@ApiStatus.Internal
public final class ItemUseContextImpl extends HeldItemContextBase implements ItemUseContext {
    private final DefaultCall<ItemUseResult> defaults;

    public ItemUseContextImpl(
        final Player player,
        final ItemStack itemStack,
        final EquipmentSlot hand,
        final DefaultCall<ItemUseResult> defaults
    ) {
        super(player, itemStack, hand);
        this.defaults = defaults;
    }

    @Override
    public ItemUseResult defaultBehavior() {
        return this.defaults.call();
    }
}
