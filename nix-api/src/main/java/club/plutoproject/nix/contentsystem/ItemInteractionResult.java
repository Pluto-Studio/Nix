package club.plutoproject.nix.contentsystem;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NullMarked;

/**
 * Result of an item interaction.
 */
@NullMarked
@ApiStatus.Experimental
@ApiStatus.NonExtendable
public interface ItemInteractionResult {

    /**
     * Continues the interaction pipeline without handling the interaction.
     *
     * @return the pass result
     */
    @Contract(pure = true)
    static ItemInteractionResult pass() {
        return ItemInteractionResultImpl.PASS;
    }

    /**
     * Fails the interaction.
     *
     * @return the fail result
     */
    @Contract(pure = true)
    static ItemInteractionResult fail() {
        return ItemInteractionResultImpl.FAIL;
    }

    /**
     * Handles the interaction and requests a server-side swing.
     *
     * @return the success result
     */
    @Contract(pure = true)
    static ItemInteractionResult success() {
        return ItemInteractionResultImpl.SUCCESS;
    }

    /**
     * Handles the interaction without requesting a swing.
     *
     * @return the consume result
     */
    @Contract(pure = true)
    static ItemInteractionResult consume() {
        return ItemInteractionResultImpl.CONSUME;
    }

    /**
     * Continues a block-use pipeline with the clicked block's empty-hand
     * behavior.
     *
     * @return the try-empty-hand result
     */
    @Contract(pure = true)
    static ItemInteractionResult tryEmptyHand() {
        return ItemInteractionResultImpl.TRY_EMPTY_HAND;
    }
}
