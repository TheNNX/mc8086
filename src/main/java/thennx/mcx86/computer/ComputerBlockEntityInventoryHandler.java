package thennx.mcx86.computer;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import org.checkerframework.checker.units.qual.C;
import org.jetbrains.annotations.NotNull;
import thennx.mcx86.ComponentSlot;
import thennx.mcx86.ComponentSlot.*;
import thennx.mcx86.NbtStateStorage;
import thennx.mcx86.item.BayItem;
import thennx.mcx86.item.CardItem;
import thennx.mcx86.item.MotherboardItem;
import thennx.vm8086.devices.IPortSpaceDevice;
import thennx.vm8086.devices.IStateful;

import java.io.IOException;
import java.util.*;

public class ComputerBlockEntityInventoryHandler implements INBTSerializable<CompoundTag> {
    private final ComputerBlockEntity blockEntity;

    private ComponentSlot motherboard;
    private CardSlot[] cardSlots;
    private BaySlot[] baySlots;

    private class SlotArray {
        private final ComponentSlot[] array;
        private final String name;

        private SlotArray(ComponentSlot[] array, String name) {
            this.array = array;
            this.name = name;
        }
    }

    private List<SlotArray> getSlotArrays() {
        return List.of(new SlotArray(new ComponentSlot[]{motherboard}, "motherboard"), new SlotArray(cardSlots, "card"), new SlotArray(baySlots, "bay"));
    }

    public Map<String, ComponentSlot[]> getSlotArrayMap() {
        HashMap<String, ComponentSlot[]> result = new HashMap<>();

        for (SlotArray sa : getSlotArrays()) {
            result.put(sa.name, sa.array);
        }

        return result;
    }

    public ComputerBlockEntityInventoryHandler(ComputerBlockEntity blockEntity) {
        this.blockEntity = blockEntity;

        createBaySlots();
        createCardSlots();

        this.motherboard = new ComponentSlot(blockEntity, 1) {
            @Override
            public boolean isAccepted(Item item, int count) {
                return item instanceof MotherboardItem;
            }

            @Override
            public void setStackInSlot(int slot, ItemStack stack) {
                super.setStackInSlot(slot, stack);
                popOffInvalidCards();
            }
        };
    }

    private void createCardSlots() {
        this.cardSlots = new CardSlot[blockEntity.getCardSlots()];
        for (int i = 0; i < this.cardSlots.length; i++) {
            this.cardSlots[i] = new CardSlot(blockEntity, blockEntity.isCardSlotLong(i), i);
        }
    }

    private void createBaySlots() {
        this.baySlots = new BaySlot[blockEntity.getBaySlots()];
        for (int i = 0; i < this.baySlots.length; i++) {
            this.baySlots[i] = new BaySlot(blockEntity);
        }
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();

        for (SlotArray slotArray : getSlotArrays()) {
            int index = 0;

            for (ComponentSlot slot : slotArray.array) {
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
                CardSlot slot = this.cardSlots[i];

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
            for (ComponentSlot slot : slotArray.array) {
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
