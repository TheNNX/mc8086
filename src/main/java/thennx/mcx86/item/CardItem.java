package thennx.mcx86.item;

import net.minecraft.world.item.Item;
import thennx.mcx86.AbstarctDeviceFactoryItem;
import thennx.mcx86.IDeviceFactory;

import java.util.ArrayList;
import java.util.List;

public abstract class CardItem extends Item implements IDeviceFactory {
    private final boolean longCard;

    public CardItem(boolean longCard) {
        super(new Properties());

        this.longCard = longCard;
    }

    public final boolean isLongCard() {
        return longCard;
    }
}
