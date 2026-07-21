package thennx.mcx86.packets;

import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.network.NetworkEvent;
import thennx.mcx86.MCx86Mod;
import thennx.mcx86.computer.ComputerBlockEntity;
import thennx.mcx86.screen.ScreenBlockEntity;
import thennx.vm8086.devices.IPS2Keyboard;

public class KeypressPacket {
	private final BlockPos blockEntityPos;
	private final ResourceKey<Level> dimension;
	private char character;
	private final int key;
	private final boolean pressed;

	public static final float KEYPRESS_VALID_DISTSQUARE = 6.0f;

	public KeypressPacket(ScreenBlockEntity blockEntity, char character, int key, boolean pressed) {
		this.blockEntityPos = blockEntity.getBlockPos();
		this.dimension = blockEntity.getLevel().dimension();
		this.character = character;
		this.key = key;
		this.pressed = pressed;
	}

	KeypressPacket(final FriendlyByteBuf packetBuffer) {
		this.blockEntityPos = packetBuffer.readBlockPos();
		ResourceLocation registryLocation = packetBuffer.readResourceLocation();
		ResourceLocation dimensionLocation = packetBuffer.readResourceLocation();
		this.dimension = ResourceKey.create(ResourceKey.createRegistryKey(registryLocation), dimensionLocation);
		this.character = packetBuffer.readChar();
		this.key = packetBuffer.readInt();
		this.pressed = packetBuffer.readBoolean();
	}

	void encode(final FriendlyByteBuf packetBuffer) {
		packetBuffer.writeBlockPos(blockEntityPos);
		packetBuffer.writeResourceLocation(dimension.registry());
		packetBuffer.writeResourceLocation(dimension.location());
		packetBuffer.writeChar(this.character);
		packetBuffer.writeInt(this.key);
		packetBuffer.writeBoolean(this.pressed);
	}

	public static void handle(KeypressPacket msg, Supplier<NetworkEvent.Context> ctx) {
		NetworkEvent.Context context = ctx.get();
		if (context.getDirection().getReceptionSide() == LogicalSide.SERVER) {
			ctx.get().enqueueWork(() -> {
				ServerPlayer player = context.getSender();
				Level level = player.level();

				if (!level.hasChunkAt(msg.blockEntityPos)
						|| msg.blockEntityPos.distSqr(player.blockPosition()) > KEYPRESS_VALID_DISTSQUARE) {
                    MCx86Mod.LOGGER.warn("Player {} tried to access a block entity outside their range. ", player.getName().getString());
					return;
				}

				BlockEntity blockEntity = level.getBlockEntity(msg.blockEntityPos);
				if (!(blockEntity instanceof ScreenBlockEntity screenEntity))
					return;

				ComputerBlockEntity computerEntity = screenEntity.getComputerEntity();
				if (computerEntity == null)
					return;

				IPS2Keyboard keyboard = computerEntity.getKeyboard();

				if (msg.key == 28)
					keyboard.queueKeystroke(msg.key, '\n', msg.pressed);
				else
					keyboard.queueKeystroke(msg.key, msg.character, msg.pressed);
			});
		}
		ctx.get().setPacketHandled(true);
	}
}
