package thennx.mcx86.computer;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.items.SlotItemHandler;
import thennx.mcx86.ComponentHandler;
import thennx.mcx86.ComponentHandler.*;
import thennx.mcx86.gui.SlotComponent;
import thennx.mcx86.item.MotherboardItem;

import java.util.*;

public class ComputerBlockEntityInventoryHandler implements INBTSerializable<CompoundTag> {
    private final ComputerBlockEntity blockEntity;

    private ComponentHandler motherboard;
    private CardHandler[] cardSlots;
    private BayHandler[] baySlots;
    private ComponentHandler[] removableMedia;

    public static class SlotArray {
        public final ComponentHandler[] array;
        public final String name;

        private SlotArray(ComponentHandler[] array, String name) {
            this.array = array;
            this.name = name;
        }
    }

    public List<SlotArray> getSlotArrays() {
        return List.of(
                new SlotArray(new ComponentHandler[]{motherboard}, "motherboard"),
                new SlotArray(baySlots, "bay"),
                new SlotArray(removableMedia, "removable"),
                new SlotArray(cardSlots, "card"));
    }

    public ComputerBlockEntityInventoryHandler(ComputerBlockEntity blockEntity) {
        this.blockEntity = blockEntity;
        this.removableMedia = new ComponentHandler[0];

        createBaySlots();
        createCardSlots();

        this.motherboard = new ComponentHandler(blockEntity, 1) {
            @Override
            public boolean isAccepted(Item item, int count) {
                return item instanceof MotherboardItem;
            }

            @Override
            public void setStackInSlot(int slot, ItemStack stack) {
                super.setStackInSlot(slot, stack);
                popOffInvalidCards();

                if (this.blockEntity.getVM() != null) {
                    this.blockEntity.shutdownVm(true);
                }
            }

            @Override
            public SlotComponent createSlotComponent(int offX, int offY, int slotArrayNum, int slotNum) {
                return new SlotComponent(this, 0, offX + 72 + slotNum * 18, offY + slotArrayNum * 18) {
                    @Override
                    public int getOverlayIconIndex() {
                        return 5;
                    }
                };
            }
        };
    }

    private void createCardSlots() {
        this.cardSlots = new CardHandler[blockEntity.getCardSlots()];
        for (int i = 0; i < this.cardSlots.length; i++) {
            this.cardSlots[i] = new CardHandler(blockEntity, blockEntity.isCardSlotLong(i), i);
        }
    }

    private void createBaySlots() {
        this.baySlots = new BayHandler[blockEntity.getBaySlots()];
        for (int i = 0; i < this.baySlots.length; i++) {
            this.baySlots[i] = new BayHandler(blockEntity);
        }
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();

        for (SlotArray slotArray : getSlotArrays()) {
            int index = 0;

            for (ComponentHandler slot : slotArray.array) {
                slot.save(slotArray.name + index, tag);
                index++;
            }
        }

        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        motherboard.load(blockEntity, "motherboard0", tag);

        createCardSlots();
        createBaySlots();

        for (int i = 0; i < baySlots.length; i++) {
            baySlots[i].load(blockEntity, "bay" + i, tag);
        }

        for (int i = 0; i < removableMedia.length; i++) {
            removableMedia[i].load(blockEntity, "removable" + i, tag);
        }

        for (int i = 0; i < cardSlots.length; i++) {
            cardSlots[i].load(blockEntity, "card" + i, tag);
        }

        blockEntity.setChanged();
    }

    public void popOffInvalidCards() {
        int maxCards = 0;

        if (this.motherboard.getItem() instanceof MotherboardItem motherboardItem) {
            maxCards = motherboardItem.getMaxCards();
        }

        if (blockEntity.getLevel() != null && !blockEntity.getLevel().isClientSide()) {
            for (int i = 0; i < this.cardSlots.length; i++) {
                CardHandler slot = this.cardSlots[i];

                if (i >= maxCards) {
                    Block.popResource(blockEntity.getLevel(), blockEntity.getBlockPos(), slot.getItemStack());
                    slot.setStackInSlot(0, ItemStack.EMPTY);
                    if (slot.device != null) {
                        blockEntity.removeDevice(slot.device);
                    }
                }
            }
        }
    }

    public boolean insertItem(ItemStack stack) {
        for (SlotArray slotArray : getSlotArrays()) {
            for (ComponentHandler slot : slotArray.array) {
                if (slot.isAccepted(stack) && slot.getItemStack().isEmpty()) {
                    ItemStack newStack = stack.split(1);
                    slot.setStackInSlot(0, newStack);
                    return true;
                }
            }
        }

        return false;
    }

    public ItemStack[] getCards() {
        ItemStack[] cards = new ItemStack[this.cardSlots.length];
        for (int i = 0; i < this.cardSlots.length; i++) {
            cards[i] = this.cardSlots[i].getItemStack();
        }
        return cards;
    }

    public ItemStack[] getBayItems() {
        ItemStack[] result = new ItemStack[baySlots.length];

        for (int i = 0; i < result.length; i++) {
            result[i] = baySlots[i].getItemStack();
        }

        return result;
    }

    public ItemStack getMotherboard() {
        return motherboard.getItemStack();
    }
}
