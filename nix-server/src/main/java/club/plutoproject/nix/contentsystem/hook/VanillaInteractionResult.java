package club.plutoproject.nix.contentsystem.hook;

import club.plutoproject.nix.contentsystem.hook.result.ItemInteractionResult;
import club.plutoproject.nix.contentsystem.hook.result.ItemUseResult;
import net.minecraft.world.InteractionResult;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/** Retains the exact vanilla interaction result used by a default callback. */
final class VanillaInteractionResult extends ItemUseResult {

    private VanillaInteractionResult(final InteractionResult result) {
        super(kind(result), transformed(result));
    }

    static ItemUseResult from(final InteractionResult result) {
        return new VanillaInteractionResult(result);
    }

    private static ItemInteractionResult.Kind kind(final InteractionResult result) {
        if (result instanceof InteractionResult.Fail) {
            return ItemInteractionResult.Kind.FAIL;
        }
        if (result instanceof InteractionResult.TryEmptyHandInteraction) {
            return ItemInteractionResult.Kind.TRY_EMPTY_HAND;
        }
        if (result instanceof InteractionResult.Success success) {
            return success.swingSource() == InteractionResult.SwingSource.NONE
                    ? ItemInteractionResult.Kind.CONSUME
                    : ItemInteractionResult.Kind.SUCCESS;
        }
        return ItemInteractionResult.Kind.PASS;
    }

    private static @Nullable ItemStack transformed(final InteractionResult result) {
        if (!(result instanceof InteractionResult.Success success)) {
            return null;
        }
        final net.minecraft.world.item.ItemStack transformed = success.heldItemTransformedTo();
        return transformed == null ? null : CraftItemStack.asBukkitCopy(transformed);
    }
}
