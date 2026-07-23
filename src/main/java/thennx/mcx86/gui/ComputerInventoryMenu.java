package thennx.mcx86.gui;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import thennx.mcx86.ComponentHandler;
import thennx.mcx86.MCx86Mod;
import thennx.mcx86.computer.ComputerBlockEntity;
import thennx.mcx86.computer.ComputerBlockEntityInventoryHandler;

public class ComputerInventoryMenu extends AbstractContainerMenu {

    private final ComputerBlockEntity blockEntity;
    private final ContainerLevelAccess access;

    public ComputerInventoryMenu(int containerId, Inventory playerInventory, ComputerBlockEntity blockEntity, ContainerLevelAccess access) {
        super(MCx86Mod.COMPUTER_MENU.get(), containerId);
        this.blockEntity = blockEntity;
        this.access = access;

        createSlots(playerInventory);
    }

    public ComputerInventoryMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        super(MCx86Mod.COMPUTER_MENU.get(), containerId);
        this.blockEntity = (ComputerBlockEntity) playerInventory.player.level().getBlockEntity(buf.readBlockPos());
        this.access = ContainerLevelAccess.NULL;

        createSlots(playerInventory);
    }

    private void createSlots(Inventory playerInventory) {
        this.slots.clear();
        int slotArrayNum = 0;

        for (ComputerBlockEntityInventoryHandler.SlotArray slotArray : blockEntity.getSlotArrays()) {
            int slotNum = 0;

            for (ComponentHandler slot : slotArray.array) {
                this.addSlot(slot.createSlotComponent(ComputerInventoryScreen.INV_START_X, ComputerInventoryScreen.INV_START_Y, slotArrayNum, slotNum));
                slotNum++;
            }

            slotArrayNum++;
        }

        for (int j = 0; j < 9; j++) {
            this.addSlot(new Slot(playerInventory,  j, ComputerInventoryScreen.INV_START_X + 18 * j, ComputerInventoryScreen.PLAYER_HOTBAR_START_Y));
        }
        for (int i = 1; i < 4; i++) {
            for (int j = 0; j < 9; j++) {
                this.addSlot(new Slot(playerInventory, i * 9 + j, ComputerInventoryScreen.INV_START_X + 18 * j, ComputerInventoryScreen.PLAYER_INV_START_Y + 18 * (i - 1)));
            }
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int quickMovedSlotIndex) {
        ItemStack quickMovedStack = ItemStack.EMPTY;
        if (slots.size() <= quickMovedSlotIndex)
            return quickMovedStack;

        Slot quickMovedSlot = this.slots.get(quickMovedSlotIndex);

        int playerInvStart = slots.size() - 4 * 9;
        int playerInvEnd = slots.size();

        if (quickMovedSlot.hasItem()) {
            ItemStack rawStack = quickMovedSlot.getItem();
            quickMovedStack = rawStack.copy();

            if (quickMovedSlotIndex >= playerInvStart) {
                if (!this.moveItemStackTo(rawStack, 0, playerInvStart, false)) {
                    if (quickMovedSlotIndex >= playerInvStart + 9) {
                        if (!this.moveItemStackTo(rawStack, playerInvStart, playerInvStart + 9, false)) {
                            return ItemStack.EMPTY;
                        }
                    }
                    else if (!this.moveItemStackTo(rawStack, playerInvStart + 9, playerInvEnd, false)) {
                        return ItemStack.EMPTY;
                    }
                }
            }
            else if (!this.moveItemStackTo(rawStack, playerInvStart, playerInvEnd, false)) {
                return ItemStack.EMPTY;
            }

            if (rawStack.isEmpty()) {
                quickMovedSlot.set(ItemStack.EMPTY);
            } else {
                quickMovedSlot.setChanged();
            }

            if (rawStack.getCount() == quickMovedStack.getCount()) {
                return ItemStack.EMPTY;
            }

            quickMovedSlot.onTake(player, rawStack);
        }

        return quickMovedStack;
    }

    @Override
    public boolean stillValid(Player player) {
        return AbstractContainerMenu.stillValid(this.access, player, MCx86Mod.COMPUTER_BLOCK.get());
    }
}