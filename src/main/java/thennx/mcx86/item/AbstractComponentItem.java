package thennx.mcx86.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import thennx.mcx86.IDeviceFactory;

public abstract class AbstractComponentItem extends Item implements IDeviceFactory {
    public AbstractComponentItem(Properties properties) {
        super(properties);
    }
}
