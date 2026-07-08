package thennx.mcx86.computer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import thennx.mcx86.MCx86Mod;
import thennx.mcx86.congraph.AbstractNodeBlock;
import thennx.mcx86.pool.PoolManager;
import thennx.vm8086.IVirtualMachine;

import javax.annotation.Nullable;
import java.io.IOException;

public class ComputerBlock extends AbstractNodeBlock {
    public static final DirectionProperty DIRECTION_PROPERTY = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty CASE_OFF = BooleanProperty.create("case_off");
    public static final IntegerProperty POWER_STATE = IntegerProperty.create("power_state", 0, 2);

    public ComputerBlock(Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition().any().setValue(CASE_OFF, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(DIRECTION_PROPERTY, CASE_OFF, POWER_STATE);
    }

    @Override
    public boolean propagatesSkylightDown(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos) {
        if (blockState.getValue(CASE_OFF)) {
            return super.propagatesSkylightDown(blockState, blockGetter, blockPos);
        }
        return false;
    }

    @Override
    public int getLightBlock(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos) {
        if (blockState.getValue(CASE_OFF))
            return super.getLightBlock(blockState, blockGetter, blockPos);
        return blockGetter.getMaxLightLevel();
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext p_58071_) {
        BlockState blockstate = this.defaultBlockState();
        Direction[] adirection = p_58071_.getNearestLookingDirections();

        for (Direction direction : adirection) {
            if (direction.getAxis().isHorizontal()) {
                Direction direction1 = direction.getOpposite();
                blockstate = blockstate.setValue(DIRECTION_PROPERTY, direction1);
                return blockstate;
            }
        }

        return null;
    }

    @Override
    public ComputerBlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ComputerBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        return createTickerHelper(type, MCx86Mod.COMPUTER_BLOCK_ENTITY.get(), ComputerBlockEntity::tick);
    }

    @Override
    public InteractionResult use(BlockState blockState, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult p_56283_) {
        BlockEntity blockEntity = level.getBlockEntity(pos);

        if (!level.isClientSide() && hand == InteractionHand.MAIN_HAND && player.getItemInHand(hand).is(MCx86Mod.SCREWDRIVER.get())) {
            level.setBlock(pos, blockState.setValue(CASE_OFF, !blockState.getValue(CASE_OFF)), 3);
        }
        else if (blockState.getValue(CASE_OFF)) {
            if (blockEntity instanceof ComputerBlockEntity computerBlockEntity) {
                ItemStack stack = player.getItemInHand(hand);

                if (stack.is(MCx86Mod.MOTHERBOARD_8086.get())) {
                    ItemStack newStack = stack.split(1);
                    ItemStack oldMotherboard = computerBlockEntity.replaceMotherboard(newStack);

                    if (!oldMotherboard.isEmpty() && !level.isClientSide()) {
                        Block.popResource(level, pos, oldMotherboard);
                    }

                    return InteractionResult.CONSUME;
                }
            }

            return InteractionResult.SUCCESS;
        }
        else if (!level.isClientSide() && hand == InteractionHand.MAIN_HAND) {
            if (blockEntity instanceof ComputerBlockEntity computerBlockEntity) {
                IVirtualMachine vm = computerBlockEntity.getVM();

                if (vm == null)
                    return InteractionResult.SUCCESS;

                if (!vm.isRunning() && computerBlockEntity.canRun()) {
                    vm.setRunning(true);
                    PoolManager.getInstance().registerBlockEntity(computerBlockEntity);

                    level.setBlock(pos, blockState.setValue(POWER_STATE, 1), 3);
                    level.setBlockEntity(computerBlockEntity);
                }
                else {
                    vm.setRunning(false);
                    PoolManager.getInstance().unregisterBlockEntity(computerBlockEntity);

                    level.setBlock(pos, blockState.setValue(POWER_STATE, 0), 3);
                    level.setBlockEntity(computerBlockEntity);
                }

                blockEntity.setChanged();
                computerBlockEntity.setScreenDirty(true);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void onRemove(BlockState p_60515_, Level level, BlockPos pos, BlockState p_60518_, boolean p_60519_) {
        if (!p_60518_.is(MCx86Mod.COMPUTER_BLOCK.get())) {
            if (!level.isClientSide) {
                BlockEntity blockEntity = level.getBlockEntity(pos);
                if (blockEntity instanceof ComputerBlockEntity computerBlockEntity) {
                    computerBlockEntity.destroyVm();
                }
            }

            super.onRemove(p_60515_, level, pos, p_60518_, p_60519_);
        }
    }
}
