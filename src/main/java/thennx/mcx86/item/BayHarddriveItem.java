package thennx.mcx86.item;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import thennx.mcx86.computer.ComputerBlockEntity;
import thennx.vm8086.devices.DummyIdeDrive;

public class BayHarddriveItem extends BayItem {
    private final BlockDeviceItemHelper helper;

    public BayHarddriveItem(ResourceLocation resourceLocation, Properties properties, int cylinders, int heads, int sectors, int bytesPerSector) {
        super(resourceLocation, properties);
        this.helper = new BlockDeviceItemHelper(cylinders, heads, sectors, bytesPerSector);
    }

    private DummyIdeDrive createDrive(ComputerBlockEntity blockEntity, ItemStack stack) {
        return new DummyIdeDrive(
                blockEntity.getVM(), helper.getDrivePathProviderForStack(blockEntity, stack), helper.getIsStackReadonly(stack),
                helper.getCylinders(), helper.getHeads(), helper.getSectors(), helper.getBytesPerSector());
    }

    @Override
    public @Nullable DeviceInstance createDevice(ItemStack stack, ComputerBlockEntity blockEntity) {
        return DeviceInstance.ofIndexedName(blockEntity.getVM(), "bayHardDrive", createDrive(blockEntity, stack));
    }
}
