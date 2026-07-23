package thennx.mcx86.gui;

import net.minecraftforge.items.SlotItemHandler;
import thennx.mcx86.ComponentHandler;

public abstract class SlotComponent extends SlotItemHandler {
    private final ComponentHandler componentHandler;

    public SlotComponent(ComponentHandler itemHandler, int index, int xPosition, int yPosition) {
        super(itemHandler, index, xPosition, yPosition);
        this.componentHandler = itemHandler;
    }

    public ComponentHandler getComponentHandler() {
        return this.componentHandler;
    }

    public abstract int getOverlayIconIndex();
}
