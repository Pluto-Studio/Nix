package club.plutoproject.nix.contentsystem;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import org.bukkit.craftbukkit.inventory.CraftItemStack;

final class ContentSystemResultConversion {

    private ContentSystemResultConversion() {
    }

    static ItemUseResult fromVanillaUse(final InteractionResult result) {
        return ContentSystemDefaultInteractionResult.from(result);
    }

    static ItemInteractionResult fromVanillaInteraction(final InteractionResult result) {
        return ContentSystemDefaultInteractionResult.from(result);
    }

    static InteractionResult toNmsInteraction(
        final ItemInteractionResult result,
        final ContentSystemDefaultCall<?> defaults
    ) {
        if (defaults.returned(result) && defaults.nativeResultFor(result) instanceof InteractionResult nativeResult) {
            return nativeResult;
        }

        final ResultKind kind = resultKind(result);
        final ItemStack transformed = transformedItem(result);
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

    private static ResultKind resultKind(final Object result) {
        if (result instanceof ContentSystemDefaultInteractionResult defaultResult) {
            return switch (defaultResult.kind()) {
                case PASS -> ResultKind.PASS;
                case FAIL -> ResultKind.FAIL;
                case SUCCESS -> ResultKind.SUCCESS;
                case CONSUME -> ResultKind.CONSUME;
                case TRY_EMPTY_HAND -> ResultKind.TRY_EMPTY_HAND;
            };
        }
        if (result instanceof ItemInteractionResultImpl apiResult) {
            return switch (apiResult.kind()) {
                case PASS -> ResultKind.PASS;
                case FAIL -> ResultKind.FAIL;
                case SUCCESS -> ResultKind.SUCCESS;
                case CONSUME -> ResultKind.CONSUME;
                case TRY_EMPTY_HAND -> ResultKind.TRY_EMPTY_HAND;
            };
        }
        throw new IllegalArgumentException("Unknown Content System interaction result " + result.getClass().getName());
    }

    private static ItemStack transformedItem(final Object result) {
        final org.bukkit.inventory.ItemStack transformed;
        if (result instanceof ContentSystemDefaultInteractionResult defaultResult) {
            transformed = defaultResult.transformedHeldItem();
        } else if (result instanceof ItemInteractionResultImpl apiResult) {
            transformed = apiResult.transformedHeldItem();
        } else {
            transformed = null;
        }
        return transformed == null ? null : CraftItemStack.asNMSCopy(transformed);
    }

    private enum ResultKind {
        PASS,
        FAIL,
        SUCCESS,
        CONSUME,
        TRY_EMPTY_HAND
    }
}
