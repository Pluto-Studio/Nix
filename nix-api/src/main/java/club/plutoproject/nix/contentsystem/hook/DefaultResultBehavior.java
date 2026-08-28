package club.plutoproject.nix.contentsystem.hook;

import org.jetbrains.annotations.ApiStatus;

/**
 * Provides access to the underlying default operation for a result-producing
 * gameplay hook context.
 *
 * @param <R> the default result type
 */
@ApiStatus.Experimental
@ApiStatus.NonExtendable
public interface DefaultResultBehavior<R> {

    /**
     * Runs the underlying default operation.
     *
     * <p>Every invocation runs the operation again; callers should not assume
     * that it is automatically limited to one invocation.</p>
     *
     * @return the default result
     */
    R defaultBehavior();
}
