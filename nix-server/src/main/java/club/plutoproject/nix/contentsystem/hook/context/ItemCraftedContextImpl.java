package club.plutoproject.nix.contentsystem.hook.context;

import org.jetbrains.annotations.ApiStatus;
import club.plutoproject.nix.contentsystem.hook.DefaultCall;
import club.plutoproject.nix.contentsystem.hook.context.ItemCraftedContext;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

@ApiStatus.Internal
public final class ItemCraftedContextImpl implements ItemCraftedContext {
    private final ItemStack itemStack;
    private final int craftedAmount;
    private final @Nullable Player player;
    private final DefaultCall<Void> defaults;

    public ItemCraftedContextImpl(
        final ItemStack itemStack,
        final int craftedAmount,
        final @Nullable Player player,
        final DefaultCall<Void> defaults
    ) {
        this.itemStack = itemStack;
        this.craftedAmount = craftedAmount;
        this.player = player;
        this.defaults = defaults;
    }

    @Override
    public ItemStack itemStack() {
        return this.itemStack;
    }

    @Override
    public int craftedAmount() {
        return this.craftedAmount;
    }

    @Override
    public @Nullable Player player() {
        return this.player;
    }

    @Override
    public void runDefaultBehavior() {
        this.defaults.call();
    }
}
