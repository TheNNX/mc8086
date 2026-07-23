package thennx.mcx86.item;

import net.minecraft.world.item.ItemStack;
import thennx.mcx86.computer.ComputerBlockEntity;
import thennx.mcx86.device.RedstoneCardDevice;
import thennx.vm8086.devices.IPortSpaceDevice;

import javax.annotation.Nullable;

public class RedstoneCardItem extends CardItem {
    public RedstoneCardItem() {
        super(false);
    }

    @Override
    public @Nullable DeviceInstance createDevice(ItemStack stack, ComputerBlockEntity blockEntity) {
        return DeviceInstance.ofIndexedName(blockEntity.getVM(), "redstoneCard", new RedstoneCardDevice(blockEntity, (short) 0xE0));
    }
}
