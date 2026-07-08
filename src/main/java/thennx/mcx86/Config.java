package thennx.mcx86;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import thennx.mcx86.pool.PoolManager;

@Mod.EventBusSubscriber(modid = MCx86Mod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {
	private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

	public static final ForgeConfigSpec.IntValue EMULATION_QUANTUM = BUILDER.comment("Emulation quantum in milliseconds")
			.defineInRange("emulationQuantum", 10, 1, 500);

	public static final ForgeConfigSpec.IntValue EMULATION_WORKERS = BUILDER.comment("Number of emulator worker threads")
			.defineInRange("emulationWorkers", 3, 1, 500);


	public static final ForgeConfigSpec SPEC = BUILDER.build();

	public static int emulationQuantum;
	public static int emulationWorkers;

	@SubscribeEvent
	public static void onLoad(final ModConfigEvent event) {
		emulationQuantum = EMULATION_QUANTUM.get();
		emulationWorkers = EMULATION_WORKERS.get();

		applyPoolSettings();
	}

	public static void applyPoolSettings() {
		PoolManager.getInstance().resizePool(emulationWorkers);
		PoolManager.getInstance().changeQuantum(emulationQuantum);
	}
}
