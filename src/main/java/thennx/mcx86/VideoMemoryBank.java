package thennx.mcx86;

import thennx.mcx86.computer.ComputerBlockEntity;
import thennx.vm8086.devices.PhysicalMemoryBank;

public class VideoMemoryBank extends PhysicalMemoryBank {
    private ComputerBlockEntity computerBlockEntity = null;

    public VideoMemoryBank(ComputerBlockEntity blockEntity) {
        this.computerBlockEntity = blockEntity;
    }

    @Override
    public void setByte(int offset, byte b) {
        super.setByte(offset, b);
        computerBlockEntity.setScreenDirty(true);
    }

    @Override
    public void setData(byte[] data) {
        super.setData(data);
        computerBlockEntity.setScreenDirty(true);
    }
}
