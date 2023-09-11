package pl.pflp.mcx86;

import java.util.Map;

import javax.annotation.Nullable;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.SignEditScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SignApplicator;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import pl.pflp.mcx86.gui.ComputerGuiScreen;

public class DebugComputer extends SignBlock {

	public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

	public VoxelShape getShape(BlockState p_58092_, BlockGetter p_58093_, BlockPos p_58094_,
			CollisionContext p_58095_) {
		return Block.box(0, 0, 0, 16.0f, 16.0f, 16.0f);
	}

	public boolean canSurvive(BlockState p_58073_, LevelReader p_58074_, BlockPos p_58075_) {
		return true;
	}

	@Nullable
	public BlockState getStateForPlacement(BlockPlaceContext p_58071_) {
		BlockState blockstate = this.defaultBlockState();
		FluidState fluidstate = p_58071_.getLevel().getFluidState(p_58071_.getClickedPos());
		LevelReader levelreader = p_58071_.getLevel();
		BlockPos blockpos = p_58071_.getClickedPos();
		Direction[] adirection = p_58071_.getNearestLookingDirections();

		for (Direction direction : adirection) {
			if (direction.getAxis().isHorizontal()) {
				Direction direction1 = direction.getOpposite();
				blockstate = blockstate.setValue(FACING, direction1);
				if (blockstate.canSurvive(levelreader, blockpos)) {
					return blockstate.setValue(WATERLOGGED, Boolean.valueOf(fluidstate.getType() == Fluids.WATER));
				}
			}
		}

		return null;
	}

	public BlockState updateShape(BlockState p_58083_, Direction p_58084_, BlockState p_58085_, LevelAccessor p_58086_,
			BlockPos p_58087_, BlockPos p_58088_) {
		return p_58084_.getOpposite() == p_58083_.getValue(FACING) && !p_58083_.canSurvive(p_58086_, p_58087_)
				? Blocks.AIR.defaultBlockState()
				: super.updateShape(p_58083_, p_58084_, p_58085_, p_58086_, p_58087_, p_58088_);
	}

	public float getYRotationDegrees(BlockState p_278024_) {
		return p_278024_.getValue(FACING).toYRot();
	}

	public Vec3 getSignHitboxCenterPosition(BlockState p_278316_) {
		VoxelShape voxelshape = Block.box(0, 0, 0, 1, 1, 1);
		return voxelshape.bounds().getCenter();
	}

	public BlockState rotate(BlockState p_58080_, Rotation p_58081_) {
		return p_58080_.setValue(FACING, p_58081_.rotate(p_58080_.getValue(FACING)));
	}

	public BlockState mirror(BlockState p_58077_, Mirror p_58078_) {
		return p_58077_.rotate(p_58078_.getRotation(p_58077_.getValue(FACING)));
	}

	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> p_58090_) {
		p_58090_.add(FACING, WATERLOGGED);
	}

	protected DebugComputer(BlockBehaviour.Properties p_56273_) {
		super(p_56273_, WoodType.OAK);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos p_154556_, BlockState p_154557_) {
		return new DebugComputerBlockEntity(p_154556_, p_154557_);
	}

	@Override
	public String getDescriptionId() {
		return "TODO";
	}

	public RenderShape getRenderShape(BlockState p_49232_) {
		return RenderShape.MODEL;
	}

	@Override
	public InteractionResult use(BlockState p_56278_, Level p_56279_, BlockPos p_56280_, Player p_56281_,
			InteractionHand p_56282_, BlockHitResult p_56283_) {
		BlockEntity blockEntity = p_56279_.getBlockEntity(p_56280_);
		if (blockEntity instanceof DebugComputerBlockEntity debugComputer) {
			if (p_56279_.isClientSide) {
				Minecraft.getInstance().setScreen(new ComputerGuiScreen(debugComputer));
				return InteractionResult.SUCCESS;
			} else {
				return InteractionResult.SUCCESS;
			}
		}
		return InteractionResult.PASS;
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
			BlockEntityType<T> type) {
		return createTickerHelper(type, MCx86Mod.DEBUG_COMPUTER_BLOCK_ENTITY.get(), DebugComputerBlockEntity::tick);
	}
}