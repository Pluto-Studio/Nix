package club.plutoproject.nix.contentsystem;

import net.minecraft.world.InteractionResult;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * A callback-scoped result which remembers the exact NMS default result. It is
 * intentionally distinct from the API singleton factories: returning
 * {@code ItemInteractionResult.success()} after a default call must still mean
 * a plugin-created server swing, while returning {@code defaultBehavior()}
 * preserves vanilla metadata.
 */
final class ContentSystemDefaultInteractionResult implements ItemUseResult {

    private final Kind kind;
    private final @Nullable ItemStack transformedHeldItem;

    private ContentSystemDefaultInteractionResult(final InteractionResult result) {
        this.kind = kind(result);
        final net.minecraft.world.item.ItemStack transformed = result instanceof InteractionResult.Success success ? success.heldItemTransformedTo() : null;
        this.transformedHeldItem = transformed == null ? null : CraftItemStack.asBukkitCopy(transformed);
    }

    static ItemUseResult from(final InteractionResult result) {
        return new ContentSystemDefaultInteractionResult(result);
    }

    Kind kind() {
        return this.kind;
    }

    @Nullable ItemStack transformedHeldItem() {
        return this.transformedHeldItem;
    }

    private static Kind kind(final InteractionResult result) {
        if (result instanceof InteractionResult.Fail) {
            return Kind.FAIL;
        }
        if (result instanceof InteractionResult.TryEmptyHandInteraction) {
            return Kind.TRY_EMPTY_HAND;
        }
        if (result instanceof InteractionResult.Success success) {
            return success.swingSource() == InteractionResult.SwingSource.NONE ? Kind.CONSUME : Kind.SUCCESS;
        }
        return Kind.PASS;
    }

    enum Kind {
        PASS,
        FAIL,
        SUCCESS,
        CONSUME,
        TRY_EMPTY_HAND
    }
}
