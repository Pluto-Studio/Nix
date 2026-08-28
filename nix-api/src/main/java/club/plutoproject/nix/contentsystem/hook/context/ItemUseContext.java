package club.plutoproject.nix.contentsystem.hook.context;

import club.plutoproject.nix.contentsystem.hook.DefaultResultBehavior;
import club.plutoproject.nix.contentsystem.hook.result.ItemUseResult;

import org.jetbrains.annotations.ApiStatus;

/**
 * Context for using an item without a block target.
 */
@ApiStatus.Experimental
@ApiStatus.NonExtendable
public interface ItemUseContext extends HeldItemContext, DefaultResultBehavior<ItemUseResult> {
}
