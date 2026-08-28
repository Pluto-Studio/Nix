package club.plutoproject.nix.contentsystem.hook.context;

import org.jetbrains.annotations.ApiStatus;
import club.plutoproject.nix.contentsystem.hook.DefaultCall;
import club.plutoproject.nix.contentsystem.hook.context.ItemBlockStateContext;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;

@ApiStatus.Internal
public final class ItemBlockStateContextImpl<R> implements ItemBlockStateContext<R> {
    private final ItemStack itemStack;
    private final BlockData blockData;
    private final DefaultCall<R> defaults;

    public ItemBlockStateContextImpl(final ItemStack itemStack, final BlockData blockData, final DefaultCall<R> defaults) {
        this.itemStack = itemStack;
        this.blockData = blockData;
        this.defaults = defaults;
    }

    @Override
    public ItemStack itemStack() {
        return this.itemStack;
    }

    @Override
    public BlockData blockData() {
        return this.blockData;
    }

    @Override
    public R defaultBehavior() {
        return this.defaults.call();
    }
}
