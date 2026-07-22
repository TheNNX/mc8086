package thennx.mcx86.gui;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import thennx.mcx86.ComponentSlot;
import thennx.mcx86.MCx86Mod;
import thennx.mcx86.computer.ComputerBlockEntity;

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
        ComputerBlockEntity blockEntity = (ComputerBlockEntity) playerInventory.player.level().getBlockEntity(buf.readBlockPos());
        this.blockEntity = blockEntity;
        this.access = ContainerLevelAccess.NULL;

        createSlots(playerInventory);
    }

    private void createSlots(Inventory playerInventory) {
        this.slots.clear();
        int slotArrayNum = 0;

        for (ComponentSlot[] slotArray : blockEntity.getSlotArrays()) {
            int slotNum = 0;

            for (ComponentSlot slot : slotArray) {
                this.addSlot(slot.createItemHandler(slotArrayNum, slotNum));
                slotNum++;
            }

            slotArrayNum++;
        }

        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 9; j++) {
                this.addSlot(new Slot(playerInventory, i * 9 + j, 18 * j, 18 * (4 - i) + 18 * 4));
            }
        }
    }

    @Override
    public ItemStack quickMoveStack(Player p_38941_, int p_38942_) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return AbstractContainerMenu.stillValid(this.access, player, MCx86Mod.COMPUTER_BLOCK.get());
    }
}