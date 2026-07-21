package thennx.mcx86.congraph;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public abstract class AbstratcNodeBlockEntity extends BlockEntity implements INodeOwner {
    public AbstratcNodeBlockEntity(BlockEntityType<?> p_155228_, BlockPos p_155229_, BlockState p_155230_) {
        super(p_155228_, p_155229_, p_155230_);
    }

    @Override
    public void onLoad() {
        assert level != null;
        getNode().detectNeighbours(level, getBlockPos());
        super.onLoad();
    }
}
