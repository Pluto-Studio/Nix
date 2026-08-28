package club.plutoproject.nix.contentsystem;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.BlockFace;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Item;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

abstract class HeldItemContextBase implements HeldItemContext {
    private final Player player;
    private final ItemStack itemStack;
    private final EquipmentSlot hand;

    HeldItemContextBase(final Player player, final ItemStack itemStack, final EquipmentSlot hand) {
        this.player = player;
        this.itemStack = itemStack;
        this.hand = hand;
    }

    @Override
    public final Player player() {
        return this.player;
    }

    @Override
    public final ItemStack itemStack() {
        return this.itemStack;
    }

    @Override
    public final EquipmentSlot hand() {
        return this.hand;
    }
}

final class ItemUseContextImpl extends HeldItemContextBase implements ItemUseContext {
    private final ContentSystemDefaultCall<ItemUseResult> defaults;

    ItemUseContextImpl(
        final Player player,
        final ItemStack itemStack,
        final EquipmentSlot hand,
        final ContentSystemDefaultCall<ItemUseResult> defaults
    ) {
        super(player, itemStack, hand);
        this.defaults = defaults;
    }

    @Override
    public ItemUseResult defaultBehavior() {
        return this.defaults.call();
    }
}

final class ItemUseOnBlockContextImpl extends HeldItemContextBase implements ItemUseOnBlockContext {
    private final ContentSystemDefaultCall<ItemInteractionResult> defaults;
    private final Block clickedBlock;
    private final BlockFace clickedFace;
    private final Location interactionPoint;
    private final boolean insideBlock;
    private final boolean hitWorldBorder;
    private final boolean secondaryUseActive;

