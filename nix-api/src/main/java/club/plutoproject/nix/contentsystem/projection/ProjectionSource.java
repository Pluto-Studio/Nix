package club.plutoproject.nix.contentsystem.projection;

import io.papermc.paper.datacomponent.DataComponentType;
import org.bukkit.inventory.ItemType;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Read-only runtime item state supplied to a projection modifier.
 */
@NullMarked
@ApiStatus.Experimental
@ApiStatus.NonExtendable
public interface ProjectionSource {

    /**
     * Gets the runtime item type.
     *
     * @return the item type
     */
    @Contract(pure = true)
    ItemType itemType();

    /**
     * Gets the runtime stack amount.
     *
     * @return the amount
     */
    @Contract(pure = true)
    int amount();

    /**
     * Gets a valued component from the runtime stack.
     *
     * @param type the component type
     * @param <T> the component value type
     * @return the effective value, or {@code null} when it is not present
     */
    @Contract(pure = true)
    @Nullable <T> T getData(DataComponentType.Valued<T> type);

    /**
     * Checks whether a component is present on the runtime stack.
     *
     * @param type the component type
     * @return whether the component is present
     */
    @Contract(pure = true)
    boolean hasData(DataComponentType type);
}
