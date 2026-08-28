package club.plutoproject.nix.contentsystem;

import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * API-side representation used by the interaction result factories.
 *
 * <p>The server bridge may interpret the result at the corresponding vanilla
 * interaction boundary.</p>
 */
final class ItemInteractionResultImpl implements ItemUseResult {

    enum Kind {
        PASS,
        FAIL,
        SUCCESS,
        CONSUME,
        TRY_EMPTY_HAND
    }

    static final ItemInteractionResultImpl PASS = new ItemInteractionResultImpl(Kind.PASS, null);
    static final ItemInteractionResultImpl FAIL = new ItemInteractionResultImpl(Kind.FAIL, null);
    static final ItemInteractionResultImpl SUCCESS = new ItemInteractionResultImpl(Kind.SUCCESS, null);
    static final ItemInteractionResultImpl CONSUME = new ItemInteractionResultImpl(Kind.CONSUME, null);
    static final ItemInteractionResultImpl TRY_EMPTY_HAND = new ItemInteractionResultImpl(Kind.TRY_EMPTY_HAND, null);

    private final Kind kind;
    private final @Nullable ItemStack transformedHeldItem;

    private ItemInteractionResultImpl(final Kind kind, @Nullable final ItemStack transformedHeldItem) {
        this.kind = kind;
        this.transformedHeldItem = transformedHeldItem;
    }

    static ItemInteractionResultImpl transformed(final Kind kind, final ItemStack transformedHeldItem) {
        return new ItemInteractionResultImpl(kind, transformedHeldItem.clone());
    }

    Kind kind() {
        return this.kind;
    }

    @Nullable ItemStack transformedHeldItem() {
        return this.transformedHeldItem;
    }
}
