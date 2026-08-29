package club.plutoproject.nix.contentsystem.hook.context;

import club.plutoproject.nix.contentsystem.hook.DefaultResultBehavior;
import club.plutoproject.nix.contentsystem.hook.result.ItemMineBlockResult;

import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;

/**
 * Context for an item after a block has been mined.
 */
@ApiStatus.Experimental
@ApiStatus.NonExtendable
public interface ItemMineBlockContext extends DefaultResultBehavior<ItemMineBlockResult> {

    /**
     * Gets the player who mined the block.
     *
     * @return the player
     */
    @Contract(pure = true)
    Player player();

    /**
     * Gets the live main-hand stack.
     *
     * @return the main-hand stack
     */
    @Contract(pure = true)
    ItemStack itemStack();

    /**
     * Gets a copy of the mined block's location.
     *
     * @return the block location
     */
    @Contract(value = "-> new", pure = true)
    Location blockLocation();

    /**
     * Gets the block data captured before removal.
     *
     * @return the mined block data
     */
    @Contract(pure = true)
    BlockData minedBlockData();
}
