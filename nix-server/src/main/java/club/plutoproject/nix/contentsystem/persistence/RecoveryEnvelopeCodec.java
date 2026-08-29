package club.plutoproject.nix.contentsystem.persistence;

import org.jetbrains.annotations.ApiStatus;

import club.plutoproject.nix.contentsystem.item.CustomItem;
import club.plutoproject.nix.contentsystem.registry.CustomDataComponentType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.DataResult;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CollectionTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Unit;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.bukkit.craftbukkit.CraftRegistry;
import org.jspecify.annotations.Nullable;

/** Encodes, validates, and attaches opaque Content System recovery metadata. */
@ApiStatus.Internal
public final class RecoveryEnvelopeCodec {

    public static final String CUSTOM_DATA_KEY = "nix:item";
    private static final String COMPONENTS_KEY = "components";
    private static final String REMOVED_KEY = "removed";
    private static final String PROJECTION_RESTORE_KEY = "projection_restore";
    private static final String PROJECTION_RESTORE_REMOVED_KEY = "projection_restore_removed";
    private static final int MAX_ENVELOPE_BYTES = 1024 * 1024;
    private static final int MAX_COMPONENT_ENTRIES = 256;
    private static final int MAX_ENVELOPE_DEPTH = 64;

    public RecoveryEnvelope create(
            final ItemStack runtime,
            final Map<DataComponentType<?>, Optional<Object>> restorationValues,
            final boolean projection
    ) {
        CompoundTag envelope = new CompoundTag();
        final boolean customItem = runtime.getItem() instanceof CustomItem;
        if (customItem) {
            final Identifier key = BuiltInRegistries.ITEM.getKey(runtime.getItem());
            if (key == null) {
                throw new IllegalStateException("Custom item is not registered");
            }
            envelope.putString("item", key.toString());
        }

        final TreeMap<String, Tag> encodedComponents = new TreeMap<>();
        final List<String> removedComponents = new ArrayList<>();
        for (final Map.Entry<DataComponentType<?>, Optional<?>> entry : runtime.getComponentsPatch().entrySet()) {
            if (!(entry.getKey() instanceof CustomDataComponentType<?> custom) || custom.persistenceCodec() == null) {
                continue;
            }
            final Identifier key = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(entry.getKey());
            if (key == null) {
                throw new IllegalStateException("Persistent custom component is not registered");
            }
            if (entry.getValue().isPresent()) {
                final Object value = entry.getValue().get();
                final Tag encoded = custom.valued()
                        ? encode(custom.persistenceCodec(), value)
                        : StringTag.valueOf("present");
                encodedComponents.put(key.toString(), encoded);
            } else {
                removedComponents.add(key.toString());
            }
        }
        if (!encodedComponents.isEmpty()) {
            final CompoundTag components = new CompoundTag();
            encodedComponents.forEach(components::put);
            envelope.put(COMPONENTS_KEY, components);
        }
        if (!removedComponents.isEmpty()) {
            removedComponents.sort(String::compareTo);
            final ListTag removed = new ListTag();
            for (final String key : removedComponents) {
                removed.add(StringTag.valueOf(key));
            }
            envelope.put(REMOVED_KEY, removed);
        }

        if (projection && !restorationValues.isEmpty()) {
            final TreeMap<String, Tag> restored = new TreeMap<>();
            final List<String> restoredRemoved = new ArrayList<>();
            for (final Map.Entry<DataComponentType<?>, Optional<Object>> entry : restorationValues.entrySet()) {
                final Identifier key = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(entry.getKey());
                if (key == null) {
                    continue;
                }
                if (entry.getValue().isPresent()) {
                    final DataComponentType<?> type = entry.getKey();
                    if (type.codec() == null) {
                        throw new IllegalStateException("Cannot persist projection restore for transient component " + key);
                    }
                    restored.put(key.toString(), encode(type.codec(), entry.getValue().get()));
                } else {
                    restoredRemoved.add(key.toString());
                }
            }
            if (!restored.isEmpty()) {
                final CompoundTag restore = new CompoundTag();
                restored.forEach(restore::put);
                envelope.put(PROJECTION_RESTORE_KEY, restore);
            }
            if (!restoredRemoved.isEmpty()) {
                restoredRemoved.sort(String::compareTo);
                final ListTag removed = new ListTag();
                for (final String key : restoredRemoved) {
                    removed.add(StringTag.valueOf(key));
                }
                envelope.put(PROJECTION_RESTORE_REMOVED_KEY, removed);
            }
        }
        if (!customItem && envelope.size() == 0) {
            final CustomData existingData = runtime.get(DataComponents.CUSTOM_DATA);
            if (existingData != null && existingData.getUnsafe().get(CUSTOM_DATA_KEY) instanceof CompoundTag existing) {
                envelope = existing.copy();
            }
        }
        return new RecoveryEnvelope(envelope, shouldAttachEnvelope(runtime, envelope));
    }

