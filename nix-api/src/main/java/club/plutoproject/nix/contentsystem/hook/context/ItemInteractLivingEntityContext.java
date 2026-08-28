package club.plutoproject.nix.contentsystem.hook.context;

import club.plutoproject.nix.contentsystem.hook.DefaultResultBehavior;
import club.plutoproject.nix.contentsystem.hook.result.ItemInteractionResult;

import org.bukkit.entity.LivingEntity;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;

/**
 * Context for interacting with a living entity using an item.
 */
@ApiStatus.Experimental
@ApiStatus.NonExtendable
public interface ItemInteractLivingEntityContext extends HeldItemContext, DefaultResultBehavior<ItemInteractionResult> {

    /**
     * Gets the living entity being interacted with.
     *
     * @return the target entity
     */
    @Contract(pure = true)
    LivingEntity target();
}
