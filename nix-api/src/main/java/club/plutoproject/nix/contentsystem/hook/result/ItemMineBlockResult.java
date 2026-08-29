package club.plutoproject.nix.contentsystem.hook.result;

import org.jetbrains.annotations.ApiStatus;

/**
 * Determines whether the item-used statistic is awarded after mining a block.
 */
@ApiStatus.Experimental
public enum ItemMineBlockResult {

    /** Award the item-used statistic. */
    AWARD_ITEM_USED_STAT,
    /** Do not award the item-used statistic. */
    SKIP_ITEM_USED_STAT
}
