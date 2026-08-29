package club.plutoproject.nix.contentsystem.registry;

import club.plutoproject.nix.contentsystem.projection.ProjectionModifier;

import com.mojang.serialization.Codec;
import io.papermc.paper.datacomponent.DataComponentType;
import io.papermc.paper.registry.RegistryBuilder;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;

/**
 * Registration definition for a custom {@link DataComponentType}.
 */
@ApiStatus.Experimental
@ApiStatus.NonExtendable
public interface DataComponentTypeRegistryEntry {

    /**
     * Mutable builder for a custom data component type registration.
     */
    @ApiStatus.NonExtendable
    interface Builder extends RegistryBuilder<DataComponentType> {

        /**
         * Selects a valued component type. A later kind selection replaces the
         * earlier selection.
         *
         * @return this builder
         */
        @Contract(value = "-> this", mutates = "this")
        Builder valued();

        /**
         * Selects a non-valued marker component type. A later kind selection
         * replaces the earlier selection.
         *
         * @return this builder
         */
        @Contract(value = "-> this", mutates = "this")
        Builder nonValued();

        /**
         * Makes a valued component persistent using the supplied DataFixerUpper
         * codec.
         *
         * @param codec the codec for values of this component
         * @param <T> the component value type
         * @return this builder
         */
        @Contract(value = "_ -> this", mutates = "this")
        <T> Builder persistent(Codec<T> codec);

        /**
         * Makes a non-valued marker component persistent.
         *
         * @return this builder
         */
        @Contract(value = "-> this", mutates = "this")
        Builder persistent();

        /**
         * Sets the default projection modifier for a valued component.
         *
         * @param modifier the default projection modifier
         * @param <T> the component value type
         * @return this builder
         */
        @Contract(value = "_ -> this", mutates = "this")
        <T> Builder defaultProjection(ProjectionModifier.Valued<T> modifier);

        /**
         * Sets the default projection modifier for a non-valued component.
         *
         * @param modifier the default projection modifier
         * @return this builder
         */
        @Contract(value = "_ -> this", mutates = "this")
        Builder defaultProjection(ProjectionModifier.NonValued modifier);
    }
}
