package club.plutoproject.nix.contentsystem;

import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;

/**
 * Context shared by item queries that use a stack and captured block data.
 *
 * @param <R> the query result type
 */
@ApiStatus.Experimental
@ApiStatus.NonExtendable
public interface ItemBlockStateContext<R> extends DefaultResultBehavior<R> {

    /**
     * Gets the live item stack used for the query.
     *
     * @return the item stack
     */
    @Contract(pure = true)
    ItemStack itemStack();

    /**
     * Gets the captured block data.
     *
     * @return the block data
     */
    @Contract(pure = true)
    BlockData blockData();
}
