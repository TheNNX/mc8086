package thennx.mcx86;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class MotherboardItem extends Item {
    public MotherboardItem(Properties p_41383_) {
        super(p_41383_);
    }

    @Override
    public void appendHoverText(ItemStack p_41421_, @Nullable Level p_41422_, List<Component> tooltip, TooltipFlag p_41424_) {
        tooltip.add(
                Component.translatableWithFallback("tooltip." + MCx86Mod.MODID + ".has_8086", " - Built-in 8086 CPU")
                        .withStyle(ChatFormatting.YELLOW)
                        .withStyle(ChatFormatting.ITALIC));
        super.appendHoverText(p_41421_, p_41422_, tooltip, p_41424_);
    }
}
