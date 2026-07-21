package thennx.mcx86.gui;

import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;
import net.minecraftforge.network.NetworkHooks;
import thennx.mcx86.MCx86Mod;
import thennx.mcx86.computer.ComputerBlockEntity;

public class ComputerInventoryMenu extends AbstractContainerMenu {

    private final ComputerBlockEntity blockEntity;
    private final ContainerLevelAccess access;

    public ComputerInventoryMenu(int containerId, Inventory playerInventory, ComputerBlockEntity blockEntity, ContainerLevelAccess access) {
        super(MCx86Mod.COMPUTER_MENU.get(), containerId);
        this.blockEntity = blockEntity;
        this.access = access;

        createSlots(playerInventory, blockEntity.getInventoryHandler());
    }

    public ComputerInventoryMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        super(MCx86Mod.COMPUTER_MENU.get(), containerId);
        ComputerBlockEntity blockEntity = (ComputerBlockEntity) playerInventory.player.level().getBlockEntity(buf.readBlockPos());
        this.blockEntity = blockEntity;
        this.access = ContainerLevelAccess.NULL;

        createSlots(playerInventory, blockEntity.getInventoryHandler());
    }

    private void createSlots(Inventory playerInventory, IItemHandler dataInventory) {
        for (int i = 0; i < dataInventory.getSlots(); i++) {
            this.addSlot(new SlotItemHandler(dataInventory, 0, 0, 0));
        }

        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 9; j++) {
                this.addSlot(new Slot(playerInventory, i * 9 + j, 18 * j, 18 * i));
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