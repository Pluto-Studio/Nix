package club.plutoproject.nix.contentsystem.hook.context;

import club.plutoproject.nix.contentsystem.hook.DefaultResultBehavior;

import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;

/**
 * Context for deciding whether an entity can destroy a block with an item.
 */
@ApiStatus.Experimental
@ApiStatus.NonExtendable
public interface ItemCanDestroyBlockContext extends DefaultResultBehavior<Boolean> {

    /**
     * Gets the entity performing the query.
     *
     * @return the entity
     */
    @Contract(pure = true)
    LivingEntity entity();

    /**
     * Gets the live item stack.
     *
     * @return the item stack
     */
    @Contract(pure = true)
    ItemStack itemStack();

    /**
     * Gets the live block being queried.
     *
     * @return the block
     */
    @Contract(pure = true)
    Block block();

    /**
     * Gets the captured block data for the query.
     *
     * @return the block data
     */
    @Contract(pure = true)
    BlockData blockData();
}
