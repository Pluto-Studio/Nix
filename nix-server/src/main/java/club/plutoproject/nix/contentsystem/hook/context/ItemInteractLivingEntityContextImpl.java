package club.plutoproject.nix.contentsystem.hook.context;

import org.jetbrains.annotations.ApiStatus;
import club.plutoproject.nix.contentsystem.hook.DefaultCall;
import club.plutoproject.nix.contentsystem.hook.result.ItemInteractionResult;
import club.plutoproject.nix.contentsystem.hook.context.ItemInteractLivingEntityContext;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

@ApiStatus.Internal
public final class ItemInteractLivingEntityContextImpl extends HeldItemContextBase implements ItemInteractLivingEntityContext {
    private final DefaultCall<ItemInteractionResult> defaults;
    private final LivingEntity target;

    public ItemInteractLivingEntityContextImpl(
        final Player player,
        final ItemStack itemStack,
        final EquipmentSlot hand,
        final DefaultCall<ItemInteractionResult> defaults,
        final LivingEntity target
    ) {
        super(player, itemStack, hand);
        this.defaults = defaults;
        this.target = target;
    }

    @Override
    public ItemInteractionResult defaultBehavior() {
        return this.defaults.call();
    }

    @Override
    public LivingEntity target() {
        return this.target;
    }
}
