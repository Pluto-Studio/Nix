package club.plutoproject.nix.contentsystem.hook.context;

import org.jetbrains.annotations.ApiStatus;
import club.plutoproject.nix.contentsystem.hook.context.HeldItemContext;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

@ApiStatus.Internal
public abstract class HeldItemContextBase implements HeldItemContext {
    private final Player player;
    private final ItemStack itemStack;
    private final EquipmentSlot hand;

    public HeldItemContextBase(final Player player, final ItemStack itemStack, final EquipmentSlot hand) {
        this.player = player;
        this.itemStack = itemStack;
        this.hand = hand;
    }

    @Override
    public final Player player() {
        return this.player;
    }

    @Override
    public final ItemStack itemStack() {
        return this.itemStack;
    }

    @Override
    public final EquipmentSlot hand() {
        return this.hand;
    }
}
