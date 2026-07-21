package thennx.mcx86;

import thennx.mcx86.computer.ComputerBlockEntity;
import thennx.vm8086.devices.IPortSpaceDevice;

import javax.annotation.Nullable;

public interface IDeviceFactory {
    @Nullable
    IPortSpaceDevice createDevice(ComputerBlockEntity blockEntity);
}
