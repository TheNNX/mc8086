package thennx.mcx86.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import thennx.mcx86.MCx86Mod;
import thennx.mcx86.computer.ComputerBlockEntity;
import thennx.vm8086.IPathProvider;

import java.nio.file.Path;
import java.util.UUID;

public class BlockDeviceItemHelper {
    private final int cylinders;
    private final int heads;
    private final int sectors;
    private final int bytesPerSector;

    public BlockDeviceItemHelper(int cylinders, int heads, int sectors, int bytesPerSector) {
        this.cylinders = cylinders;
        this.heads = heads;
        this.sectors = sectors;
        this.bytesPerSector = bytesPerSector;
    }

    private void allocateTag(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString("uuid", String.valueOf(UUID.randomUUID()));
    }

    public IPathProvider getDrivePathProviderForStack(ComputerBlockEntity blockEntity, ItemStack stack) {
        if (!stack.hasTag()) {
            allocateTag(stack);
        }

        String uuid = stack.getTag().getString("uuid");
        MCx86Mod.LOGGER.info("Instantiating block device with UUID" + uuid);

        class PathProviderImpl implements IPathProvider {
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

    public boolean getIsStackReadonly(ItemStack stack) {
        return !stack.getTag().contains("readonly") && stack.getTag().getBoolean("readonly");
    }

    public int getCylinders() {
        return cylinders;
    }

    public int getHeads() {
        return heads;
    }

    public int getSectors() {
        return sectors;
    }

    public int getBytesPerSector() {
        return bytesPerSector;
    }
}
