package org.bukkit.contentsystem;

import club.plutoproject.nix.contentsystem.projection.ItemProjector;
import club.plutoproject.nix.contentsystem.projection.ProjectedSlotState;
import net.minecraft.network.HashedPatchMap.HashGenerator;
import net.minecraft.network.HashedStack;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.bukkit.entity.Player;
import org.bukkit.support.environment.AllFeatures;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;

@AllFeatures
class ProjectedSlotStateTest {

    @Test
    void clientClaimsAreCheckedAgainstTheProjectedRemoteStack() {
        final Player viewer = mock(Player.class);
        final ItemProjector projectionService = new ItemProjector();
        final ProjectedSlotState state = new ProjectedSlotState();
        final ItemStack runtime = new ItemStack(Items.STONE, 2);
        final ProjectedSlotState.ProjectionResult projected = state.synchronize(runtime, viewer, 0L, projectionService);
        final HashGenerator hasher = ignored -> 42;
        final HashedStack claim = HashedStack.create(projected.stack(), hasher);

        state.acceptClientClaim(claim);

        Assertions.assertTrue(state.clientClaimMatches(hasher));
        Assertions.assertTrue(state.promoteMatchingClaim(hasher));
        Assertions.assertNull(state.clientClaim());
    }

    @Test
    void reusesProjectedRepresentationUntilRuntimeOrRevisionChanges() {
        final Player viewer = mock(Player.class);
        final ItemProjector projectionService = new ItemProjector();
        final ProjectedSlotState state = new ProjectedSlotState();
        final ItemStack runtime = new ItemStack(Items.STONE, 2);

        final ProjectedSlotState.ProjectionResult first = state.synchronize(runtime, viewer, 0L, projectionService);
        Assertions.assertTrue(first.canonicalChanged());
        Assertions.assertTrue(first.representationChanged());
        Assertions.assertTrue(ItemStack.matches(runtime, first.stack()));

        final ProjectedSlotState.ProjectionResult unchanged = state.synchronize(runtime.copy(), viewer, 0L, projectionService);
        Assertions.assertFalse(unchanged.canonicalChanged());
        Assertions.assertFalse(unchanged.representationChanged());
        Assertions.assertSame(first.stack(), unchanged.stack());

        final ProjectedSlotState.ProjectionResult refreshed = state.synchronize(runtime, viewer, 1L, projectionService);
        Assertions.assertFalse(refreshed.canonicalChanged());
        Assertions.assertFalse(refreshed.representationChanged());
        Assertions.assertNotSame(first.stack(), refreshed.stack());

        state.invalidate();
        final ProjectedSlotState.ProjectionResult invalidated = state.synchronize(runtime, viewer, 1L, projectionService);
        Assertions.assertTrue(invalidated.canonicalChanged());
    }
}
