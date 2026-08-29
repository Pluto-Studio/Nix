package club.plutoproject.nix.contentsystem.projection;

import io.papermc.paper.datacomponent.DataComponentType;
import org.bukkit.Material;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Mutable vanilla component output for a client projection.
 */
@NullMarked
@ApiStatus.Experimental
@ApiStatus.NonExtendable
public interface ProjectionOutput {

    /**
     * Gets the vanilla material used by the projection.
     *
     * @return the vanilla material
     */
    @Contract(pure = true)
    Material vanillaMaterial();

    /**
     * Gets a valued vanilla component from the current output.
     *
     * @param type the component type
     * @param <T> the component value type
     * @return the current value, or {@code null} when it is not present
     */
    @Contract(pure = true)
    @Nullable <T> T get(DataComponentType.Valued<T> type);

    /**
     * Checks whether a component is present in the current output.
     *
     * @param type the component type
     * @return whether the component is present
     */
    @Contract(pure = true)
    boolean has(DataComponentType type);

    /**
     * Sets a valued component in the projected output.
     *
     * @param type the component type
     * @param value the component value
     * @param <T> the component value type
     */
    <T> void set(DataComponentType.Valued<T> type, T value);

    /**
     * Sets a non-valued marker component in the projected output.
     *
     * @param type the marker component type
     */
    void set(DataComponentType.NonValued type);

    /**
     * Removes a component from the projected output.
     *
     * @param type the component type
     */
    void unset(DataComponentType type);
}
