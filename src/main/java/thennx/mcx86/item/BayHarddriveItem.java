package thennx.mcx86.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import thennx.mcx86.MCx86Mod;
import thennx.mcx86.computer.ComputerBlockEntity;
import thennx.vm8086.devices.DummyIdeDrive;

import java.nio.file.Path;
import java.util.UUID;

public class BayHarddriveItem extends BayItem {
    private final int cylinders;
    private final int heads;
    private final int sectors;
    private final int bytesPerSector;

    public BayHarddriveItem(ResourceLocation resourceLocation, Properties properties, int cylinders, int heads, int sectors, int bytesPerSector) {
        super(resourceLocation, properties);
        this.cylinders = cylinders;
        this.heads = heads;
        this.sectors = sectors;
        this.bytesPerSector = bytesPerSector;
    }

    private void allocateTag(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString("uuid", String.valueOf(UUID.randomUUID()));
    }

    private DummyIdeDrive.IPathProvider getDrivePathProviderForStack(ComputerBlockEntity blockEntity, ItemStack stack) {
        if (!stack.hasTag()) {
            allocateTag(stack);
        }

        String uuid = stack.getTag().getString("uuid");
        MCx86Mod.LOGGER.info("Instantiating block device with UUID" + uuid);

        class PathProviderImpl implements DummyIdeDrive.IPathProvider {
            private final String uuid;
            private final ComputerBlockEntity blockEntity;

            public PathProviderImpl(ComputerBlockEntity blockEntity, String uuid) {
                this.uuid = uuid;
                this.blockEntity = blockEntity;
            }

            @Override
            public Path getPath() {
                return blockEntity.getComputerSaveLocation().resolve(uuid + ".dat");
            }
        }

        return new PathProviderImpl(blockEntity, uuid);
    }

    private boolean getIsStackReadonly(ItemStack stack) {
        return !stack.getTag().contains("readonly") && stack.getTag().getBoolean("readonly");
    }

    private DummyIdeDrive createDrive(ComputerBlockEntity blockEntity, ItemStack stack) {
        return new DummyIdeDrive(
                blockEntity.getVM(), getDrivePathProviderForStack(blockEntity, stack), getIsStackReadonly(stack),
                cylinders, heads, sectors, bytesPerSector);
    }

    @Override
    public @Nullable DeviceInstance createDevice(ItemStack stack, ComputerBlockEntity blockEntity) {
        return DeviceInstance.ofIndexedName(blockEntity.getVM(), "bayHardDrive", createDrive(blockEntity, stack));
    }
}
