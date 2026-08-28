package club.plutoproject.nix.contentsystem;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import io.papermc.paper.datacomponent.DataComponentAdapter;
import io.papermc.paper.datacomponent.PaperDataComponentType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.UnaryOperator;
import java.util.logging.Level;
import java.util.logging.Logger;

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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.SulfurCubeContent;
import net.minecraft.world.item.component.UseRemainder;
import org.bukkit.Material;
import org.bukkit.craftbukkit.CraftRegistry;
import org.bukkit.craftbukkit.inventory.CraftItemType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemType;
import org.jspecify.annotations.Nullable;

/**
 * The single presentation and recovery implementation used by Nix.
 *
 * <p>It deliberately operates on copies. The input stack is authoritative and
 * is never decorated with projection metadata or modifier output.</p>
 */
public final class ContentSystemProjectionService {

    static final String CUSTOM_DATA_KEY = "nix:item";
    static final String COMPONENTS_KEY = "components";
    static final String REMOVED_KEY = "removed";
    static final String PROJECTION_RESTORE_KEY = "projection_restore";
    static final String PROJECTION_RESTORE_REMOVED_KEY = "projection_restore_removed";
    static final int MAX_ENVELOPE_BYTES = 1024 * 1024;
    static final int MAX_COMPONENT_ENTRIES = 256;
    static final int MAX_ENVELOPE_DEPTH = 64;

    private static final Logger LOGGER = Logger.getLogger("Nix Content System");
    private static final Comparator<DataComponentType<?>> COMPONENT_ORDER = Comparator.comparing(ContentSystemProjectionService::componentKey);

