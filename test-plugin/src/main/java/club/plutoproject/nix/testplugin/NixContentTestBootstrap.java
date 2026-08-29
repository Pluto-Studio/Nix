package club.plutoproject.nix.testplugin;

import club.plutoproject.nix.contentsystem.hook.ItemHooks;
import club.plutoproject.nix.contentsystem.hook.result.ItemMineBlockResult;
import club.plutoproject.nix.contentsystem.hook.result.ItemUseResult;
import club.plutoproject.nix.contentsystem.registry.ItemTypeRegistryEntry;
import club.plutoproject.nix.contentsystem.registry.RegistryEvents;
import com.mojang.serialization.Codec;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Consumable;
import io.papermc.paper.datacomponent.item.FoodProperties;
import io.papermc.paper.datacomponent.item.ItemLore;
import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class NixContentTestBootstrap implements PluginBootstrap {

    private static final Component PREFIX = Component.text("[Nix test] ", NamedTextColor.DARK_AQUA);

    @Override
    public void bootstrap(final BootstrapContext context) {
        context.getLifecycleManager().registerEventHandler(
            RegistryEvents.DATA_COMPONENT_TYPE.compose(),
            event -> {
                event.registry().register(TestContent.CHARGES_KEY, builder -> builder
                    .valued()
                    .persistent(Codec.intRange(0, 100))
                    .defaultProjection((projection, charges, output) -> {
                        output.set(DataComponentTypes.CUSTOM_NAME, Component.text("Projected charges: " + charges, NamedTextColor.AQUA));
                        output.set(DataComponentTypes.LORE, ItemLore.lore(List.of(
                            Component.text("Default component projection", NamedTextColor.GRAY),
                            Component.text("Viewer: " + projection.viewer().getName(), NamedTextColor.DARK_GRAY)
                        )));
                    }));
                event.registry().register(TestContent.GLOWING_KEY, builder -> builder
                    .nonValued()
                    .persistent()
                    .defaultProjection((projection, output) -> output.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true)));
                event.registry().register(TestContent.SESSION_VALUE_KEY, builder -> builder.valued());
            }
        );

        context.getLifecycleManager().registerEventHandler(
            RegistryEvents.ITEM.compose(),
            event -> {
                event.registry().register(TestContent.WAND_KEY, builder -> addCommonHooks(builder
                    .vanillaMaterial(Material.BLAZE_ROD)
                    .component(DataComponentTypes.CUSTOM_NAME, Component.text("Runtime Nix Wand", NamedTextColor.GOLD))
                    .component(DataComponentTypes.FOOD, FoodProperties.food().nutrition(1).saturation(0.1F).canAlwaysEat(true).build())
                    .component(DataComponentTypes.CONSUMABLE, Consumable.consumable().consumeSeconds(2.0F).build())
                    .component(TestContent.charges(), 5)
                    .component(TestContent.glowing())
                    .component(TestContent.sessionValue(), "transient-default")
                    .project(TestContent.charges(), (projection, charges, output) -> {
                        output.set(DataComponentTypes.CUSTOM_NAME, Component.text("Nix Wand [" + charges + "]", NamedTextColor.LIGHT_PURPLE));
                        output.set(DataComponentTypes.LORE, ItemLore.lore(List.of(
                            Component.text("Item-specific projection override", NamedTextColor.GRAY),
                            Component.text("Seen by " + projection.viewer().getName(), NamedTextColor.DARK_GRAY)
                        )));
                    })));

                event.registry().register(TestContent.QUIET_WAND_KEY, builder -> addCommonHooks(builder
                    .vanillaMaterial(Material.STICK)
                    .component(TestContent.charges(), 3)
                    .component(TestContent.glowing())
                    .component(TestContent.sessionValue(), "quiet-transient")
                    .suppressProjection(TestContent.glowing())));

                event.registry().register(TestContent.TRANSFORMER_KEY, builder -> builder
                    .vanillaMaterial(Material.FEATHER)
                    .component(DataComponentTypes.CUSTOM_NAME, Component.text("Right-click: transform into diamond", NamedTextColor.YELLOW))
                    .addHook(ItemHooks.ON_USE, use -> {
                        use.player().sendMessage(PREFIX.append(Component.text("ON_USE returned a transformed stack")));
                        return ItemUseResult.success(ItemStack.of(Material.DIAMOND));
                    }));
            }
        );
    }

    private static ItemTypeRegistryEntry.Builder addCommonHooks(
        final ItemTypeRegistryEntry.Builder builder
    ) {
        return builder
            .addHook(ItemHooks.ON_USE, context -> {
                context.player().sendMessage(PREFIX.append(Component.text("ON_USE; running default consumable behavior")));
                return context.defaultBehavior();
            })
            .addHook(ItemHooks.ON_USE_ON_BLOCK, context -> {
                context.player().sendMessage(PREFIX.append(Component.text("ON_USE_ON_BLOCK at " + context.clickedBlock().getType())));
                return context.defaultBehavior();
            })
            .addHook(ItemHooks.ON_INTERACT_LIVING_ENTITY, context -> {
                context.player().sendMessage(PREFIX.append(Component.text("ON_INTERACT_LIVING_ENTITY: " + context.target().getType())));
                return context.defaultBehavior();
            })
            .addHook(ItemHooks.ON_USE_TICK, context -> {
                if (context.remainingTicks() % 10 == 0) {
                    context.entity().sendActionBar(PREFIX.append(Component.text("ON_USE_TICK used=" + context.usedTicks())));
                }
                context.runDefaultBehavior();
            })
            .addHook(ItemHooks.ON_FINISH_USE, context -> {
                context.entity().sendMessage(PREFIX.append(Component.text("ON_FINISH_USE")));
                return context.defaultBehavior();
            })
            .addHook(ItemHooks.ON_RELEASE_USE, context -> {
                context.entity().sendMessage(PREFIX.append(Component.text("ON_RELEASE_USE used=" + context.usedTicks())));
                return context.defaultBehavior();
            })
            .addHook(ItemHooks.CAN_DESTROY_BLOCK, context -> true)
            .addHook(ItemHooks.DESTROY_SPEED, context -> 12.0F)
            .addHook(ItemHooks.IS_CORRECT_TOOL_FOR_DROPS, context -> true)
            .addHook(ItemHooks.ON_MINE_BLOCK, context -> {
                context.player().sendMessage(PREFIX.append(Component.text("ON_MINE_BLOCK: " + context.minedBlockData().getMaterial())));
                context.defaultBehavior();
                return ItemMineBlockResult.AWARD_ITEM_USED_STAT;
            })
            .addHook(ItemHooks.ATTACK_DAMAGE_BONUS, context -> 4.0F)
            .addHook(ItemHooks.ON_HURT_ENTITY, context -> {
                context.attacker().sendMessage(PREFIX.append(Component.text("ON_HURT_ENTITY: " + context.victim().getType())));
                context.runDefaultBehavior();
            })
            .addHook(ItemHooks.AFTER_HURT_ENTITY, context -> {
                context.attacker().sendMessage(PREFIX.append(Component.text("AFTER_HURT_ENTITY: " + context.victim().getType())));
                context.runDefaultBehavior();
            })
            .addHook(ItemHooks.INVENTORY_TICK, context -> {
                if (context.entity() instanceof Player player && context.equipmentSlot() != null && Bukkit.getCurrentTick() % 100 == 0) {
                    player.sendActionBar(PREFIX.append(Component.text("INVENTORY_TICK slot=" + context.equipmentSlot())));
                }
                context.runDefaultBehavior();
            })
            .addHook(ItemHooks.ON_CRAFTED, context -> {
                final String crafter = context.automated() ? "automated crafter" : context.player().getName();
                Bukkit.broadcast(PREFIX.append(Component.text("ON_CRAFTED amount=" + context.craftedAmount() + " by " + crafter)));
                context.runDefaultBehavior();
            })
            .addHook(ItemHooks.ON_DESTROYED_AS_ITEM_ENTITY, context -> {
                Bukkit.broadcast(PREFIX.append(Component.text("ON_DESTROYED_AS_ITEM_ENTITY source=" + context.damageSource().getDamageType().getKey())));
                context.runDefaultBehavior();
            })
            .addHook(ItemHooks.CAN_FIT_INSIDE_CONTAINER_ITEMS, context -> true);
    }
}
