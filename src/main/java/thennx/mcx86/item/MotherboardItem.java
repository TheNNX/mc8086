package thennx.mcx86.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import thennx.mcx86.MCx86Mod;
import thennx.vm8086.devices.IPortSpaceDevice;

import java.util.List;

public class MotherboardItem extends Item {
    private final int maxCards;
    private final boolean builtinCpu;

    public MotherboardItem(int maxCards, boolean builtinCpu) {
        super(new Properties());
        this.maxCards = maxCards;
        this.builtinCpu = builtinCpu;
    }

    public static class CardSlot {
        public final CardItem[] acceptedCards;
        private ItemStack stack = ItemStack.EMPTY;
        public IPortSpaceDevice device = null;

        public CardSlot(CardItem[] acceptedCards) {
            this.acceptedCards = acceptedCards;
        }

        public boolean isAccepted(Item item, int count) {
            if (count != 1)
                return false;

            for (Item i : acceptedCards) {
                if (item == i)
                    return true;
            }

            return false;
        }

        public boolean isAccepted(ItemStack itemStack) {
            return isAccepted(itemStack.getItem(), itemStack.getCount());
        }

        public ItemStack replaceItemStack(ItemStack stack) {
            return replaceItemStack(stack, false);
        }

        public ItemStack replaceItemStack(ItemStack stack, boolean ignoreChecks) {
            if (!isAccepted(stack) && !ignoreChecks) {
                return null;
            }

            ItemStack old = this.stack;
            this.stack = stack;
            return old;
        }

        public ItemStack getItemStack() {
            return this.stack;
        }

        public Item getItem() {
            return this.stack.getItem();
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        if (this.builtinCpu) {
            tooltip.add(
                    Component.translatableWithFallback("tooltip." + MCx86Mod.MODID + ".has_8086", " - Built-in 8086 CPU")
                            .withStyle(ChatFormatting.YELLOW)
                            .withStyle(ChatFormatting.ITALIC));
        }
        super.appendHoverText(stack, level, tooltip, flag);
    }

    public CardSlot[] createCardSlots() {
        CardSlot[] slots = new CardSlot[maxCards];

        for (int i = 0; i < maxCards; i++) {
            slots[i] = new CardSlot(CardItem.getCards());
        }

        return slots;
    }
}
