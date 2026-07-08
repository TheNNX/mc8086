package thennx.mcx86.screen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import thennx.mcx86.MCx86Mod;
import thennx.mcx86.packets.DeviceStateUpdateRequest;
import thennx.mcx86.packets.MCx86PacketHandler;
import thennx.mcx86.computer.ComputerBlockEntity;
import thennx.mcx86.congraph.AbstratcNodeBlockEntity;
import thennx.mcx86.congraph.INodeOwner;
import thennx.mcx86.congraph.Node;
import thennx.mcx86.packets.VgaUpdatePacket;
import thennx.vm8086.VM8086;

public class ScreenBlockEntity extends AbstratcNodeBlockEntity {
    private byte[] displayData = null;
    private ComputerBlockEntity connectedComputerBlockEntity = null;
    private Node<ScreenBlockEntity> node = new Node<>(this);

    public ScreenBlockEntity(BlockPos p_155229_, BlockState p_155230_) {
        super(MCx86Mod.SCREEN_BLOCK_ENTITY.get(), p_155229_, p_155230_);
    }

    public void updateVgaText() {
        MCx86PacketHandler.INSTANCE.send(
                PacketDistributor.TRACKING_CHUNK.with(() -> (LevelChunk) this.level.getChunk(this.getBlockPos())),
                new VgaUpdatePacket(this));
    }

    public int[] getDisplayPixelData(int width, int height, int[] colorPalette) {
        final int CHAR_HEIGHT = 16;
        final int CHAR_WIDTH = 9;

        if (displayData == null) {
            return null;
        }

        int[] data = new int[width * height];

        for (int y = 0; y < height / CHAR_HEIGHT; y++) {
            for (int x = 0; x < width / CHAR_WIDTH; x++) {
                int offset = 2 * (x + y * width / CHAR_WIDTH);
                char c = (char) (displayData[offset] & 0xFF);
                int colorIdx = (char) (displayData[1 + offset] & 0xFF);

                int colorForeground = colorPalette[colorIdx & 0xF];
                int colorBackground = colorPalette[(colorIdx & 0xF0) >> 4];

                for (int yy = 0; yy < CHAR_HEIGHT; yy++) {
                    for (int xx = 0; xx < CHAR_WIDTH; xx++) {
                        int vgaRomOffset = c * CHAR_HEIGHT + yy;
                        int vgaData = VM8086.VGA_ROM_F16[vgaRomOffset];

                        int index = xx + x * CHAR_WIDTH + (yy + y * CHAR_HEIGHT) * width;

                        if ((vgaData & (1 << (8 - xx - 1))) != 0)
                            data[index] = colorForeground;
                        else
                            data[index] = colorBackground;
                    }
                }
            }
        }

        return data;
    }

    public void setDisplayData(byte[] data) {
        this.displayData = data;
    }

    public ComputerBlockEntity getComputerEntity() {
        return this.connectedComputerBlockEntity;
    }

    @Override
    public Node<ScreenBlockEntity> getNode() {
        return node;
    }

    @Override
    public void notifyNeighbourChange(Direction direction, @Nullable INodeOwner newNeighbour, @Nullable INodeOwner prevNeighbour) {
        if (newNeighbour instanceof ComputerBlockEntity computerBlockEntity) {
            this.connectedComputerBlockEntity = computerBlockEntity;
        }
        else if (prevNeighbour instanceof ComputerBlockEntity computerBlockEntity && connectedComputerBlockEntity == computerBlockEntity) {
            this.connectedComputerBlockEntity = null;
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && level.isClientSide()) {
            MCx86PacketHandler.INSTANCE.sendToServer(new DeviceStateUpdateRequest(this));
        }
        else if (level != null && !level.isClientSide()) {
            updateVgaText();
        }
    }
}
