package club.plutoproject.nix.contentsystem;

import com.mojang.serialization.Codec;
import io.papermc.paper.datacomponent.DataComponentAdapter;
import io.papermc.paper.datacomponent.DataComponentAdapters;
import java.util.Objects;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;

/**
 * Nix's registry value for a plugin data component.
 *
 * <p>The value codec is deliberately kept separate from the network codec. A
 * custom component is never a valid member of a vanilla-facing stack; the
 * deliberately failing network codec makes an accidental unprojected send
 * fail loudly until the NMS projection layer is installed.</p>
 */
public final class ContentSystemDataComponentType<T> implements DataComponentType<T> {

    private static final StreamCodec<RegistryFriendlyByteBuf, Object> NEVER_NETWORK = new StreamCodec<>() {
        @Override
        public Object decode(final RegistryFriendlyByteBuf input) {
            throw new IllegalStateException("A custom Content System component reached the vanilla network codec");
        }

        @Override
        public void encode(final RegistryFriendlyByteBuf output, final Object value) {
            throw new IllegalStateException("A custom Content System component reached the vanilla network codec");
        }
    };

    private final ResourceKey<DataComponentType<?>> key;
    private final boolean valued;
    private final @Nullable Codec<T> codec;
    private final ProjectionModifier defaultProjection;
    private final DataComponentAdapter<?, ?> paperAdapter;

    public ContentSystemDataComponentType(
        final ResourceKey<DataComponentType<?>> key,
        final boolean valued,
        final @Nullable Codec<T> codec,
        final ProjectionModifier defaultProjection
    ) {
        this.key = Objects.requireNonNull(key, "key");
        this.valued = valued;
        this.codec = codec;
        this.defaultProjection = defaultProjection;
        this.paperAdapter = DataComponentAdapters.createContentSystemAdapter(valued);
    }

    public ResourceKey<DataComponentType<?>> key() {
        return this.key;
    }

    public boolean valued() {
        return this.valued;
    }

    public @Nullable Codec<T> persistenceCodec() {
        return this.codec;
    }

    public ProjectionModifier defaultProjection() {
        return this.defaultProjection;
    }

    public DataComponentAdapter<?, ?> paperAdapter() {
        return this.paperAdapter;
    }

    @Override
    public @Nullable Codec<T> codec() {
        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public StreamCodec<? super RegistryFriendlyByteBuf, T> streamCodec() {
        return (StreamCodec<? super RegistryFriendlyByteBuf, T>) (StreamCodec<?, ?>) NEVER_NETWORK;
    }

    @Override
    public boolean ignoreSwapAnimation() {
        return false;
    }

    @Override
    public String toString() {
        final Identifier registeredName = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(this);
        return registeredName == null ? this.key.toString() : registeredName.toString();
    }
}