    ItemUseOnBlockContextImpl(
        final Player player,
        final ItemStack itemStack,
        final EquipmentSlot hand,
        final ContentSystemDefaultCall<ItemInteractionResult> defaults,
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

final class ItemInteractLivingEntityContextImpl extends HeldItemContextBase implements ItemInteractLivingEntityContext {
    private final ContentSystemDefaultCall<ItemInteractionResult> defaults;
    private final LivingEntity target;

    ItemInteractLivingEntityContextImpl(
        final Player player,
        final ItemStack itemStack,
        final EquipmentSlot hand,
        final ContentSystemDefaultCall<ItemInteractionResult> defaults,
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

final class ItemUseTickContextImpl implements ItemUseTickContext {
    private final LivingEntity entity;
    private final ItemStack itemStack;
    private final EquipmentSlot hand;
    private final int remainingTicks;
    private final int usedTicks;
    private final ContentSystemDefaultCall<Void> defaults;

    ItemUseTickContextImpl(
        final LivingEntity entity,
        final ItemStack itemStack,
        final EquipmentSlot hand,
        final int remainingTicks,
        final int usedTicks,
        final ContentSystemDefaultCall<Void> defaults
    ) {
        this.entity = entity;
        this.itemStack = itemStack;
        this.hand = hand;
        this.remainingTicks = remainingTicks;
        this.usedTicks = usedTicks;
        this.defaults = defaults;
    }

    @Override
    public LivingEntity entity() {
        return this.entity;
    }

    @Override
    public ItemStack itemStack() {
        return this.itemStack;
    }

    @Override
    public EquipmentSlot hand() {
        return this.hand;
    }

    @Override
    public int remainingTicks() {
        return this.remainingTicks;
    }

    @Override
    public int usedTicks() {
        return this.usedTicks;
    }

    @Override
    public void runDefaultBehavior() {
        this.defaults.call();
    }
}

final class ItemFinishUseContextImpl implements ItemFinishUseContext {
    private final LivingEntity entity;
    private final ItemStack itemStack;
    private final EquipmentSlot hand;
    private final ContentSystemDefaultCall<ItemStack> defaults;

    ItemFinishUseContextImpl(
        final LivingEntity entity,
        final ItemStack itemStack,
        final EquipmentSlot hand,
        final ContentSystemDefaultCall<ItemStack> defaults
    ) {
        this.entity = entity;
        this.itemStack = itemStack;
        this.hand = hand;
        this.defaults = defaults;
    }

    @Override
    public LivingEntity entity() {
        return this.entity;
    }

    @Override
    public ItemStack itemStack() {
        return this.itemStack;
    }

    @Override
    public EquipmentSlot hand() {
        return this.hand;
    }

    @Override
    public ItemStack defaultBehavior() {
        return this.defaults.call();
    }
}

final class ItemReleaseUseContextImpl implements ItemReleaseUseContext {
    private final LivingEntity entity;
    private final ItemStack itemStack;
    private final EquipmentSlot hand;
    private final int remainingTicks;
    private final int usedTicks;
    private final ContentSystemDefaultCall<ItemReleaseUseResult> defaults;

    ItemReleaseUseContextImpl(
        final LivingEntity entity,
        final ItemStack itemStack,
        final EquipmentSlot hand,
        final int remainingTicks,
        final int usedTicks,
        final ContentSystemDefaultCall<ItemReleaseUseResult> defaults
    ) {
        this.entity = entity;
        this.itemStack = itemStack;
        this.hand = hand;
        this.remainingTicks = remainingTicks;
        this.usedTicks = usedTicks;
        this.defaults = defaults;
    }

    @Override
    public LivingEntity entity() {
        return this.entity;
    }

    @Override
    public ItemStack itemStack() {
        return this.itemStack;
    }

    @Override
    public EquipmentSlot hand() {
        return this.hand;
    }

    @Override
    public int remainingTicks() {
        return this.remainingTicks;
    }

    @Override
    public int usedTicks() {
        return this.usedTicks;
    }

    @Override
    public ItemReleaseUseResult defaultBehavior() {
        return this.defaults.call();
    }
}

final class ItemCanDestroyBlockContextImpl implements ItemCanDestroyBlockContext {
    private final LivingEntity entity;
    private final ItemStack itemStack;
    private final Block block;
    private final BlockData blockData;
    private final ContentSystemDefaultCall<Boolean> defaults;

    ItemCanDestroyBlockContextImpl(
        final LivingEntity entity,
        final ItemStack itemStack,
        final Block block,
        final BlockData blockData,
        final ContentSystemDefaultCall<Boolean> defaults
    ) {
        this.entity = entity;
        this.itemStack = itemStack;
        this.block = block;
        this.blockData = blockData;
        this.defaults = defaults;
    }

    @Override
    public LivingEntity entity() {
        return this.entity;
    }

    @Override
    public ItemStack itemStack() {
        return this.itemStack;
    }

    @Override
    public Block block() {
        return this.block;
    }

    @Override
    public BlockData blockData() {
        return this.blockData;
    }

    @Override
    public Boolean defaultBehavior() {
        return this.defaults.call();
    }
}

final class ItemBlockStateContextImpl<R> implements ItemBlockStateContext<R> {
    private final ItemStack itemStack;
    private final BlockData blockData;
    private final ContentSystemDefaultCall<R> defaults;

    ItemBlockStateContextImpl(final ItemStack itemStack, final BlockData blockData, final ContentSystemDefaultCall<R> defaults) {
        this.itemStack = itemStack;
        this.blockData = blockData;
        this.defaults = defaults;
    }

    @Override
    public ItemStack itemStack() {
        return this.itemStack;
    }

    @Override
    public BlockData blockData() {
        return this.blockData;
    }

    @Override
    public R defaultBehavior() {
        return this.defaults.call();
    }
}

final class ItemMineBlockContextImpl implements ItemMineBlockContext {
    private final Player player;
    private final ItemStack itemStack;
    private final Location blockLocation;
    private final BlockData minedBlockData;
    private final ContentSystemDefaultCall<ItemMineBlockResult> defaults;

    ItemMineBlockContextImpl(
        final Player player,
        final ItemStack itemStack,
        final Location blockLocation,
        final BlockData minedBlockData,
        final ContentSystemDefaultCall<ItemMineBlockResult> defaults
    ) {
        this.player = player;
        this.itemStack = itemStack;
        this.blockLocation = blockLocation;
        this.minedBlockData = minedBlockData;
        this.defaults = defaults;
    }

    @Override
    public Player player() {
        return this.player;
    }

    @Override
    public ItemStack itemStack() {
        return this.itemStack;
    }

    @Override
    public Location blockLocation() {
        return this.blockLocation.clone();
    }

    @Override
    public BlockData minedBlockData() {
        return this.minedBlockData;
    }

    @Override
    public ItemMineBlockResult defaultBehavior() {
        return this.defaults.call();
    }
}

final class ItemAttackDamageBonusContextImpl implements ItemAttackDamageBonusContext {
    private final LivingEntity attacker;
    private final Entity victim;
    private final ItemStack weapon;
    private final float damageBeforeBonus;
    private final DamageSource damageSource;
    private final ContentSystemDefaultCall<Float> defaults;

    ItemAttackDamageBonusContextImpl(
        final LivingEntity attacker,
        final Entity victim,
        final ItemStack weapon,
        final float damageBeforeBonus,
        final DamageSource damageSource,
        final ContentSystemDefaultCall<Float> defaults
    ) {
        this.attacker = attacker;
        this.victim = victim;
        this.weapon = weapon;
        this.damageBeforeBonus = damageBeforeBonus;
        this.damageSource = damageSource;
        this.defaults = defaults;
    }

    @Override
    public LivingEntity attacker() {
        return this.attacker;
    }

    @Override
    public Entity victim() {
        return this.victim;
    }

    @Override
    public ItemStack weapon() {
        return this.weapon;
    }

    @Override
    public float damageBeforeBonus() {
        return this.damageBeforeBonus;
    }

    @Override
    public DamageSource damageSource() {
        return this.damageSource;
    }

    @Override
    public Float defaultBehavior() {
        return this.defaults.call();
    }
}

final class ItemHurtEntityContextImpl implements ItemHurtEntityContext {
    private final LivingEntity attacker;
    private final LivingEntity victim;
    private final ItemStack weapon;
    private final ContentSystemDefaultCall<Void> defaults;

    ItemHurtEntityContextImpl(
        final LivingEntity attacker,
        final LivingEntity victim,
        final ItemStack weapon,
        final ContentSystemDefaultCall<Void> defaults
    ) {
        this.attacker = attacker;
        this.victim = victim;
        this.weapon = weapon;
        this.defaults = defaults;
    }

    @Override
    public LivingEntity attacker() {
        return this.attacker;
    }

    @Override
    public LivingEntity victim() {
        return this.victim;
    }

    @Override
    public ItemStack weapon() {
        return this.weapon;
    }

    @Override
    public void runDefaultBehavior() {
        this.defaults.call();
    }
}

final class ItemInventoryTickContextImpl implements ItemInventoryTickContext {
    private final Entity entity;
    private final ItemStack itemStack;
    private final @Nullable EquipmentSlot equipmentSlot;
    private final ContentSystemDefaultCall<Void> defaults;

    ItemInventoryTickContextImpl(
        final Entity entity,
        final ItemStack itemStack,
        final @Nullable EquipmentSlot equipmentSlot,
        final ContentSystemDefaultCall<Void> defaults
    ) {
        this.entity = entity;
        this.itemStack = itemStack;
        this.equipmentSlot = equipmentSlot;
        this.defaults = defaults;
    }

    @Override
    public Entity entity() {
        return this.entity;
    }

    @Override
    public ItemStack itemStack() {
        return this.itemStack;
    }

    @Override
    public @Nullable EquipmentSlot equipmentSlot() {
        return this.equipmentSlot;
    }

    @Override
    public void runDefaultBehavior() {
        this.defaults.call();
    }
}

final class ItemCraftedContextImpl implements ItemCraftedContext {
    private final ItemStack itemStack;
    private final int craftedAmount;
    private final @Nullable Player player;
    private final ContentSystemDefaultCall<Void> defaults;

    ItemCraftedContextImpl(
        final ItemStack itemStack,
        final int craftedAmount,
        final @Nullable Player player,
        final ContentSystemDefaultCall<Void> defaults
    ) {
        this.itemStack = itemStack;
        this.craftedAmount = craftedAmount;
        this.player = player;
        this.defaults = defaults;
    }

    @Override
    public ItemStack itemStack() {
        return this.itemStack;
    }

    @Override
    public int craftedAmount() {
        return this.craftedAmount;
    }

    @Override
    public @Nullable Player player() {
        return this.player;
    }

    @Override
    public void runDefaultBehavior() {
        this.defaults.call();
    }
}

final class ItemDestroyedAsEntityContextImpl implements ItemDestroyedAsEntityContext {
    private final Item itemEntity;
    private final ItemStack itemStack;
    private final DamageSource damageSource;
    private final ContentSystemDefaultCall<Void> defaults;

    ItemDestroyedAsEntityContextImpl(
        final Item itemEntity,
        final ItemStack itemStack,
        final DamageSource damageSource,
        final ContentSystemDefaultCall<Void> defaults
    ) {
        this.itemEntity = itemEntity;
        this.itemStack = itemStack;
        this.damageSource = damageSource;
        this.defaults = defaults;
    }

    @Override
    public Item itemEntity() {
        return this.itemEntity;
    }

    @Override
    public ItemStack itemStack() {
        return this.itemStack;
    }

    @Override
    public DamageSource damageSource() {
        return this.damageSource;
    }

    @Override
    public void runDefaultBehavior() {
        this.defaults.call();
    }
}

final class ItemContainerFitContextImpl implements ItemContainerFitContext {
    private final ItemStack itemStack;
    private final ContentSystemDefaultCall<Boolean> defaults;

    ItemContainerFitContextImpl(final ItemStack itemStack, final ContentSystemDefaultCall<Boolean> defaults) {
        this.itemStack = itemStack;
        this.defaults = defaults;
    }

    @Override
    public ItemStack itemStack() {
        return this.itemStack;
    }

    @Override
    public Boolean defaultBehavior() {
        return this.defaults.call();
    }
}
