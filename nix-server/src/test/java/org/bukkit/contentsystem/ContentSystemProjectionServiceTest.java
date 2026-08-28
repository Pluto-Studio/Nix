package org.bukkit.contentsystem;

import club.plutoproject.nix.contentsystem.CustomDataComponentType;
import club.plutoproject.nix.contentsystem.ContentSystemItem;
import club.plutoproject.nix.contentsystem.ContentSystemProjectionService;
import club.plutoproject.nix.contentsystem.ProjectionModifier;
import com.mojang.serialization.Codec;
import io.papermc.paper.datacomponent.DataComponentTypes;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.MapPostProcessing;
import net.minecraft.world.item.component.SulfurCubeContent;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.support.environment.AllFeatures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;

@AllFeatures
@SuppressWarnings({"unchecked", "rawtypes"})
class ContentSystemProjectionServiceTest {

    private static final String NIX_ITEM_KEY = "nix:item";
    private RegistrySnapshot itemRegistry;
    private RegistrySnapshot componentRegistry;
    private List<Object> componentInitializers;

    @BeforeEach
    void snapshotRegistries() throws Exception {
        this.itemRegistry = RegistrySnapshot.capture((MappedRegistry<?>) BuiltInRegistries.ITEM);
        this.componentRegistry = RegistrySnapshot.capture((MappedRegistry<?>) BuiltInRegistries.DATA_COMPONENT_TYPE);
        final Field field = DataComponentInitializersField.field();
        this.componentInitializers = new ArrayList<>((List<?>) field.get(BuiltInRegistries.DATA_COMPONENT_INITIALIZERS));
    }

    @AfterEach
    void restoreRegistries() throws Exception {
        this.itemRegistry.restore();
        this.componentRegistry.restore();
        final Field field = DataComponentInitializersField.field();
        final List<Object> initializers = (List<Object>) field.get(BuiltInRegistries.DATA_COMPONENT_INITIALIZERS);
        initializers.clear();
        initializers.addAll(this.componentInitializers);
    }

    @Test
    void recoveryReconcilesTheAppleFallbackPrototypeIntoACustomItem() throws Exception {
        final ContentSystemItem custom = registerItem("recovery-apple");
        final ContentSystemProjectionService service = new ContentSystemProjectionService();
        final ItemStack apple = new ItemStack(Items.APPLE);
        final FoodProperties appleFood = apple.get(DataComponents.FOOD);

        final ItemStack prototypeFallback = withEnvelope(apple.copy(), custom);
        final ItemStack recoveredFromPrototype = service.recover(prototypeFallback);
        Assertions.assertSame(custom, recoveredFromPrototype.getItem());
        Assertions.assertEquals(appleFood, recoveredFromPrototype.get(DataComponents.FOOD));
        Assertions.assertFalse(hasEnvelope(recoveredFromPrototype));

        final ItemStack explicitFallback = apple.copy();
        explicitFallback.set(DataComponents.FOOD, appleFood);
        final ItemStack recoveredFromExplicit = service.recover(withEnvelope(explicitFallback, custom));
        Assertions.assertSame(custom, recoveredFromExplicit.getItem());
        Assertions.assertEquals(appleFood, recoveredFromExplicit.get(DataComponents.FOOD));
        Assertions.assertFalse(hasEnvelope(recoveredFromExplicit));
    }

    @Test
    void modifierFailureFailsFast() throws Exception {
        final RegisteredComponent<Integer> first = registerComponent("fail-fast-first");
        final RegisteredComponent<Integer> failed = registerComponent("fail-fast-failed");
        final RegisteredComponent<Integer> last = registerComponent("fail-fast-last");
        final ContentSystemItem custom = registerItem(
            "fail-fast",
            List.of(
                binding(first, (context, value, output) -> output.set(DataComponentTypes.MAX_STACK_SIZE, 2)),
                binding(failed, (context, value, output) -> {
                    output.set(DataComponentTypes.MAX_DAMAGE, 1);
                    throw new IllegalStateException();
                }),
                binding(last, (context, value, output) -> output.set(DataComponentTypes.DAMAGE, 1))
            )
        );
        final ItemStack runtime = new ItemStack(custom);
        runtime.set(first.type(), 1);
        runtime.set(failed.type(), 1);
        runtime.set(last.type(), 1);

        Assertions.assertThrows(
            IllegalStateException.class,
            () -> new ContentSystemProjectionService().project(runtime, mock(Player.class))
        );
    }

