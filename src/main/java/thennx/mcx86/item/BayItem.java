package thennx.mcx86.item;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import thennx.mcx86.MCx86Mod;

public class BayItem extends Item {

    public final ResourceLocation resourceLocation;

    public BayItem(ResourceLocation resourceLocation, Properties p_41383_) {
        super(p_41383_);

        this.resourceLocation = resourceLocation;
    }

    public ResourceLocation getResourceLocation() {
        return resourceLocation;
    }
}
