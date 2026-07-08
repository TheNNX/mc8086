package thennx.mcx86.screen;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.client.extensions.common.IClientBlockExtensions;
import thennx.mcx86.congraph.AbstractNodeBlock;
import thennx.mcx86.gui.ComputerGuiScreen;
import thennx.mcx86.packets.DeviceStateUpdateRequest;
import thennx.mcx86.packets.KeypressPacket;
import thennx.mcx86.packets.MCx86PacketHandler;

import java.util.function.Consumer;

public class Screen extends AbstractNodeBlock {
	public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

	public Screen(BlockBehaviour.Properties p_56273_) {
		super(p_56273_);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	@Nullable
	public BlockState getStateForPlacement(BlockPlaceContext ctx) {
		Direction[] directions = ctx.getNearestLookingDirections();

		for (Direction direction : directions) {
			if (direction.getAxis().isHorizontal()) {
				return this.defaultBlockState().setValue(FACING, direction.getOpposite());
			}
		}

		return null;
	}

	public float getYRotationDegrees(BlockState p_278024_) {
		return p_278024_.getValue(FACING).toYRot();
	}

	@Override
	public ScreenBlockEntity newBlockEntity(BlockPos p_154556_, BlockState p_154557_) {
		return new ScreenBlockEntity(p_154556_, p_154557_);
	}

	@Override
	public InteractionResult use(BlockState p_60503_, Level level, BlockPos blockPos, Player p_60506_, InteractionHand p_60507_, BlockHitResult p_60508_) {
		BlockEntity blockEntity = level.getBlockEntity(blockPos);
		if (blockEntity instanceof ScreenBlockEntity debugComputer) {
			if (level.isClientSide && blockPos.distToCenterSqr(p_60506_.position()) < KeypressPacket.KEYPRESS_VALID_DISTSQUARE) {
				Minecraft.getInstance().setScreen(new ComputerGuiScreen(debugComputer));
			}
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.PASS;
	}

	@Override
	public RenderShape getRenderShape(BlockState p_49232_) {
		return RenderShape.MODEL;
	}
}