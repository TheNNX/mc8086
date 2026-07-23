package thennx.mcx86.item;

import net.minecraft.world.item.ItemStack;
import thennx.mcx86.computer.ComputerBlockEntity;
import thennx.vm8086.IVirtualMachine;
import thennx.vm8086.devices.ATAChannel;
import thennx.vm8086.devices.DummyIdeDrive;
import thennx.vm8086.devices.IDevice;
import thennx.vm8086.devices.IPortSpaceDevice;

import javax.annotation.Nullable;
import java.util.List;

public class DiskControllerCardItem extends CardItem {
    public DiskControllerCardItem() {
        super(true);
    }

    @Override
    public @Nullable DeviceInstance createDevice(ItemStack stack, ComputerBlockEntity blockEntity) {
        IVirtualMachine vm = blockEntity.getVM();
        List<IDevice> deviceList = vm.getDevices();

        int numATAChannels = 0;
        for (IDevice device : deviceList) {
            if (device instanceof ATAChannel) {
                numATAChannels++;
            }
        }

        ATAChannel channel;

        if (numATAChannels > 2)
            return null;
        if (numATAChannels == 1)
            channel = new ATAChannel((short) 0x170, (short) 0x376);
        else
            channel = new ATAChannel((short) 0x1F0, (short) 0x3F6);

        return new DeviceInstance("ataChannel" + numATAChannels, channel);
    }
}
