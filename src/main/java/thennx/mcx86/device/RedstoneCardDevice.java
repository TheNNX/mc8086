package thennx.mcx86.device;

import thennx.mcx86.computer.ComputerBlockEntity;
import thennx.vm8086.IVirtualMachine;
import thennx.vm8086.devices.IPortSpaceDevice;

public class RedstoneCardDevice implements IPortSpaceDevice {
    private final short port;
    private final ComputerBlockEntity blockEntity;

    public RedstoneCardDevice(ComputerBlockEntity blockEntity, short port) {
        this.port = port;
        this.blockEntity = blockEntity;
    }

    @Override
    public boolean matchPort(short port) {
        return port == this.port;
    }

    @Override
    public void writeByte(short port, byte data) {

    }

    @Override
    public byte readByte(short port) {
        return 0;
    }

    @Override
    public boolean onAdded(IVirtualMachine machine) {
        blockEntity.redstoneCardNumber++;
        return IPortSpaceDevice.super.onAdded(machine);
    }

    @Override
    public void onRemoved(IVirtualMachine machine) {
        blockEntity.redstoneCardNumber--;
        IPortSpaceDevice.super.onRemoved(machine);
    }
}
