package club.plutoproject.nix.contentsystem;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.NullMarked;

/**
 * Callback used by a result-producing gameplay hook.
 *
 * @param <C> the hook context type
 * @param <R> the hook result type
 */
@FunctionalInterface
@NullMarked
@ApiStatus.Experimental
public interface ItemHookHandler<C, R> {

    /**
     * Handles a gameplay hook invocation.
     *
     * @param context the callback-scoped hook context
     * @return the hook result
     */
    R handle(C context);
}
