package club.plutoproject.nix.contentsystem;

import org.jetbrains.annotations.ApiStatus;

/**
 * Context for using an item without a block target.
 */
@ApiStatus.Experimental
@ApiStatus.NonExtendable
public interface ItemUseContext extends HeldItemContext, DefaultResultBehavior<ItemUseResult> {
}
