package club.plutoproject.nix.contentsystem;

import club.plutoproject.nix.contentsystem.persistence.ItemStackPersistence;
import club.plutoproject.nix.contentsystem.persistence.RecoveryEnvelopeCodec;
import club.plutoproject.nix.contentsystem.projection.ItemProjector;
import club.plutoproject.nix.contentsystem.projection.ProjectedSlotState;
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
import org.jetbrains.annotations.ApiStatus;

/** Server-lifetime Content System service implementation. */
@ApiStatus.Internal
public final class ContentSystemImpl implements ContentSystem {

    private final RecoveryEnvelopeCodec recoveryEnvelopeCodec = new RecoveryEnvelopeCodec();
    private final ItemProjector projector = new ItemProjector(this.recoveryEnvelopeCodec);
    private final ItemStackPersistence persistence = new ItemStackPersistence(this.recoveryEnvelopeCodec);
    private final ConcurrentHashMap<UUID, AtomicLong> projectionRevisions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Set<ProjectedSlotState>> remoteStates = new ConcurrentHashMap<>();

    public ContentSystemImpl() {
    }

    @Override
    public void refreshItemProjections(final Player viewer) {
        Objects.requireNonNull(viewer, "viewer");
        this.projectionRevisions.computeIfAbsent(viewer.getUniqueId(), ignored -> new AtomicLong()).incrementAndGet();
        final Set<ProjectedSlotState> states = this.remoteStates.get(viewer.getUniqueId());
        if (states != null) {
            states.forEach(ProjectedSlotState::invalidate);
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

    public void registerRemoteState(final Player viewer, final ProjectedSlotState state) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(state, "state");
        this.remoteStates.computeIfAbsent(viewer.getUniqueId(), ignored -> ConcurrentHashMap.newKeySet()).add(state);
    }

    public void unregisterRemoteState(final Player viewer, final ProjectedSlotState state) {
        Objects.requireNonNull(viewer, "viewer");
        final Set<ProjectedSlotState> states = this.remoteStates.get(viewer.getUniqueId());
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
        return this.projector.project(runtime, viewer);
    }

    public ItemStack persistentForm(final ItemStack runtime) {
        return this.persistence.persistentForm(runtime);
    }

    public ItemStack recover(final ItemStack encoded) {
        return this.persistence.recover(encoded);
    }

    public ItemProjector projector() {
        return this.projector;
    }
}
