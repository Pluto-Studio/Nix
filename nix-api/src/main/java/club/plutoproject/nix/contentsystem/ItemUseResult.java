package club.plutoproject.nix.contentsystem;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NullMarked;

/**
 * Result of using an item without a block target.
 *
 * <p>This result has the same interaction operations as
 * {@link ItemInteractionResult} and can additionally replace the held stack.
 * A replacement is copied when the factory is called.</p>
 */
@NullMarked
@ApiStatus.Experimental
@ApiStatus.NonExtendable
public interface ItemUseResult extends ItemInteractionResult {

    /**
     * Continues the interaction pipeline without handling the use.
     *
     * @return the pass result
     */
    @Contract(pure = true)
    static ItemUseResult pass() {
        return ItemInteractionResultImpl.PASS;
    }

    /**
     * Fails the use.
     *
     * @return the fail result
     */
    @Contract(pure = true)
    static ItemUseResult fail() {
        return ItemInteractionResultImpl.FAIL;
    }

    /**
     * Handles the use and requests a server-side swing.
     *
     * @return the success result
     */
    @Contract(pure = true)
    static ItemUseResult success() {
        return ItemInteractionResultImpl.SUCCESS;
    }

    /**
     * Handles the use, requests a server-side swing, and replaces the held
     * stack with an immediate copy of the supplied stack.
     *
     * @param transformedHeldItem the replacement held stack
     * @return the success result
     */
    @Contract(value = "_ -> new", pure = true)
    static ItemUseResult success(final ItemStack transformedHeldItem) {
        return ItemInteractionResultImpl.transformed(ItemInteractionResultImpl.Kind.SUCCESS, transformedHeldItem);
    }

    /**
     * Handles the use without requesting a swing.
     *
     * @return the consume result
     */
    @Contract(pure = true)
    static ItemUseResult consume() {
        return ItemInteractionResultImpl.CONSUME;
    }

    /**
     * Handles the use without requesting a swing and replaces the held stack
     * with an immediate copy of the supplied stack.
     *
     * @param transformedHeldItem the replacement held stack
     * @return the consume result
     */
    @Contract(value = "_ -> new", pure = true)
    static ItemUseResult consume(final ItemStack transformedHeldItem) {
        return ItemInteractionResultImpl.transformed(ItemInteractionResultImpl.Kind.CONSUME, transformedHeldItem);
    }
}
