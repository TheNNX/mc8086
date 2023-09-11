package pl.pflp.mcx86;

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
import pl.pflp.vm8086.IPS2Keyboard;

public class KeypressPacket {
	private BlockPos blockEntityPos;
	private ResourceKey<Level> dimension;
	private char character;
	private int key;
	private boolean pressed;

	public KeypressPacket(DebugComputerBlockEntity blockEntity, char character, int key, boolean pressed) {
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
		packetBuffer.writeChar((int) this.character);
		packetBuffer.writeInt(this.key);
		packetBuffer.writeBoolean(this.pressed);
	}

	public static void handle(KeypressPacket msg, Supplier<NetworkEvent.Context> ctx) {
		NetworkEvent.Context context = ctx.get();
		if (context.getDirection().getReceptionSide() == LogicalSide.SERVER) {
			ctx.get().enqueueWork(() -> {
				ServerPlayer player = context.getSender();
				Level level = player.level();
				if (level.hasChunkAt(msg.blockEntityPos) == false
						|| msg.blockEntityPos.distSqr(player.blockPosition()) > 5) {
					MCx86Mod.LOGGER.warn("Player " + player.getName().getString()
							+ " tried to access a block entity outside their range. ");
					return;
				}

				BlockEntity blockEntity = level.getBlockEntity(msg.blockEntityPos);
				if (blockEntity == null || !(blockEntity instanceof DebugComputerBlockEntity))
					return;

				DebugComputerBlockEntity dbe = (DebugComputerBlockEntity) blockEntity;
				IPS2Keyboard keyboard = dbe.getKeyboard();
				keyboard.keyPressed(msg.key | (msg.pressed ? 0 : 0x80));
			});
		}
		ctx.get().setPacketHandled(true);
	}
}
