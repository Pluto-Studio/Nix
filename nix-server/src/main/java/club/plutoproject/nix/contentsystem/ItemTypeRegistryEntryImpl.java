package club.plutoproject.nix.contentsystem;

import com.google.common.base.Preconditions;
import io.papermc.paper.datacomponent.DataComponentAdapter;
import io.papermc.paper.datacomponent.DataComponentType;
import io.papermc.paper.datacomponent.PaperDataComponentType;
import io.papermc.paper.registry.PaperRegistryBuilder;
import io.papermc.paper.registry.data.util.Conversions;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Unit;
import net.minecraft.world.item.Item;
import org.bukkit.Material;
import org.bukkit.inventory.ItemType;
import org.jspecify.annotations.Nullable;

/** Paper registry implementation for the add-only Content System item entry. */
public final class ItemTypeRegistryEntryImpl {

    private ItemTypeRegistryEntryImpl() {
    }

    public static final class PaperBuilder implements
        PaperRegistryBuilder<Item, ItemType>,
        ItemTypeRegistryEntry.Builder {

        private @Nullable Material vanillaMaterial;
        private final @Nullable Item internal;
        private boolean modified;
        private final List<ContentSystemItem.DefaultComponent> inheritedDefaults = new ArrayList<>();
        private final Map<DataComponentType, Object> defaultComponents = new LinkedHashMap<>();
        private final List<ProjectionSpec> projections = new ArrayList<>();
        private final Map<ItemHook<?, ?>, ItemHookHandler<?, ?>> hooks = new LinkedHashMap<>();

        public PaperBuilder(
            final Conversions ignoredConversions,
            final @Nullable Item internal
        ) {
            this.internal = internal;
            if (internal != null) {
                this.vanillaMaterial = ContentSystemItem.vanillaMaterial(internal);
                if (internal instanceof ContentSystemItem custom) {
                    this.inheritedDefaults.addAll(custom.defaultComponents());
                    for (final ContentSystemItem.ProjectionBinding binding : custom.projectionBindings()) {
                        this.projections.add(new ProjectionSpec(
                            PaperDataComponentType.minecraftToBukkit(binding.component()),
                            binding.modifier(),
                            binding.suppressed()
                        ));
                    }
                    this.hooks.putAll(custom.hooks());
                } else {
                    for (final net.minecraft.core.component.DataComponentType<?> type : internal.components().keySet()) {
                        this.inheritedDefaults.add(new ContentSystemItem.DefaultComponent(type, internal.components().get(type)));
                    }
                }
            }
        }

        @Override
        public PaperBuilder vanillaMaterial(final Material material) {
            this.modified = true;
            this.vanillaMaterial = Objects.requireNonNull(material, "vanillaMaterial");
            Preconditions.checkArgument(material.asItemType() != null && material != Material.AIR, "%s is not a usable item material", material);
            return this;
        }

        @Override
        public <T> PaperBuilder component(
            final DataComponentType.Valued<T> type,
            final T value
        ) {
            this.modified = true;
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(value, "value");
            this.defaultComponents.remove(type);
            this.defaultComponents.put(type, value);
            return this;
        }

        @Override
        public PaperBuilder component(final DataComponentType.NonValued type) {
            this.modified = true;
            Objects.requireNonNull(type, "type");
            this.defaultComponents.remove(type);
            this.defaultComponents.put(type, Unit.INSTANCE);
            return this;
        }

        @Override
        public <T> PaperBuilder project(
            final DataComponentType.Valued<T> type,
            final ProjectionModifier.Valued<T> modifier
        ) {
            return this.replaceProjection(type, Objects.requireNonNull(modifier, "modifier"), false);
        }

        @Override
        public PaperBuilder project(
            final DataComponentType.NonValued type,
            final ProjectionModifier.NonValued modifier
        ) {
            return this.replaceProjection(type, Objects.requireNonNull(modifier, "modifier"), false);
        }

        @Override
        public PaperBuilder suppressProjection(final DataComponentType type) {
            return this.replaceProjection(Objects.requireNonNull(type, "type"), null, true);
        }

        private PaperBuilder replaceProjection(
            final DataComponentType type,
            final ProjectionModifier modifier,
            final boolean suppressed
        ) {
            this.modified = true;
            this.projections.removeIf(spec -> spec.type().equals(type));
            this.projections.add(new ProjectionSpec(type, modifier, suppressed));
            return this;
        }

        @Override
        public <C, R> PaperBuilder addHook(
            final ItemHook<C, R> hook,
            final ItemHookHandler<C, R> handler
        ) {
            this.modified = true;
            this.hooks.put(Objects.requireNonNull(hook, "hook"), Objects.requireNonNull(handler, "handler"));
            return this;
        }

        @Override
        public <C> PaperBuilder addHook(
            final ItemHook<C, Void> hook,
            final Consumer<C> handler
        ) {
            this.modified = true;
            Objects.requireNonNull(handler, "handler");
            return this.addHook(hook, context -> {
                handler.accept(context);
                return null;
            });
        }

        @Override
        @SuppressWarnings({"rawtypes", "unchecked"})
        public Item build() {
            if (this.internal != null && !this.modified) {
                return this.internal;
            }
            final Material material = Objects.requireNonNull(this.vanillaMaterial, "vanillaMaterial must be supplied");
            Preconditions.checkArgument(material.asItemType() != null && material != Material.AIR, "%s is not a usable item material", material);
            final ResourceKey<Item> key = (ResourceKey<Item>) ContentSystemRegistrationContext.requireCurrent();
            Preconditions.checkArgument(key.registryKey().equals(Registries.ITEM), "Not an item registry key: %s", key);
            Preconditions.checkArgument(!key.identifier().getNamespace().equals("minecraft") && !key.identifier().getNamespace().equals("nix"), "Reserved Content System namespace: %s", key.identifier().getNamespace());

            final Map<net.minecraft.core.component.DataComponentType<?>, Object> convertedDefaults = new IdentityHashMap<>();
            for (final ContentSystemItem.DefaultComponent inherited : this.inheritedDefaults) {
                convertedDefaults.put(inherited.type(), inherited.value());
            }
            for (final Map.Entry<DataComponentType, Object> entry : this.defaultComponents.entrySet()) {
                final net.minecraft.core.component.DataComponentType<?> nmsType = PaperDataComponentType.bukkitToMinecraft(entry.getKey());
                Object nmsValue = entry.getValue();
                if (entry.getKey() instanceof PaperDataComponentType.ValuedImpl<?, ?> valued) {
                    nmsValue = ((DataComponentAdapter) valued.getAdapter()).toVanilla(entry.getValue(), valued.getHolder());
                }
                convertedDefaults.put(nmsType, nmsValue);
            }
            final List<ContentSystemItem.DefaultComponent> defaults = new ArrayList<>(convertedDefaults.size());
            convertedDefaults.forEach((type, value) -> defaults.add(new ContentSystemItem.DefaultComponent(type, value)));

            final List<ContentSystemItem.ProjectionBinding> bindings = new ArrayList<>(this.projections.size());
            for (final ProjectionSpec projection : this.projections) {
                bindings.add(new ContentSystemItem.ProjectionBinding(
                    PaperDataComponentType.bukkitToMinecraft(projection.type()),
                    projection.modifier(),
                    projection.suppressed()
                ));
            }
            return new ContentSystemItem(key, material, defaults, bindings, this.hooks);
        }

        private record ProjectionSpec(
            DataComponentType type,
            ProjectionModifier modifier,
            boolean suppressed
        ) {
        }
    }
}
