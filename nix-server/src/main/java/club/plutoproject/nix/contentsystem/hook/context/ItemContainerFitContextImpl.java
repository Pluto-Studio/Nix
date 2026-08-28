package club.plutoproject.nix.contentsystem.hook.context;

import org.jetbrains.annotations.ApiStatus;
import club.plutoproject.nix.contentsystem.hook.DefaultCall;
import club.plutoproject.nix.contentsystem.hook.context.ItemContainerFitContext;
import org.bukkit.inventory.ItemStack;

@ApiStatus.Internal
public final class ItemContainerFitContextImpl implements ItemContainerFitContext {
    private final ItemStack itemStack;
    private final DefaultCall<Boolean> defaults;

    public ItemContainerFitContextImpl(final ItemStack itemStack, final DefaultCall<Boolean> defaults) {
        this.itemStack = itemStack;
        this.defaults = defaults;
    }

    @Override
    public ItemStack itemStack() {
        return this.itemStack;
    }

    @Override
    public Boolean defaultBehavior() {
        return this.defaults.call();
    }
}
