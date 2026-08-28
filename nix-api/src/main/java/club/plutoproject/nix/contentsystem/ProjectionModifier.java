package club.plutoproject.nix.contentsystem;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.NullMarked;

/**
 * Maps custom component state to client-visible vanilla component state.
 */
@NullMarked
@ApiStatus.Experimental
public interface ProjectionModifier {

    /**
     * Projection modifier for a valued component.
     *
     * @param <T> the component value type
     */
    @FunctionalInterface
    interface Valued<T> extends ProjectionModifier {

        /**
         * Applies this modifier to projected output.
         *
         * @param context projection context
         * @param value the effective component value
         * @param output mutable projected output
         */
        void apply(ProjectionContext context, T value, ProjectionOutput output);
    }

    /**
     * Projection modifier for a non-valued marker component.
     */
    @FunctionalInterface
    interface NonValued extends ProjectionModifier {

        /**
         * Applies this modifier to projected output.
         *
         * @param context projection context
         * @param output mutable projected output
         */
        void apply(ProjectionContext context, ProjectionOutput output);
    }
}
