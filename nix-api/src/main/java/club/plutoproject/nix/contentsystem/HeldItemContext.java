package club.plutoproject.nix.contentsystem;

import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;

/**
 * Common context for interactions with a player's held item.
 */
@ApiStatus.Experimental
@ApiStatus.NonExtendable
public interface HeldItemContext {

    /**
     * Gets the player performing the interaction.
     *
     * @return the player
     */
    @Contract(pure = true)
    Player player();

    /**
     * Gets the live authoritative held stack.
     *
     * @return the held stack
     */
    @Contract(pure = true)
    ItemStack itemStack();

    /**
     * Gets the hand used for the interaction.
     *
     * @return the hand slot
     */
    @Contract(pure = true)
    EquipmentSlot hand();
}
