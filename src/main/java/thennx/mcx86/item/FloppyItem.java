package thennx.mcx86.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import thennx.mcx86.MCx86Mod;

import java.util.List;

public class FloppyItem extends Item {
    public final int cylinders;
    public final int heads;
    public final int sectors;
    public final int sectorSize;
    public final Formfactor formfactor;

    public enum Formfactor {
        F_3_5,
        F_5_1_4,
        F_8
    }

    public FloppyItem(Formfactor factor, int c, int h, int s, int sectorSize) {
        super(new Properties().stacksTo(1).durability(100).setNoRepair());
        this.cylinders = c;
        this.heads = h;
        this.sectors = s;
        this.formfactor = factor;
        this.sectorSize = sectorSize;
    }

    public long getSize() {
        return (long) cylinders * heads * sectors * sectorSize;
    }

    @Override
    public String getDescriptionId() {
        String itemId = switch (formfactor) {
            case F_3_5 -> "floppy_disk_35";
            case F_5_1_4 -> "floppy_disk_514";
            case F_8 -> "floppy_disk_8";
            default -> "floppy_disk";
        };

        return String.format("item.%s.%s", MCx86Mod.MODID, itemId);
    }

    @Override
    public void appendHoverText(ItemStack p_41421_, @Nullable Level p_41422_, List<Component> tooltip, TooltipFlag p_41424_) {
        super.appendHoverText(p_41421_, p_41422_, tooltip, p_41424_);

        tooltip.add(Component.literal(String.format("%d kB", getSize() / 1024)).withStyle(ChatFormatting.ITALIC, ChatFormatting.GRAY));
    }
}
