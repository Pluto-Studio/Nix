package club.plutoproject.nix.contentsystem;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NullMarked;

/**
 * Context supplied to a projection modifier.
 */
@NullMarked
@ApiStatus.Experimental
@ApiStatus.NonExtendable
public interface ProjectionContext {

    /**
     * Gets the player for whom the item is being projected.
     *
     * @return the viewer
     */
    @Contract(pure = true)
    Player viewer();

    /**
     * Gets the runtime source item data.
     *
     * @return the projection source
     */
    @Contract(pure = true)
    ProjectionSource source();
}
