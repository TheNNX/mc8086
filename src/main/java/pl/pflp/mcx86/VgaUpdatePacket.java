package pl.pflp.mcx86;

import java.util.Objects;
import java.util.function.Supplier;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkEvent.Context;
import pl.pflp.vm8086.VM8086;

public class VgaUpdatePacket {
	private BlockPos blockEntityPos;
	private ResourceKey<Level> dimension;
	private byte[] data;

	public VgaUpdatePacket(DebugComputerBlockEntity blockEntity) {
		this.blockEntityPos = blockEntity.getBlockPos();
		this.dimension = blockEntity.getLevel().dimension();
		this.data = new byte[80 * 25 * 2];

		for (int i = 0; i < data.length; i++) {
			data[i] = blockEntity.getVM().readMemoryBytePhysical(0xB8000 + i);
		}
	}

	VgaUpdatePacket(final FriendlyByteBuf packetBuffer) {
		this.blockEntityPos = packetBuffer.readBlockPos();
		ResourceLocation registryLocation = packetBuffer.readResourceLocation();
		ResourceLocation dimensionLocation = packetBuffer.readResourceLocation();
		this.dimension = ResourceKey.create(ResourceKey.createRegistryKey(registryLocation), dimensionLocation);
		this.data = packetBuffer.readByteArray();
	}

	void encode(final FriendlyByteBuf packetBuffer) {
		packetBuffer.writeBlockPos(blockEntityPos);
		packetBuffer.writeResourceLocation(dimension.registry());
		packetBuffer.writeResourceLocation(dimension.location());
		packetBuffer.writeByteArray(data);
	}

	public static void handle(VgaUpdatePacket msg, Supplier<NetworkEvent.Context> ctx) {
		NetworkEvent.Context context = ctx.get();
		if (context.getDirection().getReceptionSide() == LogicalSide.CLIENT) {
			ctx.get().enqueueWork(
					() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> clientHandlePacket(msg, ctx)));
		}
		ctx.get().setPacketHandled(true);
	}

	private static void clientHandlePacket(VgaUpdatePacket msg, Supplier<Context> ctx) {

		NetworkEvent.Context context = ctx.get();

		Level level = Minecraft.getInstance().level;
		if (level.dimension().equals(msg.dimension) == false)
			return;

		if (level.hasChunkAt(msg.blockEntityPos)) {
			BlockEntity blockEntity = Objects.requireNonNull(level.getBlockEntity(msg.blockEntityPos));
			if (blockEntity instanceof DebugComputerBlockEntity) {
				DebugComputerBlockEntity dbe = (DebugComputerBlockEntity) blockEntity;
				VM8086 vm = dbe.getVM();
				for (int i = 0; i < msg.data.length; i++) {
					vm.writeMemoryBytePhysical(0xB8000 + i, msg.data[i]);
				}
			}
		}
	}
}
