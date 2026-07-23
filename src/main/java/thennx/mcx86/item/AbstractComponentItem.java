package thennx.mcx86.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import thennx.mcx86.IDeviceFactory;
import thennx.vm8086.devices.IDevice;

public abstract class AbstractComponentItem extends Item implements IDeviceFactory {
    public AbstractComponentItem(Properties properties) {
        super(properties);
    }

    public void onDeviceAdded(ItemStack self, IDevice added) {

    }

    public void onDeviceRemoved(ItemStack selfStack, IDevice removed) {

    }
}
