package thennx.mcx86;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class WireBlockEntity extends BlockEntity {
    protected boolean rendererDirty = false;

    public WireBlockEntity(BlockPos p_155229_, BlockState p_155230_) {
        super(MCx86Mod.WIRE_BLOCK_ENTITY.get(), p_155229_, p_155230_);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, WireBlockEntity blockEntity) {

    }

    protected void setRendererDirty(boolean rendererDirty) {
        this.rendererDirty = rendererDirty;
    }

    protected boolean isRendererDirty() {
        return rendererDirty;
    }
}
