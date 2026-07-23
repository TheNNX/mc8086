package thennx.mcx86.item;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import thennx.mcx86.MCx86Mod;
import thennx.mcx86.computer.ComputerBlockEntity;
import thennx.vm8086.devices.IPortSpaceDevice;

public class BayItem extends AbstractComponentItem {

    public final ResourceLocation resourceLocation;

    public BayItem(ResourceLocation resourceLocation, Properties p_41383_) {
        super(p_41383_);

        this.resourceLocation = resourceLocation;
    }

    public ResourceLocation getResourceLocation() {
        return resourceLocation;
    }

    @Override
    public @Nullable DeviceInstance createDevice(ItemStack stack, ComputerBlockEntity blockEntity) {
        return null;
    }
}
