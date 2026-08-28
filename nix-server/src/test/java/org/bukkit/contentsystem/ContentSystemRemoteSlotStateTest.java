package org.bukkit.contentsystem;

import club.plutoproject.nix.contentsystem.ContentSystemProjectionService;
import club.plutoproject.nix.contentsystem.ContentSystemRemoteSlotState;
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
class ContentSystemRemoteSlotStateTest {

    @Test
    void clientClaimsAreCheckedAgainstTheProjectedRemoteStack() {
        final Player viewer = mock(Player.class);
        final ContentSystemProjectionService projectionService = new ContentSystemProjectionService();
        final ContentSystemRemoteSlotState state = new ContentSystemRemoteSlotState();
        final ItemStack runtime = new ItemStack(Items.STONE, 2);
        final ContentSystemRemoteSlotState.ProjectionResult projected = state.synchronize(runtime, viewer, 0L, projectionService);
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
        final ContentSystemProjectionService projectionService = new ContentSystemProjectionService();
        final ContentSystemRemoteSlotState state = new ContentSystemRemoteSlotState();
        final ItemStack runtime = new ItemStack(Items.STONE, 2);

        final ContentSystemRemoteSlotState.ProjectionResult first = state.synchronize(runtime, viewer, 0L, projectionService);
        Assertions.assertTrue(first.canonicalChanged());
        Assertions.assertTrue(first.representationChanged());
        Assertions.assertTrue(ItemStack.matches(runtime, first.stack()));

        final ContentSystemRemoteSlotState.ProjectionResult unchanged = state.synchronize(runtime.copy(), viewer, 0L, projectionService);
        Assertions.assertFalse(unchanged.canonicalChanged());
        Assertions.assertFalse(unchanged.representationChanged());
        Assertions.assertSame(first.stack(), unchanged.stack());

        final ContentSystemRemoteSlotState.ProjectionResult refreshed = state.synchronize(runtime, viewer, 1L, projectionService);
        Assertions.assertFalse(refreshed.canonicalChanged());
        Assertions.assertFalse(refreshed.representationChanged());
        Assertions.assertNotSame(first.stack(), refreshed.stack());

        state.invalidate();
        final ContentSystemRemoteSlotState.ProjectionResult invalidated = state.synchronize(runtime, viewer, 1L, projectionService);
        Assertions.assertTrue(invalidated.canonicalChanged());
    }
}
