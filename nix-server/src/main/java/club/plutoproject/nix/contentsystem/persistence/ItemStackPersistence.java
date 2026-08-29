package club.plutoproject.nix.contentsystem.persistence;

import org.jetbrains.annotations.ApiStatus;
import club.plutoproject.nix.contentsystem.ContentRuntime;
import club.plutoproject.nix.contentsystem.item.CustomItem;
import club.plutoproject.nix.contentsystem.projection.ItemProjector;
import club.plutoproject.nix.contentsystem.registry.CustomDataComponentType;
import club.plutoproject.nix.contentsystem.stack.NestedItemStacks;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.CustomData;

/** Builds persistent stacks and recovers opaque Content System envelopes. */
@ApiStatus.Internal
public final class ItemStackPersistence {

    private static final Comparator<DataComponentType<?>> COMPONENT_ORDER =
            Comparator.comparing(ItemStackPersistence::componentKey);

    private final RecoveryEnvelopeCodec envelopeCodec;

    public ItemStackPersistence() {
        this(new RecoveryEnvelopeCodec());
    }

    public ItemStackPersistence(final RecoveryEnvelopeCodec envelopeCodec) {
        this.envelopeCodec = Objects.requireNonNull(envelopeCodec, "envelopeCodec");
    }

    public ItemStack persistentForm(final ItemStack runtime) {
        if (runtime == null || runtime.isEmpty()) {
            return ItemStack.EMPTY;
        }
        final ItemStack persistent = ItemProjector.createVanillaBase(runtime);
        this.persistNestedComponents(persistent);
        final RecoveryEnvelopeCodec.RecoveryEnvelope envelope =
                this.envelopeCodec.create(runtime, Map.of(), false);
        if (envelope.shouldAttach()) {
            this.envelopeCodec.attach(persistent, envelope);
        }
        return persistent;
    }

    /** Recovers a stack after the ordinary vanilla item codec has decoded it. */
    public ItemStack recover(final ItemStack encoded) {
        if (encoded == null || encoded.isEmpty()) {
            return encoded == null ? ItemStack.EMPTY : encoded;
        }
        final CustomData customData = encoded.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (customData == null || !customData.contains(RecoveryEnvelopeCodec.CUSTOM_DATA_KEY)) {
            this.recoverNestedComponents(encoded);
            return encoded;
        }
        try {
            final Tag rawEnvelope = customData.getUnsafe().get(RecoveryEnvelopeCodec.CUSTOM_DATA_KEY);
            if (!(rawEnvelope instanceof net.minecraft.nbt.CompoundTag envelope)) {
                throw new IllegalArgumentException("nix:item is not a compound");
            }
            this.envelopeCodec.validate(envelope);
            final RecoveryEnvelopeCodec.Parsed parsed = this.envelopeCodec.parse(envelope);
            final Item selectedItem = parsed.itemKey() == null
                    ? encoded.getItem()
                    : resolveCustomItem(parsed.itemKey());
            final ItemStack fallback = encoded.copy();
            this.applyProjectionRestore(fallback, parsed);
            final ItemStack recovered = new ItemStack(selectedItem.builtInRegistryHolder(), encoded.getCount());
            this.reconcileVanillaComponents(recovered, fallback);
            this.envelopeCodec.remove(recovered);
            this.applyCustomPatch(recovered, parsed);
            this.envelopeCodec.remove(recovered);
            this.recoverNestedComponents(recovered);
            return recovered;
        } catch (final RuntimeException failure) {
            ContentRuntime.LOGGER.log(java.util.logging.Level.WARNING, "Content System recovery failed", failure);
            return encoded;
        }
    }

    private void persistNestedComponents(final ItemStack stack) {
        NestedItemStacks.transform(stack, this::persistTemplate);
    }

    private ItemStackTemplate persistTemplate(final ItemStackTemplate template) {
        final ItemStack nested = new ItemStack(template.item(), template.count(), template.components());
        if (!(nested.getItem() instanceof CustomItem) && !ItemProjector.hasCustomComponentState(nested)) {
            return template;
        }
        final ItemStack persistent = this.persistentForm(nested);
        return new ItemStackTemplate(persistent.typeHolder(), persistent.getCount(), persistent.getComponentsPatch());
    }

    private void recoverNestedComponents(final ItemStack stack) {
        NestedItemStacks.transform(stack, this::recoverTemplate);
    }

    private ItemStackTemplate recoverTemplate(final ItemStackTemplate template) {
        final ItemStack nested = new ItemStack(template.item(), template.count(), template.components());
        final ItemStack before = nested.copy();
        final ItemStack recovered = this.recover(nested);
        return sameTemplate(before, recovered)
                ? template
                : new ItemStackTemplate(recovered.typeHolder(), recovered.getCount(), recovered.getComponentsPatch());
    }

    private void reconcileVanillaComponents(final ItemStack target, final ItemStack fallback) {
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

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void applyProjectionRestore(
            final ItemStack stack,
            final RecoveryEnvelopeCodec.Parsed parsed
    ) {
        for (final Map.Entry<DataComponentType<?>, Tag> entry : parsed.restoration().entrySet()) {
            final DataComponentType type = entry.getKey();
            stack.set(type, this.envelopeCodec.decode(type, entry.getValue()));
        }
        for (final DataComponentType<?> type : parsed.restorationRemoved()) {
            stack.remove(type);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void applyCustomPatch(
            final ItemStack stack,
            final RecoveryEnvelopeCodec.Parsed parsed
    ) {
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
    private static void setUnchecked(
            final ItemStack stack,
            final DataComponentType<?> type,
            final Object value
    ) {
        stack.set((DataComponentType) type, value);
    }

    private static Item resolveCustomItem(final String key) {
        final Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(key));
        if (!(item instanceof CustomItem)) {
            throw new IllegalArgumentException("Recovery envelope does not identify a Content System item");
        }
        return item;
    }

    private static boolean sameTemplate(final ItemStack left, final ItemStack right) {
        return left.typeHolder().equals(right.typeHolder())
                && left.getCount() == right.getCount()
                && left.getComponentsPatch().equals(right.getComponentsPatch());
    }

    private static String componentKey(final DataComponentType<?> type) {
        final Identifier key = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type);
        return key == null ? type.toString() : key.toString();
    }
}
