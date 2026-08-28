package club.plutoproject.nix.testplugin;

import io.papermc.paper.datacomponent.DataComponentType;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.TypedKey;
import net.kyori.adventure.key.Key;
import org.bukkit.Registry;
import org.bukkit.inventory.ItemType;

final class TestContent {

    static final TypedKey<DataComponentType> CHARGES_KEY = componentKey("charges");
    static final TypedKey<DataComponentType> GLOWING_KEY = componentKey("glowing");
    static final TypedKey<DataComponentType> SESSION_VALUE_KEY = componentKey("session_value");

    static final TypedKey<ItemType> WAND_KEY = itemKey("wand");
    static final TypedKey<ItemType> QUIET_WAND_KEY = itemKey("quiet_wand");
    static final TypedKey<ItemType> TRANSFORMER_KEY = itemKey("transformer");

    private TestContent() {
    }

    @SuppressWarnings("unchecked")
    static DataComponentType.Valued<Integer> charges() {
        final DataComponentType type = Registry.DATA_COMPONENT_TYPE.getOrThrow(CHARGES_KEY);
        if (!(type instanceof DataComponentType.Valued<?> valued)) {
            throw new IllegalStateException("nix_test:charges is not valued");
        }
        return (DataComponentType.Valued<Integer>) valued;
    }

    static DataComponentType.NonValued glowing() {
        final DataComponentType type = Registry.DATA_COMPONENT_TYPE.getOrThrow(GLOWING_KEY);
        if (!(type instanceof DataComponentType.NonValued marker)) {
            throw new IllegalStateException("nix_test:glowing is not non-valued");
        }
        return marker;
    }

    @SuppressWarnings("unchecked")
    static DataComponentType.Valued<String> sessionValue() {
        final DataComponentType type = Registry.DATA_COMPONENT_TYPE.getOrThrow(SESSION_VALUE_KEY);
        if (!(type instanceof DataComponentType.Valued<?> valued)) {
            throw new IllegalStateException("nix_test:session_value is not valued");
        }
        return (DataComponentType.Valued<String>) valued;
    }

    static ItemType wand() {
        return Registry.ITEM.getOrThrow(WAND_KEY);
    }

    static ItemType quietWand() {
        return Registry.ITEM.getOrThrow(QUIET_WAND_KEY);
    }

    static ItemType transformer() {
        return Registry.ITEM.getOrThrow(TRANSFORMER_KEY);
    }

    private static TypedKey<DataComponentType> componentKey(final String value) {
        return RegistryKey.DATA_COMPONENT_TYPE.typedKey(Key.key("nix_test", value));
    }

    private static TypedKey<ItemType> itemKey(final String value) {
        return RegistryKey.ITEM.typedKey(Key.key("nix_test", value));
    }
}
