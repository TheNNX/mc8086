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
import java.util.Map;

public class DiskControllerCardItem extends CardItem {
    public DiskControllerCardItem() {
        super(true);
    }

    @Override
    public @Nullable DeviceInstance createDevice(ItemStack stack, ComputerBlockEntity blockEntity) {
        IVirtualMachine vm = blockEntity.getVM();
        Map<String, ATAChannel> existingChannels = vm.getDevices(ATAChannel.class);

        ATAChannel channel;

        if (existingChannels.size() > 2)
            return null;
        if (existingChannels.size() == 1)
            channel = new ATAChannel((short) 0x170, (short) 0x376);
        else
            channel = new ATAChannel((short) 0x1F0, (short) 0x3F6);

        return new DeviceInstance("ataChannel" + existingChannels.size(), channel);
    }
}
