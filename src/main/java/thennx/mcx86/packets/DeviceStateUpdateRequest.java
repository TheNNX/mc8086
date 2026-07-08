package thennx.mcx86.packets;

import java.util.Objects;
import java.util.function.Supplier;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkEvent.Context;
import thennx.mcx86.screen.ScreenBlockEntity;

public class DeviceStateUpdateRequest {
    private BlockPos blockEntityPos;
    private ResourceKey<Level> dimension;

    public DeviceStateUpdateRequest(BlockEntity blockEntity) {
        this.blockEntityPos = blockEntity.getBlockPos();
        this.dimension = blockEntity.getLevel().dimension();
    }

    public DeviceStateUpdateRequest(final FriendlyByteBuf packetBuffer) {
        this.blockEntityPos = packetBuffer.readBlockPos();
        ResourceLocation registryLocation = packetBuffer.readResourceLocation();
        ResourceLocation dimensionLocation = packetBuffer.readResourceLocation();
        this.dimension = ResourceKey.create(ResourceKey.createRegistryKey(registryLocation), dimensionLocation);
    }

    public void encode(final FriendlyByteBuf packetBuffer) {
        packetBuffer.writeBlockPos(blockEntityPos);
        packetBuffer.writeResourceLocation(dimension.registry());
        packetBuffer.writeResourceLocation(dimension.location());
    }

    public static void handle(DeviceStateUpdateRequest msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        if (context.getDirection().getReceptionSide() == LogicalSide.SERVER) {
            ctx.get().enqueueWork(() -> serverHandlePacket(msg, ctx));
        }
        ctx.get().setPacketHandled(true);
    }

    private static void serverHandlePacket(DeviceStateUpdateRequest msg, Supplier<Context> ctx) {
        Level level = ctx.get().getSender().serverLevel();
        if (!level.dimension().equals(msg.dimension))
            return;

        if (level.hasChunkAt(msg.blockEntityPos)) {
            BlockEntity blockEntity = level.getBlockEntity(msg.blockEntityPos);
            if (blockEntity instanceof ScreenBlockEntity screenBlockEntity) {
                screenBlockEntity.updateVgaText();
            }
        }
    }
}
