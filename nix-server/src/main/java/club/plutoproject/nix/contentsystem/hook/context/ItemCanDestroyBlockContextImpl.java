package club.plutoproject.nix.contentsystem.hook.context;

import org.jetbrains.annotations.ApiStatus;
import club.plutoproject.nix.contentsystem.hook.DefaultCall;
import club.plutoproject.nix.contentsystem.hook.context.ItemCanDestroyBlockContext;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;

@ApiStatus.Internal
public final class ItemCanDestroyBlockContextImpl implements ItemCanDestroyBlockContext {
    private final LivingEntity entity;
    private final ItemStack itemStack;
    private final Block block;
    private final BlockData blockData;
    private final DefaultCall<Boolean> defaults;

    public ItemCanDestroyBlockContextImpl(
        final LivingEntity entity,
        final ItemStack itemStack,
        final Block block,
        final BlockData blockData,
        final DefaultCall<Boolean> defaults
    ) {
        this.entity = entity;
        this.itemStack = itemStack;
        this.block = block;
        this.blockData = blockData;
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
    public Block block() {
        return this.block;
    }

    @Override
    public BlockData blockData() {
        return this.blockData;
    }

    @Override
    public Boolean defaultBehavior() {
        return this.defaults.call();
    }
}
