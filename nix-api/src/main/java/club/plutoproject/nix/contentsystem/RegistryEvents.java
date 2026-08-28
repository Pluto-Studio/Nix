package club.plutoproject.nix.contentsystem;

import io.papermc.paper.datacomponent.DataComponentType;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.event.RegistryEventProvider;
import org.bukkit.inventory.ItemType;
import org.jetbrains.annotations.ApiStatus;

/**
 * Lifecycle event providers used to register Nix content types.
 *
 * <p>Register handlers for these providers from a plugin bootstrapper. Both
 * providers add entries to Paper's existing registries during the registry
 * compose phase.</p>
 */
@ApiStatus.Experimental
public final class RegistryEvents {

    /**
     * Provider for custom {@link ItemType} registrations.
     */
    public static final RegistryEventProvider<ItemType, ItemTypeRegistryEntry.Builder> ITEM =
        RegistryEventProvider.<ItemType, ItemTypeRegistryEntry.Builder>create(RegistryKey.ITEM);

    /**
     * Provider for custom {@link DataComponentType} registrations.
     */
    public static final RegistryEventProvider<DataComponentType, DataComponentTypeRegistryEntry.Builder> DATA_COMPONENT_TYPE =
        RegistryEventProvider.<DataComponentType, DataComponentTypeRegistryEntry.Builder>create(RegistryKey.DATA_COMPONENT_TYPE);

    private RegistryEvents() {
    }
}
