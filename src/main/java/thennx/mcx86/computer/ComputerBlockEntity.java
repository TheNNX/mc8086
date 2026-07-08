package thennx.mcx86.computer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import thennx.mcx86.MCx86Mod;
import thennx.mcx86.NbtStateStorage;
import thennx.mcx86.pool.PoolManager;
import thennx.mcx86.screen.ScreenBlockEntity;
import thennx.mcx86.VideoMemoryBank;
import thennx.mcx86.congraph.AbstratcNodeBlockEntity;
import thennx.mcx86.congraph.INodeOwner;
import thennx.mcx86.congraph.Node;
import thennx.vm8086.FileMemoryStorage;
import thennx.vm8086.IVirtualMachine;
import thennx.vm8086.VM8086;
import thennx.vm8086.devices.*;

import javax.annotation.Nullable;

public class ComputerBlockEntity extends AbstratcNodeBlockEntity {

	private IVirtualMachine virtualMachine = null;
	private PS2Keyboard keyboard;
	private IBlockDevice bootDrive;
	private boolean devicesDiscovered = false;
	private boolean isScreenDirty = false;
	private Path savePath = null;
	private boolean delayedLoadQueued = false;
	private ScreenBlockEntity screenEntity = null;
	private Node<ComputerBlockEntity> node = new Node<>(this);
	private ItemStack motherboard = ItemStack.EMPTY;

	public ComputerBlockEntity(BlockPos pos, BlockState state) {
		super(MCx86Mod.COMPUTER_BLOCK_ENTITY.get(), pos, state);
	}

	public void createVm() throws IOException {
		if (virtualMachine != null) {
			PoolManager.getInstance().unregisterBlockEntity(this);
		}

		delayedLoadQueued = false;
		byte[] bios;

		ResourceLocation biosLocation = ResourceLocation.tryBuild(MCx86Mod.MODID, "bios/bios.bin");

		if (biosLocation == null) {
			throw new IOException("BIOS image couldn't be found");
		}

		try (InputStream biosStream = Minecraft.getInstance().getResourceManager().open(biosLocation)) {
			bios = biosStream.readAllBytes();
		}

		VM8086 vm8086 = new VM8086(1024 * 1024, bios);
		vm8086.setRunning(false);

		keyboard = new PS2Keyboard();
		vm8086.attachPS2Keyboard(keyboard);

		this.bootDrive = new DummyIdeDrive(vm8086, false);
		vm8086.attachIdeDevice(0, false, bootDrive);

		vm8086.memory[0xB8000 / IMemoryBank.BANK_SIZE] = new VideoMemoryBank(this);

		this.virtualMachine = vm8086;
	}

	private void updateVgaText() {
		if (screenEntity != null && level != null) {
			if (!level.isClientSide()) {
				screenEntity.updateVgaText();
			}
		}
	}

	private void delayedLoad() {
		delayedLoadQueued = false;

		try {
			prepareDirectories();
			synchronized (virtualMachine) {
				virtualMachine.load(new FileMemoryStorage(savePath));
			}
		} catch (IOException err) {
			err.printStackTrace(System.err);
		}

		PoolManager.getInstance().registerBlockEntity(this);
		updateVgaText();
	}

	public static void tick(Level level, BlockPos pos, BlockState state, ComputerBlockEntity blockEntity) {
		if (!blockEntity.devicesDiscovered) {
			blockEntity.getNode().detectNeighbours(level, pos);
			blockEntity.devicesDiscovered = true;
		}

		if (!level.isClientSide) {
			if (blockEntity.getVM() == null) {
                try {
                    blockEntity.createVm();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                return;
			}

			synchronized (blockEntity.virtualMachine) {
				if (blockEntity.delayedLoadQueued) {
					blockEntity.delayedLoad();
				}

				if (blockEntity.isScreenDirty()) {
					blockEntity.updateVgaText();
					blockEntity.setScreenDirty(false);
				}
			}
		}
	}

	public IVirtualMachine getVM() {
		return this.virtualMachine;
	}

	public IPS2Keyboard getKeyboard() {
		return this.keyboard;
	}

	private String getSaveId() {
		BlockPos pos = getBlockPos();
		return pos.getX() + " " + pos.getY() + " " + pos.getZ();
	}

	private void prepareDirectories() throws IOException {
		Path parentPath =  level.getServer().getWorldPath(LevelResource.ROOT).resolve("mcx86");

		if (Files.notExists(parentPath)) {
			Files.createDirectory(parentPath);
		}

		if (savePath == null) {
			savePath = parentPath.resolve(getSaveId());
		}
		if (Files.notExists(savePath)) {
			Files.createDirectory(savePath);
		}
	}

	private CompoundTag saveInventory() {
		CompoundTag tag = new CompoundTag();

		tag.put("motherboard", motherboard.save(new CompoundTag()));

		return tag;
	}

	private void loadInventory(CompoundTag tag) {
		motherboard = ItemStack.of(tag.getCompound("motherboard"));
	}

	@Override
	protected void saveAdditional(CompoundTag tag) {
		tag.put("inventory", saveInventory());

		if (level == null || level.isClientSide)
			return;

		/* Save registers */
        try {
			if (virtualMachine == null) {
				return;
			}

			synchronized (virtualMachine) {
				virtualMachine.save(new NbtStateStorage(tag));

				/* Save memory */
				if (!delayedLoadQueued) {
					prepareDirectories();
					virtualMachine.save(new FileMemoryStorage(savePath));
				}
			}
		} catch (IOException e) {
            throw new RuntimeException(e);
        }
	}

	@Override
	public void load(CompoundTag nbt) {
		loadInventory(nbt.getCompound("inventory"));

		if (level != null && level.isClientSide) {
			return;
		}

		try {
			if (virtualMachine == null) {
				createVm();
			}

			delayedLoadQueued = true;
			synchronized (virtualMachine) {
				virtualMachine.load(new NbtStateStorage(nbt));
			}
		}
		catch (IOException exception) {
			throw new RuntimeException(exception);
		}
	}

	@Override
	public void onChunkUnloaded() {
		PoolManager.getInstance().unregisterBlockEntity(this);
		super.onChunkUnloaded();
	}

	public void destroyVm() {
        PoolManager.getInstance().unregisterBlockEntity(this);

        try {
            this.virtualMachine.deleteSaved(new FileMemoryStorage(savePath));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        this.virtualMachine = null;
	}

	@Override
	public Node<ComputerBlockEntity> getNode() {
		return this.node;
	}

	@Override
	public void notifyNeighbourChange(Direction direction, @Nullable INodeOwner newNeighbour, @Nullable INodeOwner prevNeighbour) {
		if (newNeighbour instanceof ScreenBlockEntity screenBlockEntity) {
			this.screenEntity = screenBlockEntity;
			updateVgaText();
		}
		else if (screenEntity == prevNeighbour) {
			this.screenEntity = null;
		}
	}

	public void setScreenDirty(boolean value) {
		this.isScreenDirty = value;
	}

	public boolean isScreenDirty() {
		return this.isScreenDirty;
	}

	public boolean canRun() {
		return !motherboard.isEmpty();
	}

	public ItemStack replaceMotherboard(ItemStack newStack) {
		ItemStack oldMotherboard = motherboard;
		motherboard = newStack;

		setChanged();
		return oldMotherboard;
	}
}
