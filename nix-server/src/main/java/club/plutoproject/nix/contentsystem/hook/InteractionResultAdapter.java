package club.plutoproject.nix.contentsystem.hook;

import org.jetbrains.annotations.ApiStatus;

import club.plutoproject.nix.contentsystem.hook.result.ItemInteractionResult;
import club.plutoproject.nix.contentsystem.hook.result.ItemUseResult;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.jspecify.annotations.Nullable;

/** Converts public interaction results at an NMS interaction boundary. */
@ApiStatus.Internal
public final class InteractionResultAdapter {

    private InteractionResultAdapter() {
    }

    public static ItemUseResult fromVanillaUse(final InteractionResult result) {
        return VanillaInteractionResult.from(result);
    }

    public static ItemInteractionResult fromVanillaInteraction(final InteractionResult result) {
        return VanillaInteractionResult.from(result);
    }

    public static InteractionResult toNmsInteraction(
            final ItemInteractionResult result,
            final DefaultCall<?> defaults
    ) {
        if (defaults.returned(result) && defaults.nativeResultFor(result) instanceof InteractionResult nativeResult) {
            return nativeResult;
        }

        final ItemInteractionResult.Kind kind = result.kind();
        final @Nullable ItemStack transformed = transformedItem(result);
        return switch (kind) {
            case PASS -> InteractionResult.PASS;
            case FAIL -> InteractionResult.FAIL;
            case TRY_EMPTY_HAND -> InteractionResult.TRY_WITH_EMPTY_HAND;
            case CONSUME -> transformed == null
                    ? InteractionResult.CONSUME
                    : InteractionResult.CONSUME.heldItemTransformedTo(transformed);
            case SUCCESS -> transformed == null
                    ? InteractionResult.SUCCESS_SERVER
                    : InteractionResult.SUCCESS_SERVER.heldItemTransformedTo(transformed);
        };
    }

    private static @Nullable ItemStack transformedItem(final ItemInteractionResult result) {
        if (!(result instanceof ItemUseResult useResult)) {
            return null;
        }
        final org.bukkit.inventory.ItemStack transformed = useResult.transformedHeldItem();
        return transformed == null ? null : CraftItemStack.asNMSCopy(transformed);
    }
}
