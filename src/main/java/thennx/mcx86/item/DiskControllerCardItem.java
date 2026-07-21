package thennx.mcx86.item;

import thennx.mcx86.computer.ComputerBlockEntity;
import thennx.vm8086.IVirtualMachine;
import thennx.vm8086.devices.BarebonesATAChannel;
import thennx.vm8086.devices.DummyIdeDrive;
import thennx.vm8086.devices.IPortSpaceDevice;

import javax.annotation.Nullable;
import java.util.List;

public class DiskControllerCardItem extends CardItem {
    public DiskControllerCardItem() {
        super(true);
    }

    @Override
    public @Nullable IPortSpaceDevice createDevice(ComputerBlockEntity blockEntity) {
        IVirtualMachine vm = blockEntity.getVM();
        List<IPortSpaceDevice> deviceList = vm.getDevices();

        int numATAChannels = 0;
        for (IPortSpaceDevice device : deviceList) {
            if (device instanceof BarebonesATAChannel) {
                numATAChannels++;
            }
        }

        if (numATAChannels > 2)
            return null;
        if (numATAChannels == 1)
            return new BarebonesATAChannel((short) 0x170, (short) 0x376);

        BarebonesATAChannel primaryIde = new BarebonesATAChannel((short) 0x1F0, (short) 0x3F6);
        primaryIde.addDevice(new DummyIdeDrive(vm), false);

        return primaryIde;
    }
}