    @Test
    void transientRestorationFailureFailsFast() throws Exception {
        final RegisteredComponent<Integer> failed = registerComponent("transient-failed");
        final RegisteredComponent<Integer> last = registerComponent("transient-last");
        final ContentSystemItem custom = registerItem(
            "transient",
            List.of(
                binding(failed, (context, value, output) -> output.set(
                    DataComponentTypes.MAP_POST_PROCESSING,
                    io.papermc.paper.item.MapPostProcessing.LOCK
                )),
                binding(last, (context, value, output) -> output.set(DataComponentTypes.DAMAGE, 1))
            )
        );
        final ItemStack runtime = new ItemStack(custom);
        runtime.set(failed.type(), 1);
        runtime.set(last.type(), 1);
        runtime.set(DataComponents.MAP_POST_PROCESSING, MapPostProcessing.SCALE);

        Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> new ContentSystemProjectionService().project(runtime, mock(Player.class))
        );
    }

    @Test
    void sulfurCubeContentUsesTheSameNestedProjectionPersistenceAndRecovery() throws Exception {
        final ContentSystemItem custom = registerItem("sulfur-nested");
        final ContentSystemProjectionService service = new ContentSystemProjectionService();
        final ItemStack outer = new ItemStack(Items.STONE);
        outer.set(DataComponents.SULFUR_CUBE_CONTENT, SulfurCubeContent.ofNonEmpty(new ItemStack(custom)));

        final ItemStack projected = service.project(outer, mock(Player.class));
        final ItemStackTemplate projectedTemplate = projected.get(DataComponents.SULFUR_CUBE_CONTENT).absorbedBlockItemStack();
        Assertions.assertSame(Items.APPLE, projectedTemplate.item().value());
        Assertions.assertTrue(hasEnvelope(projectedTemplate));

        final ItemStack persistent = service.persistentForm(outer);
        final ItemStackTemplate persistentTemplate = persistent.get(DataComponents.SULFUR_CUBE_CONTENT).absorbedBlockItemStack();
        Assertions.assertSame(Items.APPLE, persistentTemplate.item().value());
        Assertions.assertTrue(hasEnvelope(persistentTemplate));
        Assertions.assertEquals(projectedTemplate, persistentTemplate);

        final ItemStack recovered = service.recover(persistent);
        Assertions.assertSame(custom, recovered.get(DataComponents.SULFUR_CUBE_CONTENT).absorbedBlockItemStack().item().value());
    }

    private static ContentSystemItem.ProjectionBinding binding(
        final RegisteredComponent<Integer> component,
        final ProjectionModifier.Valued<Integer> modifier
    ) {
        return new ContentSystemItem.ProjectionBinding(component.type(), modifier, false);
    }

    private static ItemStack withEnvelope(final ItemStack stack, final ContentSystemItem custom) {
        final CompoundTag envelope = new CompoundTag();
        envelope.putString("item", BuiltInRegistries.ITEM.getKey(custom).toString());
        final CompoundTag data = new CompoundTag();
        data.put(NIX_ITEM_KEY, envelope);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        return stack;
    }

    private static boolean hasEnvelope(final ItemStackTemplate template) {
        final CustomData customData = new ItemStack(template.item(), template.count(), template.components()).get(DataComponents.CUSTOM_DATA);
        return customData != null && customData.contains(NIX_ITEM_KEY);
    }

    private static boolean hasEnvelope(final ItemStack stack) {
        final CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        return customData != null && customData.contains(NIX_ITEM_KEY);
    }

    private static ContentSystemItem registerItem(final String name) throws Exception {
        return registerItem(name, List.of());
    }

    private static ContentSystemItem registerItem(
        final String name,
        final List<ContentSystemItem.ProjectionBinding> bindings
    ) throws Exception {
        final ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("content_test", name));
        final MappedRegistry<Item> registry = (MappedRegistry<Item>) BuiltInRegistries.ITEM;
        synchronized (registry) {
            final boolean frozen = setFrozen(registry, false);
            final Object previousIntrusive = setIntrusiveHolders(registry, new IdentityHashMap<>());
            try {
                final ContentSystemItem item = new ContentSystemItem(key, Material.APPLE, List.of(), bindings, Map.of());
                final Holder.Reference<Item> holder = registry.register(key, item, RegistrationInfo.BUILT_IN);
                holder.bindComponents(DataComponentMap.EMPTY);
                return item;
            } finally {
                setIntrusiveHolders(registry, previousIntrusive);
                setFrozen(registry, frozen);
            }
        }
    }

    private static RegisteredComponent<Integer> registerComponent(final String name) throws Exception {
        final ResourceKey<DataComponentType<?>> key = ResourceKey.create(
            Registries.DATA_COMPONENT_TYPE,
            Identifier.fromNamespaceAndPath("content_test", name)
        );
        final MappedRegistry<DataComponentType<?>> registry = (MappedRegistry<DataComponentType<?>>) BuiltInRegistries.DATA_COMPONENT_TYPE;
        synchronized (registry) {
            final boolean frozen = setFrozen(registry, false);
            try {
                final CustomDataComponentType<Integer> type = new CustomDataComponentType<>(key, true, Codec.INT, null);
                final Holder.Reference<DataComponentType<?>> holder = registry.register(key, type, RegistrationInfo.BUILT_IN);
                bindValue(holder, type);
                return new RegisteredComponent<>(type);
            } finally {
                setFrozen(registry, frozen);
            }
        }
    }

    private static boolean setFrozen(final MappedRegistry<?> registry, final boolean value) throws Exception {
        final Field field = MappedRegistry.class.getDeclaredField("frozen");
        field.setAccessible(true);
        final boolean previous = field.getBoolean(registry);
        field.setBoolean(registry, value);
        return previous;
    }

    private static Object setIntrusiveHolders(final MappedRegistry<?> registry, final Object value) throws Exception {
        final Field field = MappedRegistry.class.getDeclaredField("unregisteredIntrusiveHolders");
        field.setAccessible(true);
        final Object previous = field.get(registry);
        field.set(registry, value);
        return previous;
    }

    private static void bindValue(final Holder.Reference<?> holder, final Object value) throws Exception {
        final Method method = Holder.Reference.class.getDeclaredMethod("bindValue", Object.class);
        method.setAccessible(true);
        method.invoke(holder, value);
    }

    private static final class RegistrySnapshot {
        private final MappedRegistry<?> registry;
        private final List<Object> byId;
        private final Map<Object, Object> byLocation;
        private final Map<Object, Object> byKey;
        private final Map<Object, Object> byValue;
        private final Map<Object, Object> registrationInfos;
        private final Map<Object, Integer> toId;
        private final Map<Object, Object> temporaryUnfrozenMap;
        private final Object registryLifecycle;
        private final Object componentLookup;
        private final Object unregisteredIntrusiveHolders;
        private final boolean frozen;

        private RegistrySnapshot(final MappedRegistry<?> registry) throws Exception {
            this.registry = registry;
            this.byId = new ArrayList<>((List<?>) field("byId").get(registry));
            this.byLocation = new HashMap<>((Map<?, ?>) field("byLocation").get(registry));
            this.byKey = new HashMap<>((Map<?, ?>) field("byKey").get(registry));
            this.byValue = new IdentityHashMap<>((Map<?, ?>) field("byValue").get(registry));
            this.registrationInfos = new IdentityHashMap<>((Map<?, ?>) field("registrationInfos").get(registry));
            this.toId = new HashMap<>((Map<Object, Integer>) field("toId").get(registry));
            this.temporaryUnfrozenMap = new HashMap<>((Map<?, ?>) field("temporaryUnfrozenMap").get(registry));
            this.registryLifecycle = field("registryLifecycle").get(registry);
            this.componentLookup = field("componentLookup").get(registry);
            this.unregisteredIntrusiveHolders = field("unregisteredIntrusiveHolders").get(registry);
            this.frozen = field("frozen").getBoolean(registry);
        }

        private static RegistrySnapshot capture(final MappedRegistry<?> registry) throws Exception {
            return new RegistrySnapshot(registry);
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        private void restore() throws Exception {
            ((List) field("byId").get(this.registry)).clear();
            ((List) field("byId").get(this.registry)).addAll(this.byId);
            restoreMap("byLocation", this.byLocation);
            restoreMap("byKey", this.byKey);
            restoreMap("byValue", this.byValue);
            restoreMap("registrationInfos", this.registrationInfos);
            restoreMap("toId", this.toId);
            restoreMap("temporaryUnfrozenMap", this.temporaryUnfrozenMap);
            field("registryLifecycle").set(this.registry, this.registryLifecycle);
            field("componentLookup").set(this.registry, this.componentLookup);
            field("unregisteredIntrusiveHolders").set(this.registry, this.unregisteredIntrusiveHolders);
            field("frozen").setBoolean(this.registry, this.frozen);
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        private void restoreMap(final String name, final Map<?, ?> values) throws Exception {
            final Map target = (Map) field(name).get(this.registry);
            target.clear();
            target.putAll(values);
        }

        private static Field field(final String name) throws Exception {
            final Field field = MappedRegistry.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        }
    }

    private static final class DataComponentInitializersField {
        private DataComponentInitializersField() {
        }

        private static Field field() throws Exception {
            final Field field = DataComponentInitializers.class.getDeclaredField("initializers");
            field.setAccessible(true);
            return field;
        }
    }

    private record RegisteredComponent<T>(CustomDataComponentType<T> type) {
    }
}
