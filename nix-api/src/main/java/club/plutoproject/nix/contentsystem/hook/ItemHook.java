package club.plutoproject.nix.contentsystem.hook;

import org.jetbrains.annotations.ApiStatus;

/**
 * Closed descriptor for one Content System gameplay hook.
 *
 * @param <C> the hook context type
 * @param <R> the hook result type
 */
@ApiStatus.Experimental
public final class ItemHook<C, R> {

    private ItemHook() {
    }

    static <C, R> ItemHook<C, R> create() {
        return new ItemHook<>();
    }
}
