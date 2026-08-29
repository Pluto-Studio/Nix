package club.plutoproject.nix.testplugin;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.java.JavaPlugin;

public final class NixContentTestPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        this.getLifecycleManager().registerEventHandler(
            LifecycleEvents.COMMANDS,
            event -> event.registrar().register(
                "nixtest",
                "Manual tests for the Nix Content System",
                List.of("nct"),
                new NixTestCommand()
            )
        );
        this.registerRecipe();
        this.getComponentLogger().info("Nix Content System test plugin enabled. Run /nixtest help in game.");
    }

    private void registerRecipe() {
        final ItemStack result = TestContent.wand().createItemStack();
        result.setAmount(2);
        final ShapedRecipe recipe = new ShapedRecipe(new NamespacedKey(this, "test_wands"), result);
        recipe.shape(" B ", "BSB", " B ");
        recipe.setIngredient('B', Material.BLAZE_POWDER);
        recipe.setIngredient('S', Material.STICK);
        Bukkit.addRecipe(recipe);
    }
}
