package club.plutoproject.nix.contentsystem.registry;

import club.plutoproject.nix.contentsystem.hook.ItemHook;
import club.plutoproject.nix.contentsystem.hook.ItemHookHandler;
import club.plutoproject.nix.contentsystem.projection.ProjectionModifier;

import io.papermc.paper.datacomponent.DataComponentType;
import io.papermc.paper.registry.RegistryBuilder;
import java.util.function.Consumer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemType;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;

/**
 * Registration definition for a Nix custom {@link ItemType}.
 */
@ApiStatus.Experimental
@ApiStatus.NonExtendable
public interface ItemTypeRegistryEntry {

    /**
     * Mutable builder for a custom item type registration.
     */
    @ApiStatus.NonExtendable
    interface Builder extends RegistryBuilder<ItemType> {

        /**
         * Sets the material used by Bukkit compatibility, client projection,
         * and persistence.
         *
         * @param material the vanilla material to use
         * @return this builder
         */
        @Contract(value = "_ -> this", mutates = "this")
        Builder vanillaMaterial(Material material);

        /**
         * Sets a default valued component on the item type.
         *
         * @param type the component type
         * @param value the default component value
         * @param <T> the component value type
         * @return this builder
         */
        @Contract(value = "_, _ -> this", mutates = "this")
        <T> Builder component(DataComponentType.Valued<T> type, T value);

        /**
         * Sets a default non-valued component on the item type.
         *
         * @param type the marker component type
         * @return this builder
         */
        @Contract(value = "_ -> this", mutates = "this")
        Builder component(DataComponentType.NonValued type);

        /**
         * Binds an item-specific projection modifier for a valued component.
         *
         * @param type the component type
         * @param modifier the projection modifier
         * @param <T> the component value type
         * @return this builder
         */
        @Contract(value = "_, _ -> this", mutates = "this")
        <T> Builder project(DataComponentType.Valued<T> type, ProjectionModifier.Valued<T> modifier);

        /**
         * Binds an item-specific projection modifier for a non-valued component.
         *
         * @param type the marker component type
         * @param modifier the projection modifier
         * @return this builder
         */
        @Contract(value = "_, _ -> this", mutates = "this")
        Builder project(DataComponentType.NonValued type, ProjectionModifier.NonValued modifier);

        /**
         * Suppresses projection for a component on this item type.
         *
         * @param type the component type
         * @return this builder
         */
        @Contract(value = "_ -> this", mutates = "this")
        Builder suppressProjection(DataComponentType type);

        /**
         * Sets the callback for a gameplay hook. A later call for the same hook
         * replaces the earlier callback.
         *
         * @param hook the hook descriptor
         * @param handler the callback
         * @param <C> the hook context type
         * @param <R> the hook result type
         * @return this builder
         */
        @Contract(value = "_, _ -> this", mutates = "this")
        <C, R> Builder addHook(ItemHook<C, R> hook, ItemHookHandler<C, R> handler);

        /**
         * Sets a void gameplay-hook callback. A later call for the same hook
         * replaces the earlier callback.
         *
         * @param hook the void hook descriptor
         * @param handler the callback
         * @param <C> the hook context type
         * @return this builder
         */
        @Contract(value = "_, _ -> this", mutates = "this")
        <C> Builder addHook(ItemHook<C, Void> hook, Consumer<C> handler);
    }
}
