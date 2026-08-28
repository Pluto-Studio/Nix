package club.plutoproject.nix.contentsystem;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;

/**
 * Context for using an item on a block.
 */
@ApiStatus.Experimental
@ApiStatus.NonExtendable
public interface ItemUseOnBlockContext extends HeldItemContext, DefaultResultBehavior<ItemInteractionResult> {

    /**
     * Gets the clicked block. The block is live.
     *
     * @return the clicked block
     */
    @Contract(pure = true)
    Block clickedBlock();

    /**
     * Gets the face that was clicked.
     *
     * @return the clicked face
     */
    @Contract(pure = true)
    BlockFace clickedFace();

    /**
     * Gets a defensive copy of the interaction point.
     *
     * @return the interaction point
     */
    @Contract(value = "-> new", pure = true)
    Location interactionPoint();

    /**
     * Gets whether the hit occurred inside the clicked block.
     *
     * @return whether the hit was inside the block
     */
    @Contract(pure = true)
    boolean insideBlock();

    /**
     * Gets whether the hit was blocked by the world border.
     *
     * @return whether the world border was hit
     */
    @Contract(pure = true)
    boolean hitWorldBorder();

    /**
     * Gets whether the player is using the secondary-use action.
     *
     * @return whether secondary use is active
     */
    @Contract(pure = true)
    boolean secondaryUseActive();
}
