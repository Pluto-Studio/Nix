package club.plutoproject.nix.contentsystem.projection;

import club.plutoproject.nix.contentsystem.projection.ProjectionSource;
import io.papermc.paper.datacomponent.DataComponentAdapter;
import io.papermc.paper.datacomponent.PaperDataComponentType;
import net.minecraft.world.item.ItemStack;
import org.bukkit.craftbukkit.inventory.CraftItemType;
import org.bukkit.inventory.ItemType;
import org.jspecify.annotations.Nullable;

final class ProjectionSourceImpl implements ProjectionSource {
    private final ItemStack source;

    ProjectionSourceImpl(final ItemStack source) {
        this.source = source;
    }

    @Override
    public ItemType itemType() {
        return CraftItemType.minecraftToBukkitNew(this.source.getItem());
    }

    @Override
    public int amount() {
        return this.source.getCount();
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public <T> @Nullable T getData(final io.papermc.paper.datacomponent.DataComponentType.Valued<T> type) {
        final PaperDataComponentType.ValuedImpl<T, ?> paperType =
                (PaperDataComponentType.ValuedImpl<T, ?>) type;
        final Object value = this.source.get(paperType.getHandle());
        return value == null ? null : (T) ((DataComponentAdapter) paperType.getAdapter()).fromVanilla(value);
    }

    @Override
    public boolean hasData(final io.papermc.paper.datacomponent.DataComponentType type) {
        return this.source.has(PaperDataComponentType.bukkitToMinecraft(type));
    }
}
