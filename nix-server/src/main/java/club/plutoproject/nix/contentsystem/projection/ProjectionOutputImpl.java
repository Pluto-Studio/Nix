package club.plutoproject.nix.contentsystem.projection;

import org.jetbrains.annotations.ApiStatus;

import club.plutoproject.nix.contentsystem.registry.CustomDataComponentType;
import club.plutoproject.nix.contentsystem.persistence.RecoveryEnvelopeCodec;
import io.papermc.paper.datacomponent.DataComponentAdapter;
import io.papermc.paper.datacomponent.PaperDataComponentType;
import com.mojang.serialization.Codec;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.bukkit.Material;
import org.jspecify.annotations.Nullable;

@ApiStatus.Internal
final class ProjectionOutputImpl implements ProjectionOutput {
    private final ItemStack projected;
    private final Material vanillaMaterial;
    private final Map<DataComponentType<?>, Optional<Object>> restorationValues = new IdentityHashMap<>();

    ProjectionOutputImpl(final ItemStack projected, final Material vanillaMaterial) {
        this.projected = projected;
        this.vanillaMaterial = vanillaMaterial;
    }

    ItemStack projected() {
        return this.projected;
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
            this.restorationValues.put(type, Optional.ofNullable(this.projected.get(type)));
        }
    }

    Map<DataComponentType<?>, Optional<Object>> restorationValues() {
        return Map.copyOf(this.restorationValues);
    }

    void validateRestorationValues() {
        for (final Map.Entry<DataComponentType<?>, Optional<Object>> entry : this.restorationValues.entrySet()) {
            final DataComponentType<?> type = entry.getKey();
            final Identifier key = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type);
            if (key == null) {
                throw new IllegalArgumentException("Projection restore component is not registered");
            }
            if (entry.getValue().isPresent()) {
                final Codec<?> codec = type.codec();
                if (codec == null) {
                    throw new IllegalArgumentException("Cannot persist projection restore for transient component " + key);
                }
                RecoveryEnvelopeCodec.encode(codec, entry.getValue().get());
            }
        }
    }

}
