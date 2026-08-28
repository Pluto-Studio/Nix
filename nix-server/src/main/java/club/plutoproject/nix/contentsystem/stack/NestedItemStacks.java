package club.plutoproject.nix.contentsystem.stack;

import org.jetbrains.annotations.ApiStatus;
import java.util.function.UnaryOperator;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.SulfurCubeContent;
import net.minecraft.world.item.component.UseRemainder;

/** Shared traversal of every ItemStack-bearing item component. */
@ApiStatus.Internal
public final class NestedItemStacks {

    private NestedItemStacks() {
    }

    /** Applies a template transformation to all supported nested item stacks. */
    public static void transform(
            final ItemStack stack,
            final UnaryOperator<ItemStackTemplate> mapper
    ) {
        final UseRemainder remainder = stack.get(net.minecraft.core.component.DataComponents.USE_REMAINDER);
        if (remainder != null) {
            final ItemStackTemplate original = remainder.convertInto();
            final ItemStackTemplate transformed = mapper.apply(original);
            if (!transformed.equals(original)) {
                stack.set(net.minecraft.core.component.DataComponents.USE_REMAINDER, new UseRemainder(transformed));
            }
        }

        final BundleContents bundle = stack.get(net.minecraft.core.component.DataComponents.BUNDLE_CONTENTS);
        if (bundle != null) {
            final BundleContents transformed = bundle.mapItems(mapper);
            if (transformed != bundle) {
                stack.set(net.minecraft.core.component.DataComponents.BUNDLE_CONTENTS, transformed);
            }
        }

        final ItemContainerContents container = stack.get(net.minecraft.core.component.DataComponents.CONTAINER);
        if (container != null) {
            final ItemContainerContents transformed = container.mapItems(mapper);
            if (transformed != container) {
                stack.set(net.minecraft.core.component.DataComponents.CONTAINER, transformed);
            }
        }

        final ChargedProjectiles projectiles = stack.get(net.minecraft.core.component.DataComponents.CHARGED_PROJECTILES);
        if (projectiles != null) {
            final ChargedProjectiles transformed = projectiles.mapItems(mapper);
            if (transformed != projectiles) {
                stack.set(net.minecraft.core.component.DataComponents.CHARGED_PROJECTILES, transformed);
            }
        }

        final SulfurCubeContent sulfur = stack.get(net.minecraft.core.component.DataComponents.SULFUR_CUBE_CONTENT);
        if (sulfur != null) {
            final ItemStackTemplate original = sulfur.absorbedBlockItemStack();
            final ItemStackTemplate transformed = mapper.apply(original);
            if (!transformed.equals(original)) {
                stack.set(net.minecraft.core.component.DataComponents.SULFUR_CUBE_CONTENT, new SulfurCubeContent(transformed));
            }
        }
    }
}
