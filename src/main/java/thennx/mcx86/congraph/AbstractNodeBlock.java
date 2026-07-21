package thennx.mcx86.congraph;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import thennx.mcx86.MCx86Mod;

public abstract class AbstractNodeBlock extends BaseEntityBlock {
    protected AbstractNodeBlock(Properties p_49224_) {
        super(p_49224_);
    }

    @Override
    public abstract @Nullable AbstratcNodeBlockEntity newBlockEntity(BlockPos p_153215_, BlockState p_153216_);

    @Override
    public void onPlace(BlockState blockState, Level level, BlockPos pos, BlockState prevState, boolean p_60570_) {
        if (!level.isClientSide) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof INodeOwner nodeOwner) {
                nodeOwner.getNode().detectNeighbours(level, pos);
            }
        }
        super.onPlace(blockState, level, pos, prevState, p_60570_);
    }

    @Override
    public void onRemove(BlockState p_60515_, Level level, BlockPos pos, BlockState p_60518_, boolean p_60519_) {
        if (!p_60518_.is(MCx86Mod.COMPUTER_BLOCK.get())) {
            if (!level.isClientSide) {
                BlockEntity blockEntity = level.getBlockEntity(pos);

                if (blockEntity instanceof INodeOwner nodeOwner) {
                    nodeOwner.getNode().removeNeighbours();
                }
            }

            super.onRemove(p_60515_, level, pos, p_60518_, p_60519_);
        }
    }
}
