package thennx.mcx86.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import thennx.mcx86.ComponentHandler;
import thennx.mcx86.MCx86Mod;

public class ComputerInventoryScreen extends AbstractContainerScreen<ComputerInventoryMenu> {
    private final ResourceLocation BACKGROUND_LOCATION = new ResourceLocation(MCx86Mod.MODID, "textures/gui/container/computer_inventory.png");

    public static final int IMAGE_WIDTH = 176;
    public static final int IMAGE_HEIGHT = 186;
    public static final int PLAYER_INV_START_Y = 104;
    public static final int PLAYER_INV_LABEL_START_Y = PLAYER_INV_START_Y - 12;
    public static final int INV_START_X = 8;
    public static final int INV_START_Y = 18;
    public static final int PLAYER_HOTBAR_START_Y = 162;

    private static final int SPECIAL_ELEMENTS_UV_Y = 238;

    public ComputerInventoryScreen(ComputerInventoryMenu menu, Inventory playerInventory, Component component) {
        super(menu, playerInventory, component);
        this.imageWidth = IMAGE_WIDTH;
        this.imageHeight = IMAGE_HEIGHT;
        this.inventoryLabelY = PLAYER_INV_LABEL_START_Y;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(BACKGROUND_LOCATION, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight);

        for (Slot s : menu.slots) {
            if (!s.isActive()) {
                graphics.blit(BACKGROUND_LOCATION, this.leftPos + s.x - 1, this.topPos + s.y - 1, 0, SPECIAL_ELEMENTS_UV_Y, 18, 18);
            }
            else if (s instanceof SlotComponent slotComponent){
                if (s.getItem().isEmpty()) {
                    graphics.blit(BACKGROUND_LOCATION, this.leftPos + s.x - 1, this.topPos + s.y - 1, slotComponent.getOverlayIconIndex() * 18, SPECIAL_ELEMENTS_UV_Y, 18, 18);
                }

                ComponentHandler handler = slotComponent.getComponentHandler();
                
            }
        }
    }
}
