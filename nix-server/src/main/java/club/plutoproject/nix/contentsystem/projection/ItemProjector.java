package club.plutoproject.nix.contentsystem.projection;

import org.jetbrains.annotations.ApiStatus;
import club.plutoproject.nix.contentsystem.item.CustomItem;
import club.plutoproject.nix.contentsystem.persistence.RecoveryEnvelopeCodec;
import club.plutoproject.nix.contentsystem.registry.CustomDataComponentType;
import club.plutoproject.nix.contentsystem.stack.NestedItemStacks;
import com.mojang.serialization.DataResult;
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
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import org.bukkit.craftbukkit.inventory.CraftItemType;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;
import club.plutoproject.nix.contentsystem.projection.ProjectionContext;
import club.plutoproject.nix.contentsystem.projection.ProjectionModifier;

/** Projects a runtime stack into a vanilla-compatible client stack. */
@ApiStatus.Internal
public final class ItemProjector {

    private static final Comparator<DataComponentType<?>> COMPONENT_ORDER = Comparator.comparing(ItemProjector::componentKey);
    private final RecoveryEnvelopeCodec recoveryEnvelopeCodec;

    public ItemProjector() {
        this(new RecoveryEnvelopeCodec());
    }

    public ItemProjector(final RecoveryEnvelopeCodec recoveryEnvelopeCodec) {
        this.recoveryEnvelopeCodec = Objects.requireNonNull(recoveryEnvelopeCodec, "recoveryEnvelopeCodec");
    }

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
        final CustomItem customItem = runtime.getItem() instanceof CustomItem custom ? custom : null;
        final Map<DataComponentType<?>, Optional<Object>> restorationValues = new IdentityHashMap<>();

        if (customItem != null
                && runtime.get(DataComponents.ITEM_MODEL) != null
                && !runtime.hasNonDefault(DataComponents.ITEM_MODEL)
                && projected.hasNonDefault(DataComponents.ITEM_MODEL)
        ) {
            restorationValues.put(
                DataComponents.ITEM_MODEL,
                Optional.ofNullable(runtime.get(DataComponents.ITEM_MODEL))
            );
            final Identifier vanillaModel = Objects.requireNonNull(
                projected.getPrototype().get(DataComponents.ITEM_MODEL)
            );
            projected.set(DataComponents.ITEM_MODEL, vanillaModel);
        }

        final ProjectionOutputImpl output = needsProjectionOutput(customItem, runtime)
                ? new ProjectionOutputImpl(projected, CustomItem.vanillaMaterial(runtime.getItem()))
                : null;
        if (output != null) {
            this.applyProjectionModifiers(customItem, runtime, viewer, output);
            output.restorationValues().forEach(
                (type, value) -> restorationValues.putIfAbsent(type, value)
            );
        }
        NestedItemStacks.transform(projected, template -> projectTemplate(template, viewer));

        final RecoveryEnvelopeCodec.RecoveryEnvelope envelope = this.recoveryEnvelopeCodec.create(runtime, restorationValues, true);
        if (envelope.shouldAttach()) {
            this.recoveryEnvelopeCodec.attach(projected, envelope);
        }
        return projected;
    }

    private ItemStackTemplate projectTemplate(
            final ItemStackTemplate template, final Player viewer
    ) {
        final ItemStack nested = new ItemStack(template.item(), template.count(), template.components());
        if (!(nested.getItem() instanceof CustomItem) && !hasCustomComponentState(nested)) {
            return template;
        }
        final ItemStack projected = this.project(nested, viewer);
        return new ItemStackTemplate(projected.typeHolder(), projected.getCount(), projected.getComponentsPatch());
    }

    public static ItemStack createVanillaBase(final ItemStack runtime) {
        if (!(runtime.getItem() instanceof CustomItem) && !hasCustomComponentState(runtime)) {
            return runtime.copy();
        }
        Item target = runtime.getItem();
        if (target instanceof CustomItem custom) {
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

    public static boolean hasCustomComponentState(final ItemStack runtime) {
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

    private static boolean needsProjectionOutput(final @Nullable CustomItem item, final ItemStack runtime) {
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
            final @Nullable CustomItem item,
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

        final Map<DataComponentType<?>, CustomItem.ProjectionBinding> itemBindings = new IdentityHashMap<>();
        if (item != null) {
            for (final CustomItem.ProjectionBinding binding : item.projectionBindings()) {
                if (binding.component() instanceof CustomDataComponentType<?>) {
                    itemBindings.put(binding.component(), binding);
                }
            }
        }

        final List<DataComponentType<?>> defaultOrder = new ArrayList<>(effectiveCustom);
        defaultOrder.sort(COMPONENT_ORDER);
        final ProjectionContextImpl context = new ProjectionContextImpl(
                viewer,
                new ProjectionSourceImpl(runtime)
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
            for (final CustomItem.ProjectionBinding binding : item.projectionBindings()) {
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
        final DataResult<ItemStack> validation = ItemStack.validateStrict(output.projected());
        if (validation.isError()) {
            throw new IllegalArgumentException(
                    "Projection modifiers produced an invalid item stack: "
                            + validation.error().map(DataResult.Error::message).orElse("unknown error")
            );
        }
        output.validateRestorationValues();
    }

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

    private static void setUnchecked(final ItemStack stack, final DataComponentType<?> type, final Object value) {
        stack.set((DataComponentType) type, value);
    }
}