    private static boolean shouldAttachEnvelope(final ItemStack runtime, final CompoundTag generatedEnvelope) {
        if (runtime.getItem() instanceof CustomItem) {
            return true;
        }
        if (generatedEnvelope.contains(COMPONENTS_KEY) || generatedEnvelope.contains(REMOVED_KEY)
                || generatedEnvelope.contains(PROJECTION_RESTORE_KEY) || generatedEnvelope.contains(PROJECTION_RESTORE_REMOVED_KEY)) {
            return true;
        }
        final CustomData customData = runtime.get(DataComponents.CUSTOM_DATA);
        return customData != null && customData.contains(CUSTOM_DATA_KEY);
    }

    private void attachEnvelope(final ItemStack stack, final CompoundTag envelope) {
        final CustomData current = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        final CompoundTag tag = current.copyTag();
        tag.put(CUSTOM_DATA_KEY, envelope);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    private void removeEnvelope(final ItemStack stack) {
        final CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null || !customData.contains(CUSTOM_DATA_KEY)) {
            return;
        }
        final CompoundTag tag = customData.copyTag();
        tag.remove(CUSTOM_DATA_KEY);
        if (tag.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
    }

    public void validate(final CompoundTag envelope) {
        if (envelope.sizeInBytes() > MAX_ENVELOPE_BYTES || depth(envelope, 0) > MAX_ENVELOPE_DEPTH) {
            throw new IllegalArgumentException("Content System recovery envelope exceeds its limits");
        }
        if (envelope.contains("item") && (!envelope.getString("item").isPresent() || envelope.getStringOr("item", "").length() > 256)) {
            throw new IllegalArgumentException("Invalid custom item key");
        }
        int entries = 0;
        if (envelope.contains(COMPONENTS_KEY)) {
            final CompoundTag components = envelope.getCompound(COMPONENTS_KEY).orElseThrow(() -> new IllegalArgumentException("components is not a compound"));
            entries += components.size();
        }
        if (envelope.contains(REMOVED_KEY)) {
            entries += envelope.getList(REMOVED_KEY).orElseThrow(() -> new IllegalArgumentException("removed is not a list")).size();
        }
        if (entries > MAX_COMPONENT_ENTRIES) {
            throw new IllegalArgumentException("Too many custom component entries");
        }
        validateKeyList(envelope, REMOVED_KEY);
        validateKeyList(envelope, PROJECTION_RESTORE_REMOVED_KEY);
    }

    private static void validateKeyList(final CompoundTag envelope, final String key) {
        if (!envelope.contains(key)) {
            return;
        }
        final ListTag values = envelope.getList(key).orElseThrow(() -> new IllegalArgumentException(key + " is not a list"));
        for (int index = 0; index < values.size(); index++) {
            final String value = values.getString(index).orElseThrow(() -> new IllegalArgumentException("Invalid key in " + key));
            parseIdentifier(value);
        }
    }

    private static int depth(final Tag tag, final int current) {
        if (current > MAX_ENVELOPE_DEPTH) {
            return current;
        }
        if (tag instanceof CompoundTag compound) {
            int deepest = current;
            for (final Tag child : compound.values()) {
                deepest = Math.max(deepest, depth(child, current + 1));
            }
            return deepest;
        }
        if (tag instanceof CollectionTag collection) {
            int deepest = current;
            for (final Tag child : collection) {
                deepest = Math.max(deepest, depth(child, current + 1));
            }
            return deepest;
        }
        return current;
    }

    public Parsed parse(final CompoundTag envelope) {
        final @Nullable String itemKey = envelope.getStringOr("item", null);
        if (envelope.contains("item") && itemKey == null) {
            throw new IllegalArgumentException("Invalid item key");
        }
        final Map<DataComponentType<?>, Optional<Object>> components = new IdentityHashMap<>();
        if (envelope.contains(COMPONENTS_KEY)) {
            final CompoundTag values = envelope.getCompound(COMPONENTS_KEY).orElseThrow();
            for (final Map.Entry<String, Tag> entry : values.entrySet()) {
                final Identifier key = parseIdentifier(entry.getKey());
                final DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(key);
                if (!(type instanceof CustomDataComponentType<?> custom) || custom.persistenceCodec() == null) {
                    throw new IllegalArgumentException("Unknown persistent custom component " + key);
                }
                final Object value = custom.valued() ? decode(custom.persistenceCodec(), entry.getValue()) : Unit.INSTANCE;
                if (components.containsKey(type)) {
                    throw new IllegalArgumentException("Duplicate component patch entry");
                }
                components.put(type, Optional.of(value));
            }
        }
        final List<DataComponentType<?>> removed = new ArrayList<>();
        if (envelope.contains(REMOVED_KEY)) {
            final ListTag values = envelope.getList(REMOVED_KEY).orElseThrow();
            for (int index = 0; index < values.size(); index++) {
                final String componentKey = values.getString(index).orElseThrow();
                final DataComponentType<?> type = resolvePersistentCustomType(componentKey);
                if (components.containsKey(type)) {
                    throw new IllegalArgumentException("Duplicate component patch entry");
                }
                components.put(type, Optional.empty());
                removed.add(type);
            }
        }

        final Map<DataComponentType<?>, Tag> restoration = new IdentityHashMap<>();
        if (envelope.contains(PROJECTION_RESTORE_KEY)) {
            final CompoundTag values = envelope.getCompound(PROJECTION_RESTORE_KEY).orElseThrow();
            for (final Map.Entry<String, Tag> entry : values.entrySet()) {
                final DataComponentType<?> type = resolveVanillaType(entry.getKey());
                restoration.put(type, entry.getValue());
            }
        }
        final List<DataComponentType<?>> restorationRemoved = new ArrayList<>();
        if (envelope.contains(PROJECTION_RESTORE_REMOVED_KEY)) {
            final ListTag values = envelope.getList(PROJECTION_RESTORE_REMOVED_KEY).orElseThrow();
            for (int index = 0; index < values.size(); index++) {
                final DataComponentType<?> type = resolveVanillaType(values.getString(index).orElseThrow());
                restorationRemoved.add(type);
            }
        }
        return new Parsed(itemKey, components, restoration, restorationRemoved);
    }

    private static DataComponentType<?> resolvePersistentCustomType(final String key) {
        final DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(parseIdentifier(key));
        if (!(type instanceof CustomDataComponentType<?> custom) || custom.persistenceCodec() == null) {
            throw new IllegalArgumentException("Unknown persistent custom component " + key);
        }
        return type;
    }

    private static DataComponentType<?> resolveVanillaType(final String key) {
        final DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(parseIdentifier(key));
        if (type == null || type instanceof CustomDataComponentType<?>) {
            throw new IllegalArgumentException("Invalid projection restore component " + key);
        }
        return type;
    }

    private static Identifier parseIdentifier(final String key) {
        if (key == null || key.isEmpty() || key.length() > 256) {
            throw new IllegalArgumentException("Invalid namespaced key");
        }
        return Identifier.parse(key);
    }

    public static Tag encode(final Codec codec, final Object value) {
        final DynamicOps<Tag> ops = CraftRegistry.getMinecraftRegistry().createSerializationContext(NbtOps.INSTANCE);
        final DataResult<Tag> result = codec.encodeStart(ops, value);
        return result.result().orElseThrow(() -> new IllegalArgumentException("Custom component codec rejected a value: " + result.error().map(DataResult.Error::message).orElse("unknown error")));
    }

    public static Object decode(final Codec codec, final Tag value) {
        final DynamicOps<Tag> ops = CraftRegistry.getMinecraftRegistry().createSerializationContext(NbtOps.INSTANCE);
        final DataResult<?> result = codec.parse(ops, value);
        return result.result().orElseThrow(() -> new IllegalArgumentException("Custom component codec rejected a value: " + result.error().map(DataResult.Error::message).orElse("unknown error")));
    }

    public Object decode(final DataComponentType<?> type, final Tag value) {
        return decode(type.codecOrThrow(), value);
    }

    public void attach(final ItemStack stack, final RecoveryEnvelope envelope) {
        this.attachEnvelope(stack, envelope.tag());
    }

    public void remove(final ItemStack stack) {
        this.removeEnvelope(stack);
    }

    public record Parsed(
            @Nullable String itemKey,
            Map<DataComponentType<?>, Optional<Object>> components,
            Map<DataComponentType<?>, Tag> restoration,
            List<DataComponentType<?>> restorationRemoved
    ) {
    }

    public record RecoveryEnvelope(CompoundTag tag, boolean shouldAttach) {
    }

}
