package club.plutoproject.nix.contentsystem.hook.context;

import org.jetbrains.annotations.ApiStatus;
import club.plutoproject.nix.contentsystem.hook.DefaultCall;
import club.plutoproject.nix.contentsystem.hook.result.ItemInteractionResult;
import club.plutoproject.nix.contentsystem.hook.context.ItemUseOnBlockContext;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

@ApiStatus.Internal
public final class ItemUseOnBlockContextImpl extends HeldItemContextBase implements ItemUseOnBlockContext {
    private final DefaultCall<ItemInteractionResult> defaults;
    private final Block clickedBlock;
    private final BlockFace clickedFace;
    private final Location interactionPoint;
    private final boolean insideBlock;
    private final boolean hitWorldBorder;
    private final boolean secondaryUseActive;

    public ItemUseOnBlockContextImpl(
        final Player player,
        final ItemStack itemStack,
        final EquipmentSlot hand,
        final DefaultCall<ItemInteractionResult> defaults,
        final Block clickedBlock,
        final BlockFace clickedFace,
        final Location interactionPoint,
        final boolean insideBlock,
        final boolean hitWorldBorder,
        final boolean secondaryUseActive
    ) {
        super(player, itemStack, hand);
        this.defaults = defaults;
        this.clickedBlock = clickedBlock;
        this.clickedFace = clickedFace;
        this.interactionPoint = interactionPoint;
        this.insideBlock = insideBlock;
        this.hitWorldBorder = hitWorldBorder;
        this.secondaryUseActive = secondaryUseActive;
    }

    @Override
    public ItemInteractionResult defaultBehavior() {
        return this.defaults.call();
    }

    @Override
    public Block clickedBlock() {
        return this.clickedBlock;
    }

    @Override
    public BlockFace clickedFace() {
        return this.clickedFace;
    }

    @Override
    public Location interactionPoint() {
        return this.interactionPoint.clone();
    }

    @Override
    public boolean insideBlock() {
        return this.insideBlock;
    }

    @Override
    public boolean hitWorldBorder() {
        return this.hitWorldBorder;
    }

    @Override
    public boolean secondaryUseActive() {
        return this.secondaryUseActive;
    }
}
