package thennx.mcx86;

import net.minecraft.world.item.ItemStack;
import thennx.mcx86.computer.ComputerBlockEntity;
import thennx.mcx86.device.RedstoneCardDevice;
import thennx.vm8086.IVirtualMachine;
import thennx.vm8086.devices.IDevice;
import thennx.vm8086.devices.IPortSpaceDevice;

import javax.annotation.Nullable;

public interface IDeviceFactory {
    class DeviceInstance {
        private final String name;
        private final IDevice device;
        public String providerSlot = null;

        public DeviceInstance(String name, IDevice device) {
            this.name = name;
            this.device = device;
        }

        public String getName() {
            return name;
        }

        public IDevice getDevice() {
            return device;
        }

        public static DeviceInstance ofIndexedName(IVirtualMachine vm, String name, IDevice device) {
            int index = 0;
            while (vm.getDevice(name + index) != null) {
                index++;
            }

            return new DeviceInstance(name + index, device);
        }
    }

    @Nullable
    DeviceInstance createDevice(ItemStack selfStack, ComputerBlockEntity blockEntity);
}
