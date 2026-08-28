package club.plutoproject.nix.contentsystem;

import java.util.Objects;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

public final class ContentSystemServices {

    private static volatile ContentSystemImpl current;
    private static final ThreadLocal<Player> NETWORK_VIEWER = new ThreadLocal<>();

    private ContentSystemServices() {
    }

    public static void install(final ContentSystemImpl service) {
        current = Objects.requireNonNull(service, "service");
    }

    public static ContentSystemImpl current() {
        ContentSystemImpl service = current;
        if (service == null) {
            service = new ContentSystemImpl();
            current = service;
        }
        return service;
    }

    public static ProjectionScope enterNetworkProjectionViewer(final @Nullable ServerPlayer viewer) {
        final Player previous = NETWORK_VIEWER.get();
        NETWORK_VIEWER.set(viewer == null ? null : viewer.getBukkitEntity());
        return new ProjectionScope(previous);
    }

    public static @Nullable Player networkProjectionViewer() {
        return NETWORK_VIEWER.get();
    }

    public static final class ProjectionScope implements AutoCloseable {
        private final @Nullable Player previous;
        private boolean closed;

        private ProjectionScope(final @Nullable Player previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            if (this.closed) {
                return;
            }
            this.closed = true;
            if (this.previous == null) {
                NETWORK_VIEWER.remove();
            } else {
                NETWORK_VIEWER.set(this.previous);
            }
        }
    }
}
