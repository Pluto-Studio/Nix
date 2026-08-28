package club.plutoproject.nix.contentsystem.hook.result;

import org.jetbrains.annotations.ApiStatus;

/**
 * Determines whether normal after-use effects are applied when an active use
 * is released or interrupted.
 */
@ApiStatus.Experimental
public enum ItemReleaseUseResult {

    /** Apply the item's use remainder and use cooldown effects. */
    APPLY_AFTER_USE_EFFECTS,
    /** Skip the item's use remainder and use cooldown effects. */
    SKIP_AFTER_USE_EFFECTS
}
