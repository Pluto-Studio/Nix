package club.plutoproject.nix.contentsystem;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.NullMarked;

/**
 * Server-owned services for the Nix content system.
 */
@NullMarked
@ApiStatus.Experimental
@ApiStatus.NonExtendable
public interface ContentSystem {

    /**
     * Refreshes the projected item state visible to a player.
     *
     * <p>Use this after changing viewer-specific or other external state that
     * affects projection without changing a runtime item stack.</p>
     *
     * @param viewer the player whose visible item projections should be refreshed
     */
    void refreshItemProjections(Player viewer);
}
