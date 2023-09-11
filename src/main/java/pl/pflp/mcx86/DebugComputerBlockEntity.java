package pl.pflp.mcx86;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.command.TextComponentHelper;
import pl.pflp.vm8086.DummyIdeDrive;
import pl.pflp.vm8086.IBlockDevice;
import pl.pflp.vm8086.IPS2Keyboard;
import pl.pflp.vm8086.PS2Keyboard;
import pl.pflp.vm8086.Registers8086.Register16;
import pl.pflp.vm8086.VM8086;

public class DebugComputerBlockEntity extends SignBlockEntity {

	private VM8086 vm8086;
	private PS2Keyboard keyboard;
	private IBlockDevice bootDrive;
	public boolean isScreenDirty = false;

	public DebugComputerBlockEntity(BlockPos pos, BlockState state) {
		super(MCx86Mod.DEBUG_COMPUTER_BLOCK_ENTITY.get(), pos, state);

		keyboard = new PS2Keyboard();

		byte[] bios = new byte[65536];

		for (int i = 0; i < bios.length; i++) {
			bios[i] = (byte) 0xf4;
		}

		int[] end = { 0xea, 0x00, 0x00, 0x00, 0xf0, 0xf4, 0xf4, 0xf4, 0xf4, 0xf4, 0xf4, 0xf4, 0xf4, 0xf4, 0xf4, 0xf4 };

		int[] defaultBios = VM8086.getDefaultBios();

		for (int i = 0; i < defaultBios.length; i++)
			bios[i] = (byte) defaultBios[i];

		for (int i = 0; i < end.length; i++)
			bios[i + bios.length - end.length] = (byte) end[i];

		this.bootDrive = new DummyIdeDrive();

		vm8086 = new VM8086(1024 * 1024, bios);
		vm8086.attachPS2Keyboard(keyboard);
		vm8086.attachIdeDevice(0, false, bootDrive);
	}

	int ticks = 0;

	private String[] vgaText = null;

	public String[] getVgaText() {
		if (vgaText == null)
			updateVgaText();
		return vgaText;
	}

	public String[] updateVgaText() {
		int videoMemStart = 0xB8000;
		String[] result = new String[25];

		for (int j = 0; j < 25; j++) {
			StringBuilder builder = new StringBuilder();
			for (int i = 0; i < 80; i++) {
				char c = (char) (vm8086.readMemoryBytePhysical(videoMemStart + 2 * (i + j * 80)) & 0xFF);
				if (c < ' ')
					builder.append(" ");
				else
					builder.append(c);
			}
			result[j] = builder.toString();
		}

		vgaText = result;

		MCx86PacketHandler.INSTANCE.send(
				PacketDistributor.TRACKING_CHUNK.with(() -> (LevelChunk) this.level.getChunk(this.getBlockPos())),
				new VgaUpdatePacket(this));
		return result;
	}

	public static void tick(Level level, BlockPos pos, BlockState state, DebugComputerBlockEntity blockEntity) {

		if (level.isClientSide == false) {
			if (blockEntity.ticks++ % 5 == 0) {

				blockEntity.updateVgaText();
				blockEntity.isScreenDirty = true;

				// blockEntity.getBackText().setMessage(0, Component.literal(str));
				// blockEntity.getFrontText().setMessage(0, Component.literal(str));
				blockEntity.ticks = 0;
				blockEntity.setChanged();
			}
			blockEntity.vm8086.step(1000);
		}
		// SignBlockEntity.tick(level, pos, state, blockEntity);
	}

	public VM8086 getVM() {
		return this.vm8086;
	}

	@Override
	public int getMaxTextLineWidth() {
		return 80;
	}

	public IPS2Keyboard getKeyboard() {
		return this.keyboard;
	}

	@Override
	protected void saveAdditional(CompoundTag tag) {
		CompoundTag subtag = new CompoundTag();

		for (Register16 register : this.vm8086.registers) {
			subtag.putShort(register.getName(), register.shortValue());
		}

		tag.put("registers", subtag);

		if (1 == 1)
			return;
		tag.putByteArray("memory", this.vm8086.memory);

	}

	@Override
	public void load(CompoundTag nbt) {
		CompoundTag registers = nbt.getCompound("registers");
		for (Register16 register : this.vm8086.registers) {
			if (!registers.contains(register.getName(), Tag.TAG_SHORT)) {
				this.vm8086.isRunning = false;
				return;
			}
			register.write(registers.getShort(register.getName()));
		}

		if (1 == 1)
			return;
		this.vm8086.memory = nbt.getByteArray("memory");
	}
}
