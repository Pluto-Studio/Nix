package club.plutoproject.nix.contentsystem.hook.context;

import org.jetbrains.annotations.ApiStatus;
import club.plutoproject.nix.contentsystem.hook.DefaultCall;
import club.plutoproject.nix.contentsystem.hook.result.ItemMineBlockResult;
import club.plutoproject.nix.contentsystem.hook.context.ItemMineBlockContext;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

@ApiStatus.Internal
public final class ItemMineBlockContextImpl implements ItemMineBlockContext {
    private final Player player;
    private final ItemStack itemStack;
    private final Location blockLocation;
    private final BlockData minedBlockData;
    private final DefaultCall<ItemMineBlockResult> defaults;

    public ItemMineBlockContextImpl(
        final Player player,
        final ItemStack itemStack,
        final Location blockLocation,
        final BlockData minedBlockData,
        final DefaultCall<ItemMineBlockResult> defaults
    ) {
        this.player = player;
        this.itemStack = itemStack;
        this.blockLocation = blockLocation;
        this.minedBlockData = minedBlockData;
        this.defaults = defaults;
    }

    @Override
    public Player player() {
        return this.player;
    }

    @Override
    public ItemStack itemStack() {
        return this.itemStack;
    }

    @Override
    public Location blockLocation() {
        return this.blockLocation.clone();
    }

    @Override
    public BlockData minedBlockData() {
        return this.minedBlockData;
    }

    @Override
    public ItemMineBlockResult defaultBehavior() {
        return this.defaults.call();
    }
}
