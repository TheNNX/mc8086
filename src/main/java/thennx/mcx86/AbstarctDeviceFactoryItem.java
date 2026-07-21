package thennx.mcx86;

import net.minecraft.world.item.Item;
import thennx.mcx86.computer.ComputerBlockEntity;

public abstract class AbstarctDeviceFactoryItem extends Item {
    public AbstarctDeviceFactoryItem(Properties properties) {
        super(properties);
    }

    public abstract IDeviceFactory createDevice(ComputerBlockEntity blockEntity);
}
