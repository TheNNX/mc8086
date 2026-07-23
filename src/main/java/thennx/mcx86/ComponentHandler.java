package thennx.mcx86;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import thennx.mcx86.computer.ComputerBlockEntity;
import thennx.mcx86.gui.SlotComponent;
import thennx.mcx86.item.BayItem;
import thennx.mcx86.item.CardItem;
import thennx.mcx86.item.MotherboardItem;
import thennx.vm8086.devices.IPortSpaceDevice;
import thennx.vm8086.devices.IStateful;

import java.io.IOException;

public abstract class ComponentHandler implements IItemHandler, IItemHandlerModifiable {
    private ItemStack stack = ItemStack.EMPTY;
    public IPortSpaceDevice device = null;
    private final int limit;
    protected ComputerBlockEntity blockEntity;

    public ComponentHandler(ComputerBlockEntity blockEntity, int limit) {
        this.limit = limit;
        this.blockEntity = blockEntity;
    }

    public abstract boolean isAccepted(Item item, int count);

    public boolean isAccepted(ItemStack itemStack) {
        return isAccepted(itemStack.getItem(), itemStack.getCount());
    }

    public ItemStack getItemStack() {
        return this.stack.copy();
    }

    public Item getItem() {
        return this.stack.getItem();
    }

    public void save(String name, CompoundTag inventoryTag) {
        CompoundTag tag = new CompoundTag();
        tag.put("item", getItemStack().save(new CompoundTag()));

        if (device instanceof IStateful stateful) {
            CompoundTag deviceTag = new CompoundTag();
            try {
                stateful.save(new NbtStateStorage(deviceTag));
                tag.put("device", deviceTag);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        inventoryTag.put(name, tag);
    }

    public void load(ComputerBlockEntity blockEntity, String name, CompoundTag parent) {
        CompoundTag tag = parent.getCompound(name);
        setStackInSlot(0, ItemStack.of(tag.getCompound("item")));

        if (device instanceof IStateful stateful && tag.contains("device", Tag.TAG_COMPOUND)) {
            try {
                stateful.load(new NbtStateStorage(tag.getCompound("device")));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
        this.stack = stack;
        blockEntity.setChanged();

        if (blockEntity.getVM() == null)
            return;

        if (!getItemStack().isEmpty()) {
            if (device != null) {
                blockEntity.removeDevice(device);
                device = null;
            }
        }

        if (stack.getItem() instanceof IDeviceFactory deviceFactory) {
            device = deviceFactory.createDevice(blockEntity);
            blockEntity.addDevice(device);
        }
    }

    @Override
    public int getSlots() {
        return 1;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return this.stack;
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        stack = stack.copy();
        if (this.isAccepted(stack) && this.stack.isEmpty()) {
            ItemStack split = stack.split(1);
            if (!simulate) {
                setStackInSlot(slot, split);
            }
        }

        return stack;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (this.stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = this.stack.copy();
        if (!simulate) {
            setStackInSlot(0, ItemStack.EMPTY);
        }

        return stack;
    }

    @Override
    public int getSlotLimit(int slot) {
        return this.limit;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return isAccepted(stack);
    }

    public SlotComponent createSlotComponent(int offX, int offY, int slotArrayNum, int slotNum) {
        return new SlotComponent(this, 0, offX + slotNum * 18, offY + slotArrayNum * 18) {
            @Override
            public int getOverlayIconIndex() {
                return 1;
            }
        };
    }

    public static class CardHandler extends ComponentHandler {
        private final boolean isLong;
        private final int cardIndex;

        public CardHandler(ComputerBlockEntity blockEntity, boolean isLong, int cardIndex) {
            super(blockEntity, 1);
            this.isLong = isLong;
            this.cardIndex = cardIndex;
        }

        @Override
        public boolean isAccepted(Item item, int count) {
            if (!(item instanceof CardItem cardItem))
                return false;
            if (!isLong && cardItem.isLongCard())
                return false;
            if (!(blockEntity.getMotherboard().getItem() instanceof MotherboardItem motherboardItem))
                return false;
            return cardIndex < motherboardItem.getMaxCards();
        }

        @Override
        public SlotComponent createSlotComponent(int offX, int offY, int slotArrayNum, int slotNum) {
            class CardSlotComponent extends SlotComponent {
                public final int cardNum;

                public CardSlotComponent(ComponentHandler itemHandler, int index, int xPosition, int yPosition, int cardNum) {
                    super(itemHandler, index, xPosition, yPosition);
                    this.cardNum = cardNum;
                }

                @Override
                public boolean isActive() {
                    if (!(blockEntity.getMotherboard().getItem() instanceof MotherboardItem motherboardItem))
                        return false;
                    return cardNum < motherboardItem.getMaxCards();
                }

                @Override
                public int getOverlayIconIndex() {
                    return 6;
                }
            }

            return new CardSlotComponent(this, 0, offX + 18 * slotNum, offY + 18 * slotArrayNum, cardIndex);
        }
    }

    public static class BayHandler extends ComponentHandler {
        public BayHandler(ComputerBlockEntity blockEntity) {
            super(blockEntity, 1);
        }

        @Override
        public boolean isAccepted(Item item, int count) {
            return item instanceof BayItem;
        }
    }
}