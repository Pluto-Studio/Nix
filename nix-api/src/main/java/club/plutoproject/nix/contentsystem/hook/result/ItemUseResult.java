package club.plutoproject.nix.contentsystem.hook.result;

import java.util.Objects;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** Result of using an item without a block target. */
@NullMarked
@ApiStatus.Experimental
@ApiStatus.NonExtendable
public class ItemUseResult extends ItemInteractionResult {

    private static final ItemUseResult PASS = new ItemUseResult(Kind.PASS);
    private static final ItemUseResult FAIL = new ItemUseResult(Kind.FAIL);
    private static final ItemUseResult SUCCESS = new ItemUseResult(Kind.SUCCESS);
    private static final ItemUseResult CONSUME = new ItemUseResult(Kind.CONSUME);

    private final @Nullable ItemStack transformedHeldItem;

    /** Creates a result for a server-side result adapter. */
    protected ItemUseResult(final Kind kind) {
        this(kind, null);
    }

    /** Creates a result with an immediately copied transformed held stack. */
    protected ItemUseResult(final Kind kind, final @Nullable ItemStack transformedHeldItem) {
        super(kind);
        this.transformedHeldItem = transformedHeldItem == null ? null : transformedHeldItem.clone();
    }

    /** Continues the interaction pipeline without handling the use. */
    @Contract(pure = true)
    public static ItemUseResult pass() {
        return PASS;
    }

    /** Fails the use. */
    @Contract(pure = true)
    public static ItemUseResult fail() {
        return FAIL;
    }

    /** Handles the use and requests a server-side swing. */
    @Contract(pure = true)
    public static ItemUseResult success() {
        return SUCCESS;
    }

    /** Handles the use and replaces the held stack with an immediate copy. */
    @Contract(value = "_ -> new", pure = true)
    public static ItemUseResult success(final ItemStack transformedHeldItem) {
        return new ItemUseResult(Kind.SUCCESS, Objects.requireNonNull(transformedHeldItem, "transformedHeldItem"));
    }

    /** Handles the use without requesting a swing. */
    @Contract(pure = true)
    public static ItemUseResult consume() {
        return CONSUME;
    }

    /** Handles the use without a swing and replaces the held stack with a copy. */
    @Contract(value = "_ -> new", pure = true)
    public static ItemUseResult consume(final ItemStack transformedHeldItem) {
        return new ItemUseResult(Kind.CONSUME, Objects.requireNonNull(transformedHeldItem, "transformedHeldItem"));
    }

    /**
     * Gets a defensive copy of the transformed held stack, if one was supplied.
     *
     * @return the transformed stack or {@code null}
     */
    @Contract(value = "-> new", pure = true)
    public final @Nullable ItemStack transformedHeldItem() {
        return this.transformedHeldItem == null ? null : this.transformedHeldItem.clone();
    }
}
