package club.plutoproject.nix.testplugin;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.datacomponent.DataComponentTypes;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ItemType;

final class NixTestCommand implements BasicCommand {

    private static final Component PREFIX = Component.text("[Nix test] ", NamedTextColor.DARK_AQUA);
    private static final List<String> ROOTS = List.of(
        "help", "give", "inspect", "setcharges", "marker", "refresh", "clone", "convert", "roundtrip", "drop", "checklist"
    );

    @Override
    public void execute(final CommandSourceStack source, final String[] args) {
        final CommandSender sender = source.getSender();
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            this.help(sender);
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(PREFIX.append(Component.text("This subcommand must be run by a player.", NamedTextColor.RED)));
            return;
        }

        try {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "give" -> this.give(player, args.length > 1 ? args[1] : "all");
                case "inspect" -> this.inspect(player, held(player));
                case "setcharges" -> this.setCharges(player, args);
                case "marker" -> this.toggleMarker(player);
                case "refresh" -> this.refresh(player);
                case "clone" -> this.cloneTest(player);
                case "convert" -> this.convert(player, args);
                case "roundtrip" -> this.roundTrip(player);
                case "drop" -> this.drop(player);
                case "checklist" -> this.checklist(player);
                default -> this.help(sender);
            }
        } catch (final RuntimeException exception) {
            sender.sendMessage(PREFIX.append(Component.text(exception.getClass().getSimpleName() + ": " + exception.getMessage(), NamedTextColor.RED)));
            throw exception;
        }
    }

    @Override
    public Collection<String> suggest(final CommandSourceStack source, final String[] args) {
        if (args.length <= 1) {
            final String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            return ROOTS.stream().filter(value -> value.startsWith(prefix)).toList();
        }
        if (args[0].equalsIgnoreCase("give") && args.length == 2) {
            return List.of("all", "wand", "quiet", "transformer", "vanilla");
        }
        if (args[0].equalsIgnoreCase("convert") && args.length == 2) {
            return List.of("wand", "apple", "quiet");
        }
        return List.of();
    }

    @Override
    public String permission() {
        return "nix.content-test";
    }

    private void give(final Player player, final String selection) {
        switch (selection.toLowerCase(Locale.ROOT)) {
            case "wand" -> give(player, wand());
            case "quiet" -> give(player, quietWand());
            case "transformer" -> give(player, TestContent.transformer().createItemStack());
            case "vanilla" -> give(player, vanillaCustomComponentStack());
            case "all" -> {
                give(player, wand());
                give(player, quietWand());
                give(player, TestContent.transformer().createItemStack());
                give(player, vanillaCustomComponentStack());
                give(player, ItemStack.of(Material.BUNDLE));
            }
            default -> throw new IllegalArgumentException("Unknown item: " + selection);
        }
        player.sendMessage(PREFIX.append(Component.text("Items added to inventory.")));
    }

    private void inspect(final Player player, final ItemStack stack) {
        final Integer charges = stack.getData(TestContent.charges());
        final String sessionValue = stack.getData(TestContent.sessionValue());
        player.sendMessage(PREFIX.append(Component.text("Bukkit Material: " + stack.getType(), NamedTextColor.YELLOW)));
        player.sendMessage(PREFIX.append(Component.text("Actual ItemType: " + stack.getItemType().getKey(), NamedTextColor.YELLOW)));
        player.sendMessage(PREFIX.append(Component.text(
            "charges=" + charges
                + ", glowing=" + stack.hasData(TestContent.glowing())
                + ", session=" + sessionValue
                + ", itemMeta=" + (stack.hasItemMeta() ? stack.getItemMeta().getClass().getSimpleName() : "none")
        )));
        player.sendMessage(PREFIX.append(Component.text(
            "overrides: charges=" + stack.isDataOverridden(TestContent.charges())
                + ", glowing=" + stack.isDataOverridden(TestContent.glowing())
        )));
    }

    private void setCharges(final Player player, final String[] args) {
        if (args.length != 2) {
            throw new IllegalArgumentException("Usage: /nixtest setcharges <0-100>");
        }
        final int value = Integer.parseInt(args[1]);
        if (value < 0 || value > 100) {
            throw new IllegalArgumentException("Charges must be in range 0-100");
        }
        final ItemStack stack = held(player);
        stack.setData(TestContent.charges(), value);
        player.sendMessage(PREFIX.append(Component.text("Charges set to " + value + ". The projected name should update.")));
    }

    private void toggleMarker(final Player player) {
        final ItemStack stack = held(player);
        if (stack.hasData(TestContent.glowing())) {
            stack.unsetData(TestContent.glowing());
            player.sendMessage(PREFIX.append(Component.text("Glowing marker removed.")));
        } else {
            stack.setData(TestContent.glowing());
            player.sendMessage(PREFIX.append(Component.text("Glowing marker added.")));
        }
    }

    private void refresh(final Player player) {
        Bukkit.getServer().getContentSystem().refreshItemProjections(player);
        player.sendMessage(PREFIX.append(Component.text("Projection revision refreshed for this viewer.")));
    }

    private void cloneTest(final Player player) {
        final ItemStack original = held(player);
        final ItemStack clone = original.clone();
        clone.setAmount(Math.min(99, original.getAmount() + 1));
        final Integer originalCharges = original.getData(TestContent.charges());
        if (originalCharges != null) {
            clone.setData(TestContent.charges(), Math.min(100, originalCharges + 1));
        }
        give(player, clone);
        player.sendMessage(PREFIX.append(Component.text(
            "Clone created. originalType=" + original.getItemType().getKey()
                + ", cloneType=" + clone.getItemType().getKey()
                + ", similar=" + original.isSimilar(clone)
        )));
    }

    private void convert(final Player player, final String[] args) {
        if (args.length != 2) {
            throw new IllegalArgumentException("Usage: /nixtest convert <wand|quiet|apple>");
        }
        final ItemStack source = held(player);
        final ItemStack converted = switch (args[1].toLowerCase(Locale.ROOT)) {
            case "wand" -> source.withType(TestContent.wand());
            case "quiet" -> source.withType(TestContent.quietWand());
            case "apple" -> source.withType(ItemType.APPLE);
            default -> throw new IllegalArgumentException("Unknown target type: " + args[1]);
        };
        player.getInventory().setItemInMainHand(converted);
        player.sendMessage(PREFIX.append(Component.text("Converted to " + converted.getItemType().getKey() + "; inspect retained component patches.")));
    }

    private void roundTrip(final Player player) {
        final ItemStack source = held(player);
        source.setData(TestContent.sessionValue(), "changed-before-roundtrip");
        final byte[] encoded = source.serializeAsBytes();
        final ItemStack decoded = ItemStack.deserializeBytes(encoded);
        player.getInventory().setItemInMainHand(decoded);
        player.sendMessage(PREFIX.append(Component.text(
            "Serialized " + encoded.length + " bytes. actualType=" + decoded.getItemType().getKey()
                + ", material=" + decoded.getType()
                + ", charges=" + decoded.getData(TestContent.charges())
                + ", transient=" + decoded.getData(TestContent.sessionValue())
        )));
    }

    private void drop(final Player player) {
        final ItemStack droppedStack = held(player).clone();
        droppedStack.setAmount(1);
        final Item dropped = player.getWorld().dropItem(player.getEyeLocation(), droppedStack);
        dropped.setPickupDelay(200);
        dropped.setVelocity(player.getLocation().getDirection().multiply(0.5));
        player.sendMessage(PREFIX.append(Component.text("Dropped one item. Destroy it with lava, fire, cactus, or an explosion.")));
    }

    private void checklist(final Player player) {
        player.sendMessage(Component.text("Nix Content System manual checklist", NamedTextColor.GOLD));
        player.sendMessage(Component.text("1. /nixtest give all; compare wand, quiet wand, and vanilla paper tooltips/glint."));
        player.sendMessage(Component.text("2. /nixtest inspect and /nixtest setcharges 9; verify Material and actual ItemType differ."));
        player.sendMessage(Component.text("3. Right-click, hold use to finish, release early, click blocks/entities, mine, and attack."));
        player.sendMessage(Component.text("4. Craft the wand recipe (blaze powder cross around a stick) and use a Crafter."));
        player.sendMessage(Component.text("5. Put items in containers/bundles, relog, restart, and run /nixtest inspect."));
        player.sendMessage(Component.text("6. In creative, move/copy the projected stack and verify identity/components recover."));
        player.sendMessage(Component.text("7. Have two players view equipment, alter charges, then run /nixtest refresh."));
        player.sendMessage(Component.text("8. /nixtest roundtrip, clone, convert, and drop; inspect each result."));
    }

    private void help(final CommandSender sender) {
        sender.sendMessage(Component.text("/nixtest give [all|wand|quiet|transformer|vanilla]", NamedTextColor.AQUA));
        sender.sendMessage(Component.text("/nixtest inspect | setcharges <n> | marker | refresh", NamedTextColor.AQUA));
        sender.sendMessage(Component.text("/nixtest clone | convert <wand|quiet|apple> | roundtrip | drop", NamedTextColor.AQUA));
        sender.sendMessage(Component.text("/nixtest checklist", NamedTextColor.AQUA));
    }

    private static ItemStack wand() {
        final ItemStack stack = TestContent.wand().createItemStack(8);
        stack.setData(TestContent.sessionValue(), "runtime-wand");
        return stack;
    }

    private static ItemStack quietWand() {
        final ItemStack stack = TestContent.quietWand().createItemStack();
        stack.setData(TestContent.sessionValue(), "runtime-quiet");
        return stack;
    }

    private static ItemStack vanillaCustomComponentStack() {
        final ItemStack stack = ItemStack.of(Material.PAPER);
        stack.setData(TestContent.charges(), 7);
        stack.setData(TestContent.glowing());
        stack.setData(TestContent.sessionValue(), "runtime-vanilla");
        return stack;
    }

    private static ItemStack held(final Player player) {
        final ItemStack stack = player.getInventory().getItemInMainHand();
        if (stack.isEmpty()) {
            throw new IllegalStateException("Hold a non-empty item first");
        }
        return stack;
    }

    private static void give(final Player player, final ItemStack stack) {
        player.getInventory().addItem(stack).values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    }
}
