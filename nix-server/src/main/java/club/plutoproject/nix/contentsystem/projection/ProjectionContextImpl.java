package club.plutoproject.nix.contentsystem.projection;

import club.plutoproject.nix.contentsystem.projection.ProjectionContext;
import club.plutoproject.nix.contentsystem.projection.ProjectionSource;
import org.bukkit.entity.Player;

final class ProjectionContextImpl implements ProjectionContext {
    private final Player viewer;
    private final ProjectionSource source;

    ProjectionContextImpl(final Player viewer, final ProjectionSource source) {
        this.viewer = viewer;
        this.source = source;
    }

    @Override
    public Player viewer() {
        return this.viewer;
    }

    @Override
    public ProjectionSource source() {
        return this.source;
    }
}
