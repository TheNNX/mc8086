package thennx.mcx86.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import thennx.mcx86.MCx86Mod;

public class ComputerInventoryScreen extends AbstractContainerScreen<ComputerInventoryMenu> {
    private final ResourceLocation BACKGROUND_LOCATION = new ResourceLocation(MCx86Mod.MODID, "textures/gui/container/computer_inventory.png");

    public ComputerInventoryScreen(ComputerInventoryMenu menu, Inventory playerInventory, Component component) {
        super(menu, playerInventory, component);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {

    }
}
