package club.plutoproject.nix.contentsystem.hook.result;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NullMarked;

/** Result of an item interaction. */
@NullMarked
@ApiStatus.Experimental
@ApiStatus.NonExtendable
public class ItemInteractionResult {

    /** The operation selected by an interaction result. */
    public enum Kind {
        PASS,
        FAIL,
        SUCCESS,
        CONSUME,
        TRY_EMPTY_HAND
    }

    private static final ItemInteractionResult PASS = new ItemInteractionResult(Kind.PASS);
    private static final ItemInteractionResult FAIL = new ItemInteractionResult(Kind.FAIL);
    private static final ItemInteractionResult SUCCESS = new ItemInteractionResult(Kind.SUCCESS);
    private static final ItemInteractionResult CONSUME = new ItemInteractionResult(Kind.CONSUME);
    private static final ItemInteractionResult TRY_EMPTY_HAND = new ItemInteractionResult(Kind.TRY_EMPTY_HAND);

    private final Kind kind;

    /**
     * Creates a result with the supplied operation.
     *
     * <p>This constructor is protected for server-side result adapters. Plugin
     * code should use the factory methods.</p>
     */
    protected ItemInteractionResult(final Kind kind) {
        this.kind = kind;
    }

    /**
     * Gets the operation represented by this result.
     *
     * @return the result kind
     */
    @Contract(pure = true)
    public final Kind kind() {
        return this.kind;
    }

    /** Continues the interaction pipeline without handling the interaction. */
    @Contract(pure = true)
    public static ItemInteractionResult pass() {
        return PASS;
    }

    /** Fails the interaction. */
    @Contract(pure = true)
    public static ItemInteractionResult fail() {
        return FAIL;
    }

    /** Handles the interaction and requests a server-side swing. */
    @Contract(pure = true)
    public static ItemInteractionResult success() {
        return SUCCESS;
    }

    /** Handles the interaction without requesting a swing. */
    @Contract(pure = true)
    public static ItemInteractionResult consume() {
        return CONSUME;
    }

    /** Continues a block-use pipeline with the clicked block's empty-hand behavior. */
    @Contract(pure = true)
    public static ItemInteractionResult tryEmptyHand() {
        return TRY_EMPTY_HAND;
    }
}
