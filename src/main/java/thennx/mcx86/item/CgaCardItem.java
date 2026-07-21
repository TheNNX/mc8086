package thennx.mcx86.item;

import thennx.mcx86.computer.ComputerBlockEntity;
import thennx.vm8086.devices.IPortSpaceDevice;

import javax.annotation.Nullable;

public class CgaCardItem extends CardItem {
    public CgaCardItem() {
        super(true);
    }

    @Override
    public @Nullable IPortSpaceDevice createDevice(ComputerBlockEntity blockEntity) {
        return null;
    }
}
