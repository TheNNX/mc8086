package pl.pflp.mcx86;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class MCx86PacketHandler {
	private static final String PROTOCOL_VERSION = "1";
	public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
			new ResourceLocation(MCx86Mod.MODID, "main"), () -> PROTOCOL_VERSION, PROTOCOL_VERSION::equals,
			PROTOCOL_VERSION::equals);

	private static boolean registered = false;

	public static final boolean registerMessages() {
		// if (registered == true) {
		// return false;
		// }

		int messageId = 0;

		INSTANCE.registerMessage(messageId++, VgaUpdatePacket.class, VgaUpdatePacket::encode, VgaUpdatePacket::new,
				VgaUpdatePacket::handle);
		INSTANCE.registerMessage(messageId++, KeypressPacket.class, KeypressPacket::encode, KeypressPacket::new,
				KeypressPacket::handle);

		registered = true;
		return true;
	}
}
