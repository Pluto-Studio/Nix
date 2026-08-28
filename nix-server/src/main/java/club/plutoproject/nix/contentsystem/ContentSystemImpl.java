package club.plutoproject.nix.contentsystem;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.server.level.ChunkMap.TrackedEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

/** Server-lifetime Content System service implementation. */
public final class ContentSystemImpl implements ContentSystem {

    private final ContentSystemProjectionService projections = new ContentSystemProjectionService();
    private final ConcurrentHashMap<UUID, AtomicLong> projectionRevisions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Set<ContentSystemRemoteSlotState>> remoteStates = new ConcurrentHashMap<>();
    public ContentSystemImpl() {
    }

    @Override
    public void refreshItemProjections(final Player viewer) {
        Objects.requireNonNull(viewer, "viewer");
        this.projectionRevisions.computeIfAbsent(viewer.getUniqueId(), ignored -> new AtomicLong()).incrementAndGet();
        final Set<ContentSystemRemoteSlotState> states = this.remoteStates.get(viewer.getUniqueId());
        if (states != null) {
            states.forEach(ContentSystemRemoteSlotState::invalidate);
        }
        // The NMS container send path consumes the invalidated revision;
        // this call also refreshes the ordinary Bukkit inventory view immediately.
        viewer.updateInventory();
        final ServerPlayer serverViewer =
            ((CraftPlayer) viewer).getHandle();
        serverViewer.level().getChunkSource().chunkMap.forEachEntityTrackedBy(serverViewer, entity -> {
            final TrackedEntity tracked =
                serverViewer.level().getChunkSource().chunkMap.entityMap.get(entity.getId());
            if (tracked != null) {
                tracked.serverEntity.sendEquipmentChanges(serverViewer);
            }
        });
    }

    public void registerRemoteState(final Player viewer, final ContentSystemRemoteSlotState state) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(state, "state");
        this.remoteStates.computeIfAbsent(viewer.getUniqueId(), ignored -> ConcurrentHashMap.newKeySet()).add(state);
    }

    public void unregisterRemoteState(final Player viewer, final ContentSystemRemoteSlotState state) {
        Objects.requireNonNull(viewer, "viewer");
        final Set<ContentSystemRemoteSlotState> states = this.remoteStates.get(viewer.getUniqueId());
        if (states != null) {
            states.remove(state);
            if (states.isEmpty()) {
                this.remoteStates.remove(viewer.getUniqueId(), states);
            }
        }
    }

    public long projectionRevision(final Player viewer) {
        Objects.requireNonNull(viewer, "viewer");
        final AtomicLong revision = this.projectionRevisions.get(viewer.getUniqueId());
        return revision == null ? 0L : revision.get();
    }

    public ItemStack project(
        final ItemStack runtime,
        final Player viewer
    ) {
        return this.projections.project(runtime, viewer);
    }

    public ItemStack persistentForm(final ItemStack runtime) {
        return this.projections.persistentForm(runtime);
    }

    public ItemStack recover(final ItemStack encoded) {
        return this.projections.recover(encoded);
    }

    public ContentSystemProjectionService projections() {
        return this.projections;
    }
}
