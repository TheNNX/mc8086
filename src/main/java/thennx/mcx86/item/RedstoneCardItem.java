package thennx.mcx86.item;

import thennx.mcx86.computer.ComputerBlockEntity;
import thennx.mcx86.device.RedstoneCardDevice;
import thennx.vm8086.devices.IPortSpaceDevice;

import javax.annotation.Nullable;

public class RedstoneCardItem extends CardItem {
    public RedstoneCardItem() {
        super(false);
    }

    @Override
    public @Nullable IPortSpaceDevice createDevice(ComputerBlockEntity blockEntity) {
        return new RedstoneCardDevice(blockEntity, (short) 0xE0);
    }
}