    private static String componentKey(final DataComponentType<?> type) {
        final Identifier key = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type);
        return key == null ? type.toString() : key.toString();
    }

    public ItemStack project(final ItemStack runtime, final Player viewer) {
        Objects.requireNonNull(viewer, "viewer");
        if (runtime == null || runtime.isEmpty()) {
            return ItemStack.EMPTY;
        }

        final ItemStack projected = createVanillaBase(runtime);
        final ContentSystemItem customItem = runtime.getItem() instanceof ContentSystemItem custom ? custom : null;
        final Map<DataComponentType<?>, ProjectionOutputImpl.Capture> restorationValues = new IdentityHashMap<>();

        if (customItem != null
                && runtime.get(DataComponents.ITEM_MODEL) != null
                && !runtime.hasNonDefault(DataComponents.ITEM_MODEL)
                && projected.hasNonDefault(DataComponents.ITEM_MODEL)
        ) {
            restorationValues.put(
                DataComponents.ITEM_MODEL,
                ProjectionOutputImpl.Capture.of(runtime, DataComponents.ITEM_MODEL)
            );
            final Identifier vanillaModel = Objects.requireNonNull(
                projected.getPrototype().get(DataComponents.ITEM_MODEL)
            );
            projected.set(DataComponents.ITEM_MODEL, vanillaModel);
        }

        final ProjectionOutputImpl output = needsProjectionOutput(customItem, runtime)
                ? new ProjectionOutputImpl(projected, ContentSystemItem.vanillaMaterial(runtime.getItem()))
                : null;
        if (output != null) {
            this.applyProjectionModifiers(customItem, runtime, viewer, output);
            output.restorationValues().forEach(
                (type, capture) -> restorationValues.putIfAbsent(type, capture)
            );
        }
        projectNestedComponents(projected, viewer);

        final CompoundTag envelope = createEnvelope(runtime, restorationValues, true);
        if (shouldAttachEnvelope(runtime, envelope)) {
            attachEnvelope(projected, envelope);
        }
        return projected;
    }

    public ItemStack persistentForm(final ItemStack runtime) {
        if (runtime == null || runtime.isEmpty()) {
            return ItemStack.EMPTY;
        }
        final ItemStack persistent = createVanillaBase(runtime);
        persistNestedComponents(persistent);
        final CompoundTag envelope = createEnvelope(runtime, Map.of(), false);
        if (shouldAttachEnvelope(runtime, envelope)) {
            attachEnvelope(persistent, envelope);
        }
        return persistent;
    }

    /**
     * Recovers a stack after the ordinary vanilla item codec has decoded it.
     * Invalid envelopes return the unchanged vanilla fallback, retaining the
     * entire opaque envelope for a later server run.
     */
    public ItemStack recover(final ItemStack encoded) {
        if (encoded == null || encoded.isEmpty()) {
            return encoded == null ? ItemStack.EMPTY : encoded;
        }
        final CustomData customData = encoded.get(DataComponents.CUSTOM_DATA);
        if (customData == null || !customData.contains(CUSTOM_DATA_KEY)) {
            recoverNestedComponents(encoded);
            return encoded;
        }
        try {
            final Tag rawEnvelope = customData.getUnsafe().get(CUSTOM_DATA_KEY);
            if (!(rawEnvelope instanceof CompoundTag envelope)) {
                throw new IllegalArgumentException("nix:item is not a compound");
            }
            validateEnvelope(envelope);
            final ParsedEnvelope parsed = parseEnvelope(envelope);
            final Item selectedItem = parsed.itemKey() == null ? encoded.getItem() : resolveCustomItem(parsed.itemKey());
            final ItemStack fallback = encoded.copy();
            applyProjectionRestore(fallback, parsed);
            final ItemStack recovered = new ItemStack(selectedItem.builtInRegistryHolder(), encoded.getCount());
            reconcileVanillaComponents(recovered, fallback);
            removeEnvelope(recovered);
            applyCustomPatch(recovered, parsed);
            removeEnvelope(recovered);
            recoverNestedComponents(recovered);
            return recovered;
        } catch (final RuntimeException failure) {
            LOGGER.log(Level.WARNING, "Content System recovery failed", failure);
            return encoded;
        }
    }

    private void persistNestedComponents(final ItemStack stack) {
        transformNestedComponents(stack, this::persistTemplate);
    }

    private ItemStackTemplate persistTemplate(final ItemStackTemplate template) {
        final ItemStack nested = new ItemStack(template.item(), template.count(), template.components());
        if (!(nested.getItem() instanceof ContentSystemItem) && !hasCustomComponentState(nested)) {
            return template;
        }
        final ItemStack persistent = this.persistentForm(nested);
        return new ItemStackTemplate(persistent.typeHolder(), persistent.getCount(), persistent.getComponentsPatch());
    }

    private void projectNestedComponents(final ItemStack stack, final Player viewer) {
        transformNestedComponents(stack, template -> projectTemplate(template, viewer));
    }

    private ItemStackTemplate projectTemplate(
            final ItemStackTemplate template, final Player viewer
    ) {
        final ItemStack nested = new ItemStack(template.item(), template.count(), template.components());
        if (!(nested.getItem() instanceof ContentSystemItem) && !hasCustomComponentState(nested)) {
            return template;
        }
        final ItemStack projected = this.project(nested, viewer);
        return new ItemStackTemplate(projected.typeHolder(), projected.getCount(), projected.getComponentsPatch());
    }

    private void recoverNestedComponents(final ItemStack stack) {
        transformNestedComponents(stack, this::recoverTemplate);
    }

    private ItemStackTemplate recoverTemplate(final ItemStackTemplate template) {
        final ItemStack nested = new ItemStack(template.item(), template.count(), template.components());
        final ItemStack before = nested.copy();
        final ItemStack recovered = this.recover(nested);
        return sameTemplate(before, recovered)
                ? template
                : new ItemStackTemplate(recovered.typeHolder(), recovered.getCount(), recovered.getComponentsPatch());
    }

    private void transformNestedComponents(
            final ItemStack stack,
            final UnaryOperator<ItemStackTemplate> mapper
    ) {
        final UseRemainder remainder = stack.get(DataComponents.USE_REMAINDER);
        if (remainder != null) {
            final ItemStackTemplate original = remainder.convertInto();
            final ItemStackTemplate transformed = mapper.apply(original);
            if (!transformed.equals(original)) {
                stack.set(DataComponents.USE_REMAINDER, new UseRemainder(transformed));
            }
        }

        final BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
        if (bundle != null) {
            final BundleContents transformed = bundle.mapItems(mapper);
            if (transformed != bundle) {
                stack.set(DataComponents.BUNDLE_CONTENTS, transformed);
            }
        }

        final ItemContainerContents container = stack.get(DataComponents.CONTAINER);
        if (container != null) {
            final ItemContainerContents transformed = container.mapItems(mapper);
            if (transformed != container) {
                stack.set(DataComponents.CONTAINER, transformed);
            }
        }

        final ChargedProjectiles projectiles = stack.get(DataComponents.CHARGED_PROJECTILES);
        if (projectiles != null) {
            final ChargedProjectiles transformed = projectiles.mapItems(mapper);
            if (transformed != projectiles) {
                stack.set(DataComponents.CHARGED_PROJECTILES, transformed);
            }
        }

        final SulfurCubeContent sulfur = stack.get(DataComponents.SULFUR_CUBE_CONTENT);
        if (sulfur != null) {
            final ItemStackTemplate original = sulfur.absorbedBlockItemStack();
            final ItemStackTemplate transformed = mapper.apply(original);
            if (!transformed.equals(original)) {
                stack.set(DataComponents.SULFUR_CUBE_CONTENT, new SulfurCubeContent(transformed));
            }
        }
    }

    private static boolean sameTemplate(final ItemStack left, final ItemStack right) {
        return left.typeHolder().equals(right.typeHolder())
                && left.getCount() == right.getCount()
                && left.getComponentsPatch().equals(right.getComponentsPatch());
    }

    private static void reconcileVanillaComponents(final ItemStack target, final ItemStack fallback) {
        final Set<DataComponentType<?>> types = Collections.newSetFromMap(new IdentityHashMap<>());
        types.addAll(fallback.getComponents().keySet());
        for (final Map.Entry<DataComponentType<?>, Optional<?>> entry : fallback.getComponentsPatch().entrySet()) {
            types.add(entry.getKey());
        }
        types.addAll(target.getComponents().keySet());
        for (final Map.Entry<DataComponentType<?>, Optional<?>> entry : target.getComponentsPatch().entrySet()) {
            types.add(entry.getKey());
        }
        final List<DataComponentType<?>> ordered = new ArrayList<>(types);
        ordered.sort(COMPONENT_ORDER);
        for (final DataComponentType<?> type : ordered) {
            if (type instanceof CustomDataComponentType<?>) {
                continue;
            }
            final Object fallbackValue = fallback.get(type);
            final Object targetValue = target.get(type);
            if (fallbackValue == null) {
                if (targetValue != null) {
                    target.remove(type);
                }
            } else if (!Objects.equals(fallbackValue, targetValue) || fallback.hasNonDefault(type)) {
                setUnchecked(target, type, fallbackValue);
            }
        }
    }

    private static ItemStack createVanillaBase(final ItemStack runtime) {
        if (!(runtime.getItem() instanceof ContentSystemItem) && !hasCustomComponentState(runtime)) {
            return runtime.copy();
        }
        Item target = runtime.getItem();
        if (target instanceof ContentSystemItem custom) {
            target = CraftItemType.bukkitToMinecraft(custom.vanillaMaterial());
        }

        final ItemStack output = new ItemStack(target, runtime.getCount());
        final Set<DataComponentType<?>> types = Collections.newSetFromMap(new IdentityHashMap<>());
        types.addAll(runtime.getComponents().keySet());
        types.addAll(output.getComponents().keySet());
        final List<DataComponentType<?>> ordered = new ArrayList<>(types);
        ordered.sort(COMPONENT_ORDER);
        for (final DataComponentType<?> type : ordered) {
            if (type instanceof CustomDataComponentType<?>) {
                continue;
            }
            final Object runtimeValue = runtime.get(type);
            final Object targetValue = output.get(type);
            if (runtimeValue == null && targetValue != null) {
                // remove components that belongs to vanilla material's default
                output.remove(type);
            } else if (!Objects.equals(runtimeValue, targetValue) || runtime.hasNonDefault(type)) {
                setUnchecked(output, type, runtimeValue);
            }
        }
        return output;
    }

    private static boolean hasCustomComponentState(final ItemStack runtime) {
        for (final DataComponentType<?> type : runtime.getComponents().keySet()) {
            if (type instanceof CustomDataComponentType<?>) {
                return true;
            }
        }
        for (final Map.Entry<DataComponentType<?>, Optional<?>> entry : runtime.getComponentsPatch().entrySet()) {
            if (entry.getKey() instanceof CustomDataComponentType<?>) {
                return true;
            }
        }
        return false;
    }

    private static boolean needsProjectionOutput(final @Nullable ContentSystemItem item, final ItemStack runtime) {
        if (item != null && !item.projectionBindings().isEmpty()) {
            return true;
        }
        for (final DataComponentType<?> type : runtime.getComponents().keySet()) {
            if (type instanceof CustomDataComponentType<?> custom && custom.defaultProjection() != null) {
                return true;
            }
        }
        return false;
    }

    private void applyProjectionModifiers(
            final @Nullable ContentSystemItem item,
            final ItemStack runtime,
            final Player viewer,
            final ProjectionOutputImpl output
    ) {
        final Set<DataComponentType<?>> effectiveCustom = Collections.newSetFromMap(new IdentityHashMap<>());
        for (final DataComponentType<?> type : runtime.getComponents().keySet()) {
            if (type instanceof CustomDataComponentType<?>) {
                effectiveCustom.add(type);
            }
        }

        final Map<DataComponentType<?>, ContentSystemItem.ProjectionBinding> itemBindings = new IdentityHashMap<>();
        if (item != null) {
            for (final ContentSystemItem.ProjectionBinding binding : item.projectionBindings()) {
                if (binding.component() instanceof CustomDataComponentType<?>) {
                    itemBindings.put(binding.component(), binding);
                }
            }
        }

        final List<DataComponentType<?>> defaultOrder = new ArrayList<>(effectiveCustom);
        defaultOrder.sort(COMPONENT_ORDER);
        final ContentSystemProjectionContext context = new ContentSystemProjectionContext(
                viewer,
                new ContentSystemProjectionSource(runtime)
        );
        for (final DataComponentType<?> type : defaultOrder) {
            if (itemBindings.containsKey(type)) {
                continue;
            }
            final CustomDataComponentType<?> custom = (CustomDataComponentType<?>) type;
            final ProjectionModifier modifier = custom.defaultProjection();
            if (modifier != null) {
                applyModifier(type, modifier, context, runtime, output);
            }
        }

        if (item != null) {
            for (final ContentSystemItem.ProjectionBinding binding : item.projectionBindings()) {
                if (!(binding.component() instanceof CustomDataComponentType<?>) || !effectiveCustom.contains(binding.component())) {
                    continue;
                }
                if (!binding.suppressed() && binding.modifier() != null) {
                    applyModifier(binding.component(), binding.modifier(), context, runtime, output);
                }
            }
        }
        validateProjectionOutput(output);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void applyModifier(
            final DataComponentType<?> type,
            final ProjectionModifier modifier,
            final ProjectionContext context,
            final ItemStack runtime,
            final ProjectionOutputImpl output
    ) {
        final Object apiValue = apiValue(runtime, type);
        if (modifier instanceof ProjectionModifier.Valued valued) {
            valued.apply(context, apiValue, output);
        } else if (modifier instanceof ProjectionModifier.NonValued nonValued) {
            nonValued.apply(context, output);
        } else {
            throw new IllegalArgumentException("Unknown projection modifier type");
        }
    }

    private static void validateProjectionOutput(final ProjectionOutputImpl output) {
        final DataResult<ItemStack> validation = ItemStack.validateStrict(output.projected);
        if (validation.isError()) {
            throw new IllegalArgumentException(
                    "Projection modifiers produced an invalid item stack: "
                            + validation.error().map(DataResult.Error::message).orElse("unknown error")
            );
        }
        output.validateRestorationValues();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static @Nullable Object apiValue(final ItemStack runtime, final DataComponentType<?> type) {
        final Object value = runtime.get(type);
        if (value == null) {
            return null;
        }
        final io.papermc.paper.datacomponent.DataComponentType apiType = PaperDataComponentType.minecraftToBukkit(type);
        if (apiType instanceof PaperDataComponentType.ValuedImpl valued) {
            return valued.getAdapter().fromVanilla(value);
        }
        return null;
    }

    private static CompoundTag createEnvelope(
            final ItemStack runtime,
            final Map<DataComponentType<?>, ProjectionOutputImpl.Capture> restorationValues,
            final boolean projection
    ) {
        final CompoundTag envelope = new CompoundTag();
        final boolean customItem = runtime.getItem() instanceof ContentSystemItem;
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
            for (final Map.Entry<DataComponentType<?>, ProjectionOutputImpl.Capture> entry : restorationValues.entrySet()) {
                final Identifier key = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(entry.getKey());
                if (key == null) {
                    continue;
                }
                if (entry.getValue().present()) {
                    final DataComponentType<?> type = entry.getKey();
                    if (type.codec() == null) {
                        throw new IllegalStateException("Cannot persist projection restore for transient component " + key);
                    }
                    restored.put(key.toString(), encode(type.codec(), entry.getValue().value()));
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
                return existing.copy();
            }
        }
        return envelope;
    }

    private static boolean shouldAttachEnvelope(final ItemStack runtime, final CompoundTag generatedEnvelope) {
        if (runtime.getItem() instanceof ContentSystemItem) {
            return true;
        }
        if (generatedEnvelope.contains(COMPONENTS_KEY) || generatedEnvelope.contains(REMOVED_KEY)
                || generatedEnvelope.contains(PROJECTION_RESTORE_KEY) || generatedEnvelope.contains(PROJECTION_RESTORE_REMOVED_KEY)) {
            return true;
        }
        final CustomData customData = runtime.get(DataComponents.CUSTOM_DATA);
        return customData != null && customData.contains(CUSTOM_DATA_KEY);
    }

    private static void attachEnvelope(final ItemStack stack, final CompoundTag envelope) {
        final CustomData current = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        final CompoundTag tag = current.copyTag();
        tag.put(CUSTOM_DATA_KEY, envelope);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    private static void removeEnvelope(final ItemStack stack) {
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

    private static void validateEnvelope(final CompoundTag envelope) {
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

    private static ParsedEnvelope parseEnvelope(final CompoundTag envelope) {
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
        return new ParsedEnvelope(itemKey, components, restoration, restorationRemoved);
    }

    private static Item resolveCustomItem(final String key) {
        final Item item = BuiltInRegistries.ITEM.getValue(parseIdentifier(key));
        if (!(item instanceof ContentSystemItem)) {
            throw new IllegalArgumentException("Recovery envelope does not identify a Content System item");
        }
        return item;
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

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void applyProjectionRestore(final ItemStack stack, final ParsedEnvelope parsed) {
        for (final Map.Entry<DataComponentType<?>, Tag> entry : parsed.restoration().entrySet()) {
            final DataComponentType type = entry.getKey();
            stack.set(type, decode(type.codecOrThrow(), entry.getValue()));
        }
        for (final DataComponentType<?> type : parsed.restorationRemoved()) {
            stack.remove(type);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void applyCustomPatch(final ItemStack stack, final ParsedEnvelope parsed) {
        for (final Map.Entry<DataComponentType<?>, Optional<Object>> entry : parsed.components().entrySet()) {
            final DataComponentType type = entry.getKey();
            if (entry.getValue().isPresent()) {
                stack.set(type, entry.getValue().get());
            } else {
                stack.remove(type);
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void setUnchecked(final ItemStack stack, final DataComponentType<?> type, final Object value) {
        stack.set((DataComponentType) type, value);
    }

    @SuppressWarnings("unchecked")
    private static Tag encode(final Codec codec, final Object value) {
        final DynamicOps<Tag> ops = CraftRegistry.getMinecraftRegistry().createSerializationContext(NbtOps.INSTANCE);
        final DataResult<Tag> result = codec.encodeStart(ops, value);
        return result.result().orElseThrow(() -> new IllegalArgumentException("Custom component codec rejected a value: " + result.error().map(DataResult.Error::message).orElse("unknown error")));
    }

    @SuppressWarnings("unchecked")
    private static Object decode(final Codec codec, final Tag value) {
        final DynamicOps<Tag> ops = CraftRegistry.getMinecraftRegistry().createSerializationContext(NbtOps.INSTANCE);
        final DataResult<?> result = codec.parse(ops, value);
        return result.result().orElseThrow(() -> new IllegalArgumentException("Custom component codec rejected a value: " + result.error().map(DataResult.Error::message).orElse("unknown error")));
    }

    private record ParsedEnvelope(
            @Nullable String itemKey,
            Map<DataComponentType<?>, Optional<Object>> components,
            Map<DataComponentType<?>, Tag> restoration,
            List<DataComponentType<?>> restorationRemoved
    ) {
    }

    private static final class ContentSystemProjectionContext implements ProjectionContext {
        private final Player viewer;
        private final ProjectionSource source;

        private ContentSystemProjectionContext(final Player viewer, final ProjectionSource source) {
            this.viewer = viewer;
            this.source = source;
        }

        @Override
        public Player viewer() {
            return this.viewer;
        }

        @Override
        public ProjectionSource source() {
            return this.source;
        }
    }

    private static final class ContentSystemProjectionSource implements ProjectionSource {
        private final ItemStack source;

        private ContentSystemProjectionSource(final ItemStack source) {
            this.source = source;
        }

        @Override
        public ItemType itemType() {
            return CraftItemType.minecraftToBukkitNew(this.source.getItem());
        }

        @Override
        public int amount() {
            return this.source.getCount();
        }

        @Override
        @SuppressWarnings({"rawtypes", "unchecked"})
        public <T> @Nullable T getData(final io.papermc.paper.datacomponent.DataComponentType.Valued<T> type) {
            final PaperDataComponentType.ValuedImpl<T, ?> paperType =
                    (PaperDataComponentType.ValuedImpl<T, ?>) type;
            final Object value = this.source.get(paperType.getHandle());
            return value == null ? null : (T) ((DataComponentAdapter) paperType.getAdapter()).fromVanilla(value);
        }

        @Override
        public boolean hasData(final io.papermc.paper.datacomponent.DataComponentType type) {
            return this.source.has(PaperDataComponentType.bukkitToMinecraft(type));
        }
    }

    static final class ProjectionOutputImpl implements ProjectionOutput {
        private final ItemStack projected;
        private final Material vanillaMaterial;
        private final Map<DataComponentType<?>, Capture> restorationValues = new IdentityHashMap<>();

        private ProjectionOutputImpl(final ItemStack projected, final Material vanillaMaterial) {
            this.projected = projected;
            this.vanillaMaterial = vanillaMaterial;
        }

        @Override
        public Material vanillaMaterial() {
            return this.vanillaMaterial;
        }

        @Override
        @SuppressWarnings({"rawtypes", "unchecked"})
        public <T> @Nullable T get(final io.papermc.paper.datacomponent.DataComponentType.Valued<T> type) {
            final PaperDataComponentType.ValuedImpl<T, ?> paperType =
                    (PaperDataComponentType.ValuedImpl<T, ?>) type;
            final Object value = this.projected.get(paperType.getHandle());
            return value == null ? null : (T) ((DataComponentAdapter) paperType.getAdapter()).fromVanilla(value);
        }

        @Override
        public boolean has(final io.papermc.paper.datacomponent.DataComponentType type) {
            return this.projected.has(PaperDataComponentType.bukkitToMinecraft(type));
        }

        @Override
        @SuppressWarnings({"rawtypes", "unchecked"})
        public <T> void set(final io.papermc.paper.datacomponent.DataComponentType.Valued<T> type, final T value) {
            final PaperDataComponentType.ValuedImpl paperType =
                    (PaperDataComponentType.ValuedImpl) type;
            final DataComponentType<?> nms = (DataComponentType<?>) paperType.getHandle();
            ensureVanilla(nms);
            this.beforeWrite(nms);
            this.projected.set((DataComponentType) nms, paperType.getAdapter().toVanilla(value, paperType.getHolder()));
        }

        @Override
        @SuppressWarnings({"rawtypes", "unchecked"})
        public void set(final io.papermc.paper.datacomponent.DataComponentType.NonValued type) {
            final PaperDataComponentType.NonValuedImpl paperType =
                    (PaperDataComponentType.NonValuedImpl) type;
            final DataComponentType<?> nms = (DataComponentType<?>) paperType.getHandle();
            ensureVanilla(nms);
            this.beforeWrite(nms);
            this.projected.set((DataComponentType) nms, paperType.getAdapter().toVanilla(null, paperType.getHolder()));
        }

        @Override
        public void unset(final io.papermc.paper.datacomponent.DataComponentType type) {
            final DataComponentType<?> nms = PaperDataComponentType.bukkitToMinecraft(type);
            ensureVanilla(nms);
            this.beforeWrite(nms);
            this.projected.remove(nms);
        }

        private void ensureVanilla(final DataComponentType<?> type) {
            if (type instanceof CustomDataComponentType<?>) {
                throw new IllegalArgumentException("Projection output cannot contain a custom component");
            }
        }

        private void beforeWrite(final DataComponentType<?> type) {
            if (!this.restorationValues.containsKey(type)) {
                this.restorationValues.put(type, Capture.of(this.projected, type));
            }
        }

        private Map<DataComponentType<?>, Capture> restorationValues() {
            return Map.copyOf(this.restorationValues);
        }

        private void validateRestorationValues() {
            for (final Map.Entry<DataComponentType<?>, Capture> entry : this.restorationValues.entrySet()) {
                final DataComponentType<?> type = entry.getKey();
                final Identifier key = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type);
                if (key == null) {
                    throw new IllegalArgumentException("Projection restore component is not registered");
                }
                final Capture capture = entry.getValue();
                if (capture.present()) {
                    final Codec<?> codec = type.codec();
                    if (codec == null) {
                        throw new IllegalArgumentException("Cannot persist projection restore for transient component " + key);
                    }
                    encode(codec, capture.value());
                }
            }
        }

        private record Capture(boolean present, @Nullable Object value) {
            private static Capture of(final ItemStack stack, final DataComponentType<?> type) {
                final Object value = stack.get(type);
                return new Capture(value != null, value);
            }
        }

    }
}
