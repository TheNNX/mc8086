package thennx.mcx86.computer;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import thennx.mcx86.NbtStateStorage;
import thennx.mcx86.item.BayItem;
import thennx.mcx86.item.CardItem;
import thennx.mcx86.item.MotherboardItem;
import thennx.vm8086.devices.IStateful;

import java.io.IOException;

public class ComputerBlockEntityInventoryHandler implements IItemHandler, IItemHandlerModifiable, INBTSerializable<CompoundTag> {
    private final ComputerBlockEntity blockEntity;
    private ItemStack motherboard = ItemStack.EMPTY;
    private ItemStack[] bayItems = { ItemStack.EMPTY, ItemStack.EMPTY };
    private MotherboardItem.CardSlot[] cardSlots = new MotherboardItem.CardSlot[0];

    public ComputerBlockEntityInventoryHandler(ComputerBlockEntity blockEntity) {
        this.blockEntity = blockEntity;
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();

        tag.put("motherboard", motherboard.save(new CompoundTag()));
        for (int i = 0; i < bayItems.length; i++) {
            tag.put(String.format("bay%d", i), bayItems[i].save(new CompoundTag()));
        }

        for (int i = 0; i < cardSlots.length; i++) {
            tag.put("slot" + i, cardSlots[i].getItemStack().save(new CompoundTag()));
            if (cardSlots[i].device instanceof IStateful stateful) {
                CompoundTag deviceTag = new CompoundTag();
                try {
                    stateful.save(new NbtStateStorage(deviceTag));
                    tag.put("device" + i, deviceTag);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        replaceMotherboard(ItemStack.of(tag.getCompound("motherboard")));

        for (int i = 0; i < bayItems.length; i++) {
            if (!tag.contains(String.format("bay%d", i), Tag.TAG_COMPOUND)) {
                continue;
            }

            bayItems[i] = ItemStack.of(tag.getCompound(String.format("bay%d", i)));
            blockEntity.onBayItemAdded(bayItems[i]);
        }

        for (int i = 0; i < cardSlots.length; i++) {
            if (!tag.contains("slot" + i, Tag.TAG_COMPOUND)) {
                continue;
            }

            ItemStack stack = ItemStack.of(tag.getCompound("slot" + i));
            cardSlots[i].replaceItemStack(stack, true);

            if (cardSlots[i].getItem() instanceof CardItem cardItem) {
                cardSlots[i].device = cardItem.createDevice(blockEntity);
                blockEntity.addDevice(cardSlots[i].device);
            }
            else {
                cardSlots[i].device = null;
            }

            if (cardSlots[i].device instanceof IStateful stateful && tag.contains("device" + i, Tag.TAG_COMPOUND)) {
                try {
                    stateful.load(new NbtStateStorage(tag.getCompound("device" + i)));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        blockEntity.setChanged();
    }

    public void popOffCards(MotherboardItem.CardSlot[] cards) {
        if (blockEntity.getLevel() != null && !blockEntity.getLevel().isClientSide()) {
            for (MotherboardItem.CardSlot slot : this.cardSlots) {
                Block.popResource(blockEntity.getLevel(), blockEntity.getBlockPos(), slot.getItemStack());
                blockEntity.removeDevice(slot.device);
            }
        }

        this.cardSlots = cards;
    }

    public boolean replaceMotherboard(ItemStack stack) {
        ItemStack newStack = stack.split(1);
        ItemStack oldMotherboard = motherboard;
        MotherboardItem.CardSlot[] newCards;

        if ((newStack.getItem() instanceof MotherboardItem motherboardItem)) {
            newCards = motherboardItem.createCardSlots();
        }
        else if (newStack.isEmpty()) {
            newCards = new MotherboardItem.CardSlot[0];
        }
        else {
            return false;
        }

        popOffCards(newCards);
        motherboard = newStack;

        if (!oldMotherboard.isEmpty() && blockEntity.getLevel() != null && !blockEntity.getLevel().isClientSide()) {
            Block.popResource(blockEntity.getLevel(), blockEntity.getBlockPos(), oldMotherboard);
        }

        blockEntity.shutdownVm(true);
        blockEntity.setChanged();
        return true;
    }

    public boolean insertCard(ItemStack stack) {
        MotherboardItem.CardSlot slot = null;
        Level level = blockEntity.getLevel();

        if (!(stack.getItem() instanceof CardItem cardItem)) {
            return false;
        }

        for (MotherboardItem.CardSlot cardSlot : this.cardSlots) {
            if (cardSlot.getItemStack().isEmpty()) {
                slot = cardSlot;
                break;
            }
        }

        if (slot == null)
            return false;

        ItemStack newStack = stack.split(1);
        ItemStack oldCard = slot.replaceItemStack(newStack);

        if (level == null || level.isClientSide()) {
            return true;
        }

        if (!oldCard.isEmpty()) {
            Block.popResource(level, blockEntity.getBlockPos(), oldCard);

            if (slot.device != null) {
                blockEntity.removeDevice(slot.device);
            }
        }

        slot.device = cardItem.createDevice(blockEntity);
        blockEntity.addDevice(slot.device);
        return true;
    }

    public boolean insertBayItem(ItemStack stack) {
        int index;

        for (index = 0; index < bayItems.length; index++) {
            if (bayItems[index].isEmpty()) {
                break;
            }
        }

        if (index == bayItems.length) {
            return false;
        }

        ItemStack newStack = stack.split(1);
        bayItems[index] = newStack;
        blockEntity.onBayItemAdded(bayItems[index]);
        return true;
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
        if (slot == 0) {
            replaceMotherboard(stack);
        }

        slot -= 1;
        if (slot > 0 && slot < cardSlots.length) {
            cardSlots[slot].replaceItemStack(stack);
        }

        slot -= cardSlots.length;
        if (slot > 0 && slot < bayItems.length) {
            bayItems[slot] = stack;
        }

        throw new RuntimeException("Slot index out of range");
    }

    @Override
    public int getSlots() {
        return 1 + this.cardSlots.length + this.bayItems.length;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        if (slot == 0) {
            return getMotherboard();
        }

        slot -= 1;
        if (slot > 0 && slot < cardSlots.length) {
            return getCards()[slot];
        }

        slot -= cardSlots.length;
        if (slot > 0 && slot < bayItems.length) {
            return getBayItems()[slot];
        }

        throw new RuntimeException("Slot index out of range");
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (getStackInSlot(slot).isEmpty()) {
            ItemStack remainder = stack.copy();
            ItemStack toInsert = remainder.split(1);

            if (isItemValid(slot, toInsert)) {
                if (!simulate)
                    setStackInSlot(slot, toInsert);
                return remainder;
            }
        }
        return stack;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public int getSlotLimit(int slot) {
        return 1;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        if (slot == 0) {
            return stack.getItem() instanceof MotherboardItem && stack.getCount() <= 1;
        }

        slot -= 1;
        if (slot > 0 && slot < cardSlots.length) {
            return this.cardSlots[slot].isAccepted(stack) && stack.getCount() <= 1;
        }

        slot -= cardSlots.length;
        if (slot > 0 && slot < bayItems.length) {
            return stack.getItem() instanceof BayItem && stack.getCount() <= 1;
        }

        throw new RuntimeException("Slot index out of range");
    }

    public ItemStack[] getCards() {
        ItemStack[] cards = new ItemStack[this.cardSlots.length];
        for (int i = 0; i < this.cardSlots.length; i++) {
            cards[i] = this.cardSlots[i].getItemStack();
        }
        return cards;
    }

    public ItemStack[] getBayItems() {
        return this.bayItems;
    }

    public ItemStack getMotherboard() {
        return motherboard;
    }
}
