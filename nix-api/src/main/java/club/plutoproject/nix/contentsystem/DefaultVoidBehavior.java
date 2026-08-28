package club.plutoproject.nix.contentsystem;

import org.jetbrains.annotations.ApiStatus;

/**
 * Provides access to the underlying default operation for a void gameplay
 * hook context.
 */
@ApiStatus.Experimental
@ApiStatus.NonExtendable
public interface DefaultVoidBehavior {

    /**
     * Runs the underlying default operation.
     *
     * <p>Every invocation runs the operation again; callers should not assume
     * that it is automatically limited to one invocation.</p>
     */
    void runDefaultBehavior();
}
