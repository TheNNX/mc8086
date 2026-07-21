package thennx.mcx86.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class ComputerInventoryScreen extends AbstractContainerScreen<ComputerInventoryMenu> {
    public ComputerInventoryScreen(ComputerInventoryMenu p_97741_, Inventory playerInventory, Component p_97743_) {
        super(p_97741_, playerInventory, p_97743_);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float p_97788_, int p_97789_, int p_97790_) {

    }
}
