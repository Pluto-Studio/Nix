package club.plutoproject.nix.contentsystem;

import java.util.Objects;
import net.minecraft.network.HashedPatchMap;
import net.minecraft.network.HashedStack;
import net.minecraft.world.item.ItemStack;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/**
 * Ephemeral per-viewer state for a projected remote slot.
 *
 * <p>The canonical runtime snapshot and the exact projected remote snapshot are
 * intentionally separate. A client claim is checked directly against the
 * projected snapshot; Nix does not retain a second expected hash.</p>
 */
public final class ContentSystemRemoteSlotState {

    private @Nullable ItemStack lastRuntimeStack;
    private @Nullable ItemStack remoteStack;
    private long lastProjectionRevision = Long.MIN_VALUE;
    private @Nullable HashedStack clientClaim;

    public ProjectionResult synchronize(
        final ItemStack runtime,
        final Player viewer,
        final long projectionRevision,
        final ContentSystemProjectionService projectionService
    ) {
        Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(projectionService, "projectionService");
        final boolean canonicalChanged = this.lastRuntimeStack == null || !ItemStack.matches(this.lastRuntimeStack, runtime);
        if (!canonicalChanged && this.lastProjectionRevision == projectionRevision && this.remoteStack != null) {
            return new ProjectionResult(this.remoteStack, false, false);
        }

        final ItemStack projected = projectionService.project(runtime, viewer);
        final boolean representationChanged = this.remoteStack == null || !ItemStack.matches(this.remoteStack, projected);
        this.lastRuntimeStack = runtime.copy();
        this.remoteStack = projected;
        this.lastProjectionRevision = projectionRevision;
        return new ProjectionResult(projected, representationChanged, canonicalChanged);
    }

    public void acceptClientClaim(final @Nullable HashedStack claim) {
        this.clientClaim = claim;
    }

    public void copyFrom(final ContentSystemRemoteSlotState other) {
        Objects.requireNonNull(other, "other");
        this.lastRuntimeStack = other.lastRuntimeStack == null ? null : other.lastRuntimeStack.copy();
        this.remoteStack = other.remoteStack == null ? null : other.remoteStack.copy();
        this.lastProjectionRevision = other.lastProjectionRevision;
        this.clientClaim = other.clientClaim;
    }

    public boolean clientClaimMatches(final HashedPatchMap.HashGenerator hasher) {
        if (this.clientClaim == null || this.remoteStack == null) {
            return false;
        }
        return this.clientClaim.matches(this.remoteStack, hasher);
    }

    public boolean promoteMatchingClaim(final HashedPatchMap.HashGenerator hasher) {
        if (!this.clientClaimMatches(hasher)) {
            return false;
        }
        this.clientClaim = null;
        return true;
    }

    public void invalidate() {
        this.lastRuntimeStack = null;
        this.remoteStack = null;
        this.lastProjectionRevision = Long.MIN_VALUE;
        this.clientClaim = null;
    }

    public @Nullable ItemStack lastRuntimeStack() {
        return this.lastRuntimeStack;
    }

    public @Nullable ItemStack remoteStack() {
        return this.remoteStack;
    }

    public long lastProjectionRevision() {
        return this.lastProjectionRevision;
    }

    public @Nullable HashedStack clientClaim() {
        return this.clientClaim;
    }

    public record ProjectionResult(ItemStack stack, boolean representationChanged, boolean canonicalChanged) {
    }
}
