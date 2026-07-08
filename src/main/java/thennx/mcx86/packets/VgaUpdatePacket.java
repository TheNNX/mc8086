package thennx.mcx86.packets;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Supplier;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkEvent.Context;
import thennx.mcx86.computer.ComputerBlockEntity;
import thennx.mcx86.screen.ScreenBlockEntity;
import thennx.vm8086.IVirtualMachine;

public class VgaUpdatePacket {
	private BlockPos blockEntityPos;
	private ResourceKey<Level> dimension;
	private byte[] data;
	private final ComputerBlockEntity computerBlockEntity;

	public VgaUpdatePacket(ScreenBlockEntity blockEntity) {
		this.blockEntityPos = blockEntity.getBlockPos();
		this.dimension = blockEntity.getLevel().dimension();
		this.data = new byte[80 * 25 * 2];
		this.computerBlockEntity = blockEntity.getComputerEntity();

		IVirtualMachine vm = computerBlockEntity != null ? computerBlockEntity.getVM() : null;

		if (vm != null && vm.isRunning()) {
			for (int i = 0; i < data.length; i++) {
				data[i] = vm.readMemoryBytePhysical(0xB8000 + i);
			}
		}
		else {
            Arrays.fill(data, (byte) 0);
		}
	}

	public VgaUpdatePacket(final FriendlyByteBuf packetBuffer) {
		this.blockEntityPos = packetBuffer.readBlockPos();
		ResourceLocation registryLocation = packetBuffer.readResourceLocation();
		ResourceLocation dimensionLocation = packetBuffer.readResourceLocation();
		this.dimension = ResourceKey.create(ResourceKey.createRegistryKey(registryLocation), dimensionLocation);
		this.data = packetBuffer.readByteArray();
		this.computerBlockEntity = null;
	}

	public void encode(final FriendlyByteBuf packetBuffer) {
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
		if (!level.dimension().equals(msg.dimension))
			return;

		if (level.hasChunkAt(msg.blockEntityPos)) {
			BlockEntity blockEntity = Objects.requireNonNull(level.getBlockEntity(msg.blockEntityPos));
			if (blockEntity instanceof ScreenBlockEntity dbe) {
				dbe.setDisplayData(msg.data);
			}
		}
	}
}
