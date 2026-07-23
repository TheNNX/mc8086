package thennx.mcx86.item;

import net.minecraft.world.item.ItemStack;
import thennx.mcx86.computer.ComputerBlockEntity;
import thennx.vm8086.devices.IPortSpaceDevice;

import javax.annotation.Nullable;

public class CgaCardItem extends CardItem {
    public CgaCardItem() {
        super(true);
    }

    @Override
    public @Nullable DeviceInstance createDevice(ItemStack stack, ComputerBlockEntity blockEntity) {
        return null;
    }
}
