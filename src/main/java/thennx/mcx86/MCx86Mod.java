package thennx.mcx86;

import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import thennx.mcx86.computer.ComputerBlock;
import thennx.mcx86.computer.ComputerBlockEntity;
import thennx.mcx86.gui.ConfigScreen;
import thennx.mcx86.packets.MCx86PacketHandler;
import thennx.mcx86.screen.Screen;
import thennx.mcx86.screen.ScreenBlockEntity;
import thennx.mcx86.screen.ScreenRenderer;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(MCx86Mod.MODID)
public class MCx86Mod {
	public static final String MODID = "mcx86mod";

	public static final Logger LOGGER = LogUtils.getLogger();
	public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
	public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);

	public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister
			.create(Registries.CREATIVE_MODE_TAB, MODID);

	public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister
			.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MODID);

	public static final RegistryObject<Block> COMPUTER_BLOCK = BLOCKS.register("computer_8086",
			() -> new ComputerBlock(BlockBehaviour.Properties.of().mapColor(MapColor.STONE).noOcclusion().isViewBlocking((state, world, pos) -> false)));

	public static final RegistryObject<Item> COMPUTER_BLOCK_ITEM = ITEMS.register("computer_8086",
			() -> new BlockItem(COMPUTER_BLOCK.get(), new Item.Properties()));

	public static final RegistryObject<Item> MOTHERBOARD_8086 = ITEMS.register("motherboard_8086",
			() -> new MotherboardItem(new Item.Properties()));

	public static final RegistryObject<Item> SCREWDRIVER = ITEMS.register("screwdriver", () -> new Item(new Item.Properties().stacksTo(1)));

	public static final RegistryObject<Block> SCREEN = BLOCKS.register("screen",
			() -> new Screen(BlockBehaviour.Properties.of().mapColor(MapColor.STONE)));
	public static final RegistryObject<Item> SCREEN_ITEM = ITEMS.register("screen",
			() -> new BlockItem(SCREEN.get(), new Item.Properties()));

	// Create redensestone blocks
	public static final RegistryObject<Block> WIRE_BLOCK = BLOCKS.register("wireblock",
			() -> new WireBlock(BlockBehaviour.Properties.of().mapColor(MapColor.STONE)));
	public static final RegistryObject<Item> WIRE_BLOCK_ITEM = ITEMS.register("wireblock",
			() -> new BlockItem(WIRE_BLOCK.get(), new Item.Properties()));

	public static final RegistryObject<CreativeModeTab> CREATIVE_TAB = CREATIVE_MODE_TABS.register("mcx86mod_tab",
			() -> CreativeModeTab.builder().withTabsBefore(CreativeModeTabs.COMBAT)
					.icon(() -> MOTHERBOARD_8086.get().getDefaultInstance()).displayItems((parameters, output) -> {
						output.accept(MOTHERBOARD_8086.get());
						output.accept(COMPUTER_BLOCK_ITEM.get());
						output.accept(SCREEN_ITEM.get());
						output.accept(WIRE_BLOCK_ITEM.get());
						output.accept(SCREWDRIVER.get());
					}).build());

	public static final RegistryObject<BlockEntityType<ScreenBlockEntity>> SCREEN_BLOCK_ENTITY = BLOCK_ENTITY_TYPES
			.register("screen",
					() -> BlockEntityType.Builder.of(ScreenBlockEntity::new, SCREEN.get()).build(null));

	public static final RegistryObject<BlockEntityType<ComputerBlockEntity>> COMPUTER_BLOCK_ENTITY = BLOCK_ENTITY_TYPES
			.register("computer_8086",
					() -> BlockEntityType.Builder.of(ComputerBlockEntity::new, COMPUTER_BLOCK.get()).build(null));

	public static final RegistryObject<BlockEntityType<WireBlockEntity>> WIRE_BLOCK_ENTITY = BLOCK_ENTITY_TYPES
			.register("wire",
					() -> BlockEntityType.Builder.of(WireBlockEntity::new, WIRE_BLOCK.get()).build(null));

	public MCx86Mod() {
		IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

		modEventBus.addListener(this::commonSetup);
		modEventBus.addListener(this::clientSetup);

		BLOCKS.register(modEventBus);
		ITEMS.register(modEventBus);
		BLOCK_ENTITY_TYPES.register(modEventBus);
		CREATIVE_MODE_TABS.register(modEventBus);

		// Register the packets used by the mod
		MCx86PacketHandler.registerMessages();

		MinecraftForge.EVENT_BUS.register(this);

		ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
	}

	private void commonSetup(final FMLCommonSetupEvent event) {
	}

	private void clientSetup(final FMLClientSetupEvent event) {
		ModLoadingContext.get().registerExtensionPoint(
				ConfigScreenHandler.ConfigScreenFactory.class,
				() -> new ConfigScreenHandler.ConfigScreenFactory((client, parentScreen) -> new ConfigScreen(parentScreen))
		);

		//ItemBlockRenderTypes.setRenderLayer(COMPUTER_BLOCK.get(), RenderType.translucent());
	}

	@Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
	public static class ClientModEvents {
		@SubscribeEvent
		public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
			event.registerBlockEntityRenderer(SCREEN_BLOCK_ENTITY.get(), ScreenRenderer::new);
		}
	}
}
