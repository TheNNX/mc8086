package thennx.mcx86.computer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import thennx.mcx86.*;
import thennx.mcx86.item.AbstractComponentItem;
import thennx.mcx86.pool.PoolManager;
import thennx.mcx86.screen.ScreenBlockEntity;
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
	private boolean devicesDiscovered = false;
	private boolean isScreenDirty = false;
	private Path savePath = null;
	private boolean delayedLoadQueued = false;
	private ScreenBlockEntity screenEntity = null;
	private Node<ComputerBlockEntity> node = new Node<>(this);
	private final ComputerBlockEntityInventoryHandler inventoryHandler = new ComputerBlockEntityInventoryHandler(this);
	public int redstoneCardNumber = 0;

	public ComputerBlockEntity(BlockPos pos, BlockState state) {
		super(MCx86Mod.COMPUTER_BLOCK_ENTITY.get(), pos, state);
	}

	public void createVm() throws IOException {
		if (virtualMachine != null) {
			shutdownVm(true);
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

		vm8086.memory[0xB8000 / IMemoryBank.BANK_SIZE] = new VideoMemoryBank(this);

		assert(level == null || level.isClientSide() == false);
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

		if (virtualMachine.isRunning()) {
			resumeVm();
		}
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

	public Path getComputerSaveLocation() {
		if (level == null || level.isClientSide())
			return null;
		return level.getServer().getWorldPath(LevelResource.ROOT).resolve("mcx86");
	}

	private void prepareDirectories() throws IOException {
		Path parentPath = getComputerSaveLocation();
		if (parentPath == null)
			return;

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

	@Override
	public CompoundTag getUpdateTag() {
		CompoundTag tag = super.getUpdateTag();
		tag.put("inventory", inventoryHandler.serializeNBT());
		return tag;
	}

	@Override
	public void handleUpdateTag(CompoundTag tag) {
		super.handleUpdateTag(tag);
		inventoryHandler.deserializeNBT(tag.getCompound("inventory"));
	}

	@Override
	protected void saveAdditional(CompoundTag tag) {
		tag.put("inventory", inventoryHandler.serializeNBT());

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
		try {
			if (virtualMachine == null && (level == null || !level.isClientSide())) {
				createVm();
			}

			inventoryHandler.deserializeNBT(nbt.getCompound("inventory"));

			delayedLoadQueued = true;
			if (virtualMachine != null) {
				synchronized (virtualMachine) {
					virtualMachine.load(new NbtStateStorage(nbt));
				}
			}
		}
		catch (IOException exception) {
			throw new RuntimeException(exception);
		}
	}

	public void resumeVm() {
		if (level == null || level.isClientSide())
			return;
		getVM().setRunning(true);
		setChanged();
		PoolManager.getInstance().registerBlockEntity(this);
		level.setBlock(getBlockPos(), getBlockState().setValue(ComputerBlock.POWER_STATE, 1), 3);
	}

	public void startVm() {
		if (level == null || level.isClientSide())
			return;
		getVM().restart();
		resumeVm();
	}

	public void shutdownVm(boolean updateBlockState) {
		if (level == null || level.isClientSide())
			return;
		getVM().setRunning(false);
		setChanged();
		PoolManager.getInstance().unregisterBlockEntity(this);
		if (updateBlockState)
			level.setBlock(getBlockPos(), getBlockState().setValue(ComputerBlock.POWER_STATE, 0), 3);
		updateVgaText();
	}

	@Override
	public void onChunkUnloaded() {
		PoolManager.getInstance().unregisterBlockEntity(this);
		super.onChunkUnloaded();
	}

	public void deleteVm() {
        shutdownVm(false);

        try {
			prepareDirectories();
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
		return !inventoryHandler.getMotherboard().isEmpty();
	}

	public boolean tryInsert(ItemStack stack) {
		return inventoryHandler.insertItem(stack);
	}

	public boolean addDevice(IDeviceFactory.DeviceInstance deviceInstance) {
		if (deviceInstance != null && getVM() != null) {
            return getVM().tryAddDevice(deviceInstance.getName(), deviceInstance.getDevice());
        }
		return false;
	}

	public void removeDevice(IDeviceFactory.DeviceInstance deviceInstance) {
		if (deviceInstance != null && getVM() != null) {
			getVM().tryRemoveDevice(deviceInstance.getName());
		}
	}

	public ItemStack[] getBayItems() {
		return inventoryHandler.getBayItems();
	}

	public ItemStack getMotherboard() {
		return inventoryHandler.getMotherboard();
	}

	public ItemStack[] getCards() {
		return this.inventoryHandler.getCards();
	}

	public List<ComputerBlockEntityInventoryHandler.SlotArray> getSlotArrays() {
		return this.inventoryHandler.getSlotArrays();
	}

	private ComputerBlock getBlock() {
		return (ComputerBlock) getBlockState().getBlock();
	}

	public int getCardSlots() {
		return getBlock().getCaseMaxCardSlots();
	}

	public int getBaySlots() {
		return getBlock().getCaseBaySlots();
	}

	public boolean isCardSlotLong(int i) {
		return i < getBlock().getCaseMaxLongCardSlots();
	}

	public ComputerBlockEntityInventoryHandler getInventoryHandler() {
		return inventoryHandler;
	}

	public boolean hasRedstoneCards() {
		return redstoneCardNumber > 0;
	}
}
