package club.plutoproject.nix.contentsystem;

import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;

/**
 * Carries the key currently being composed through Paper's key-free builder API.
 *
 * <p>Paper's registry builder contract intentionally keeps the registry key on
 * the writable registry rather than on {@code RegistryBuilder}. Nix only uses
 * this small, scoped bridge while a builder is being materialized.</p>
 */
public final class ContentSystemRegistrationContext {

    private static final ThreadLocal<Deque<ResourceKey<?>>> CURRENT_KEYS = ThreadLocal.withInitial(ArrayDeque::new);

    private ContentSystemRegistrationContext() {
    }

    public static Scope enter(final ResourceKey<?> key) {
        CURRENT_KEYS.get().push(key);
        return new Scope();
    }

    public static @Nullable ResourceKey<?> current() {
        final Deque<ResourceKey<?>> keys = CURRENT_KEYS.get();
        return keys.isEmpty() ? null : keys.peek();
    }

    public static ResourceKey<?> requireCurrent() {
        final ResourceKey<?> key = current();
        if (key == null) {
            throw new IllegalStateException("Content System registry builders require a registry key");
        }
        return key;
    }

    public static final class Scope implements AutoCloseable {
        private boolean closed;

        private Scope() {
        }

        @Override
        public void close() {
            if (this.closed) {
                return;
            }
            this.closed = true;
            final Deque<ResourceKey<?>> keys = CURRENT_KEYS.get();
            keys.pop();
            if (keys.isEmpty()) {
                CURRENT_KEYS.remove();
            }
        }
    }
}
