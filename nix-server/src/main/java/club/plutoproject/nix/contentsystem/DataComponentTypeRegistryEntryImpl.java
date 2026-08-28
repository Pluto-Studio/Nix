package club.plutoproject.nix.contentsystem;

import com.mojang.serialization.Codec;
import io.papermc.paper.registry.PaperRegistryBuilder;
import io.papermc.paper.registry.data.util.Conversions;
import java.util.Objects;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Unit;
import org.jspecify.annotations.Nullable;

/** Paper registry implementation for the add-only Content System component entry. */
public final class DataComponentTypeRegistryEntryImpl {

    private DataComponentTypeRegistryEntryImpl() {
    }

    public static final class PaperBuilder implements
        PaperRegistryBuilder<DataComponentType<?>, io.papermc.paper.datacomponent.DataComponentType>,
        DataComponentTypeRegistryEntry.Builder {

        private final DataComponentType<?> internal;
        private boolean modified;
        private boolean kindSelected;
        private boolean valued;
        private @Nullable Codec<?> valuedPersistenceCodec;
        private boolean nonValuedPersistent;
        private ProjectionModifier valuedProjection;
        private ProjectionModifier nonValuedProjection;

        public PaperBuilder(
            final Conversions ignoredConversions,
            final DataComponentType<?> internal
        ) {
            this.internal = internal;
            if (internal instanceof CustomDataComponentType<?> custom) {
                this.kindSelected = true;
                this.valued = custom.valued();
                if (custom.valued()) {
                    this.valuedPersistenceCodec = custom.persistenceCodec();
                    this.valuedProjection = custom.defaultProjection();
                } else {
                    this.nonValuedPersistent = custom.persistenceCodec() != null;
                    this.nonValuedProjection = custom.defaultProjection();
                }
            }
        }

        @Override
        public PaperBuilder valued() {
            this.modified = true;
            this.kindSelected = true;
            this.valued = true;
            return this;
        }

        @Override
        public PaperBuilder nonValued() {
            this.modified = true;
            this.kindSelected = true;
            this.valued = false;
            return this;
        }

        @Override
        public <T> PaperBuilder persistent(final Codec<T> codec) {
            this.modified = true;
            if (!this.kindSelected || !this.valued) {
                throw new IllegalStateException("persistent(codec) requires valued()");
            }
            this.valuedPersistenceCodec = Objects.requireNonNull(codec, "codec");
            return this;
        }

        @Override
        public PaperBuilder persistent() {
            this.modified = true;
            if (!this.kindSelected || this.valued) {
                throw new IllegalStateException("persistent() requires nonValued()");
            }
            this.nonValuedPersistent = true;
            return this;
        }

        @Override
        public <T> PaperBuilder defaultProjection(final ProjectionModifier.Valued<T> modifier) {
            this.modified = true;
            if (!this.kindSelected || !this.valued) {
                throw new IllegalStateException("A valued projection requires valued()");
            }
            this.valuedProjection = Objects.requireNonNull(modifier, "modifier");
            return this;
        }

        @Override
        public PaperBuilder defaultProjection(final ProjectionModifier.NonValued modifier) {
            this.modified = true;
            if (!this.kindSelected || this.valued) {
                throw new IllegalStateException("A non-valued projection requires nonValued()");
            }
            this.nonValuedProjection = Objects.requireNonNull(modifier, "modifier");
            return this;
        }

        @Override
        @SuppressWarnings("unchecked")
        public DataComponentType<?> build() {
            if (this.internal != null && !this.modified) {
                return this.internal;
            }
            if (!this.kindSelected) {
                throw new IllegalStateException("A Content System data component must select valued() or nonValued()");
            }
            final ResourceKey<DataComponentType<?>> key = (ResourceKey<DataComponentType<?>>) ContentSystemRegistrationContext.requireCurrent();
            if (key.identifier().getNamespace().equals("minecraft") || key.identifier().getNamespace().equals("nix")) {
                throw new IllegalArgumentException("Reserved Content System namespace: " + key.identifier().getNamespace());
            }
            if (this.valued) {
                return new CustomDataComponentType<>(
                    key,
                    true,
                    (Codec<Object>) this.valuedPersistenceCodec,
                    this.valuedProjection
                );
            }
            return new CustomDataComponentType<>(
                key,
                false,
                this.nonValuedPersistent ? (Codec<Unit>) Unit.CODEC : null,
                this.nonValuedProjection
            );
        }
    }
}
