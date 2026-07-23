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

public class MotherboardItem extends Item {
    private final int maxCards;
    private final boolean builtinCpu;

    public MotherboardItem(int maxCards, boolean builtinCpu) {
        super(new Properties());
        this.maxCards = maxCards;
        this.builtinCpu = builtinCpu;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        if (this.builtinCpu) {
            tooltip.add(
                    Component.translatableWithFallback("tooltip." + MCx86Mod.MODID + ".has_8086", " - Built-in 8086 CPU")
                            .withStyle(ChatFormatting.YELLOW)
                            .withStyle(ChatFormatting.ITALIC));
        }
        tooltip.add(
                Component.translatable("tooltip." + MCx86Mod.MODID + ".has_isa_slots", maxCards)
                        .withStyle(ChatFormatting.GRAY)
                        .withStyle(ChatFormatting.ITALIC));
        super.appendHoverText(stack, level, tooltip, flag);
    }

    public int getMaxCards() {
        return this.maxCards;
    }
}
