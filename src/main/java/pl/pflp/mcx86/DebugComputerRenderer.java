package pl.pflp.mcx86;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;

import org.joml.Matrix4f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.world.entity.Display.RenderState;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import pl.pflp.vm8086.VM8086;

@OnlyIn(Dist.CLIENT)
public class DebugComputerRenderer implements BlockEntityRenderer<DebugComputerBlockEntity>, AutoCloseable {
	private final Font font;

	private static final Object2ObjectArrayMap<DebugComputerBlockEntity, ScreenInstance> screenInstanceMap = new Object2ObjectArrayMap<>();

	public DebugComputerRenderer(BlockEntityRendererProvider.Context p_173636_) {
		font = p_173636_.getFont();

	}

	private static ScreenInstance addScreenInstance(DebugComputerBlockEntity blockEntity) {
		ScreenInstance newScreen = new ScreenInstance(blockEntity);
		screenInstanceMap.put(blockEntity, newScreen);
		return newScreen;
	}

	private static ScreenInstance getScreenInstance(DebugComputerBlockEntity blockEntity) {
		if (screenInstanceMap.containsKey(blockEntity) == false) {
			return addScreenInstance(blockEntity);
		}
		return screenInstanceMap.get(blockEntity);
	}

	public static void removeBlockEntity(DebugComputerBlockEntity blockEntity) {
		screenInstanceMap.remove(blockEntity);
	}

	public void render(DebugComputerBlockEntity blockEntity, float p_112498_, PoseStack p_112499_,
			MultiBufferSource p_112500_, int p_112501_, int p_112502_) {
		renderSignWithText(blockEntity, p_112499_, p_112500_);
	}

	public float getSignModelRenderScale() {
		return 0.5f;
	}

	public float getSignTextRenderScale() {
		return 0.15f;
	}

	@Override
	public int getViewDistance() {
		return 12345;
	}

	public static void renderNoTranslate(PoseStack poseStack, MultiBufferSource multiBufferSource,
			DebugComputerBlockEntity blockEntity) {
		ScreenInstance instance = getScreenInstance(blockEntity);
		instance.draw(poseStack, multiBufferSource, blockEntity.getBlockPos().getCenter());
	}

	public static void renderSignWithText(DebugComputerBlockEntity blockEntity, PoseStack poseStack,
			MultiBufferSource multiBufferSource) {
		BlockState blockState = blockEntity.getBlockState();
		DebugComputer block = (DebugComputer) blockState.getBlock();
		poseStack.pushPose();

		translateSign(poseStack, -block.getYRotationDegrees(blockState));
		translateSignText(poseStack, true);

		// this.renderSignText(blockEntity.getBlockPos(), blockEntity.getVgaText(),
		// poseStack, multiBufferSource, p_279396_,
		// blockEntity.getTextLineHeight(), blockEntity.getMaxTextLineWidth());

		renderNoTranslate(poseStack, multiBufferSource, blockEntity);
		poseStack.popPose();
	}

	private static void translateSign(PoseStack poseStack, float rotation) {
		poseStack.translate(0.5f, 0.5f, 0.5f);
		poseStack.mulPose(Axis.YP.rotationDegrees(rotation + 180));
		poseStack.scale(1.01f, 1.01f, 1.00f);
		poseStack.translate(-0.5f, -0.5f, -0.5f);
	}

	void renderSignText(BlockPos blockPos, String[] lines, PoseStack poseStack, MultiBufferSource multiBufferSource,
			int p_279300_, int lineHeight, int maxLineWidth) {

		int j = lines.length * lineHeight / 2;
		int l;

		l = p_279300_;

		for (int k = 0; k < lines.length; k++) {
			String formattedcharsequence = lines[k];
			float f = (float) (-this.font.width(formattedcharsequence) / 2);

			this.font.drawInBatch(formattedcharsequence, f, (float) (k * lineHeight - j), DyeColor.BLACK.getId(), false,
					poseStack.last().pose(), multiBufferSource, Font.DisplayMode.POLYGON_OFFSET, 0, l);

			// this.font.drawInBatch8xOutline(Component.literal("a").getVisualOrderText(),
			// f, (float) (k * lineHeight - j), k, k, null, p_279338_, k);

		}

	}

	private static void translateSignText(PoseStack p_279133_, boolean p_279134_) {
		p_279133_.translate(1.0f / 16.0f, 3.0f / 16.0f, 0.01f);
		p_279133_.scale(14.0f / 16.0f, 11.0f / 16.0f, 1.0f);
	}

	static int getDarkColor(SignText p_277914_) {
		int i = p_277914_.getColor().getTextColor();
		if (i == DyeColor.BLACK.getTextColor() && p_277914_.hasGlowingText()) {
			return -988212;
		} else {
			double d0 = 0.4D;
			int j = (int) ((double) FastColor.ARGB32.red(i) * 0.4D);
			int k = (int) ((double) FastColor.ARGB32.green(i) * 0.4D);
			int l = (int) ((double) FastColor.ARGB32.blue(i) * 0.4D);
			return FastColor.ARGB32.color(0, j, k, l);
		}
	}

	static class ScreenInstance implements AutoCloseable {
		private DynamicTexture dynamicTexture;
		private boolean requiresUpload = true;
		private int[] data = new int[80 * 9 * 25 * 16];
		private RenderType renderType;
		private static int nextScreenId = 0;
		private int screenId;
		private DebugComputerBlockEntity blockEntity;

		public ScreenInstance(DebugComputerBlockEntity blockEntity) {
			screenId = nextScreenId++;
			dynamicTexture = new DynamicTexture(80 * 9, 25 * 16, true);
			ResourceLocation resourcelocation = Minecraft.getInstance().textureManager.register("screen/" + screenId,
					this.dynamicTexture);
			this.renderType = RenderType.text(resourcelocation);
			this.blockEntity = blockEntity;
		}

		public int getWidth() {
			return this.dynamicTexture.getPixels().getWidth();
		}

		public int getHeight() {
			return this.dynamicTexture.getPixels().getHeight();
		}

		int sinceLastCall = 0;

		void draw(PoseStack p_93292_, MultiBufferSource p_93293_, Vec3 position) {
			double length = Minecraft.getInstance().player.position().subtract(position).length();

			if (this.requiresUpload || sinceLastCall > 2 + length / 2) {
				this.updateTexture();
				this.requiresUpload = false;
				sinceLastCall = 0;
			}
			sinceLastCall++;

			Matrix4f matrix4f = p_93292_.last().pose();
			VertexConsumer vertexconsumer = p_93293_.getBuffer(this.renderType);
			vertexconsumer.vertex(matrix4f, 0.0F, 1.0F, -0.01F).color(255, 255, 255, 255).uv(0.0F, 1.0F).uv2(0xF000F0)
					.endVertex();
			vertexconsumer.vertex(matrix4f, 1.0F, 1.0F, -0.01F).color(255, 255, 255, 255).uv(1.0F, 1.0F).uv2(0xF000F0)
					.endVertex();
			vertexconsumer.vertex(matrix4f, 1.0F, 0.0F, -0.01F).color(255, 255, 255, 255).uv(1.0F, 0.0F).uv2(0xF000F0)
					.endVertex();
			vertexconsumer.vertex(matrix4f, 0.0F, 0.0F, -0.01F).color(255, 255, 255, 255).uv(0.0F, 0.0F).uv2(0xF000F0)
					.endVertex();

		}

		private int last = 0;

		public void updateTexture() {
			int width = this.getWidth();
			int height = this.getHeight();

			last = (last + 1) % 2;

			/*
			 * for (int y = 0; y < height; y++) { for (int x = 0; x < width; x++) {
			 * 
			 * int xCharCoord = x / 9; int yCharCoord = y / 16;
			 * 
			 * if ((xCharCoord + yCharCoord) % 2 == last) data[x + y * width] = 0xFF000000;
			 * else data[x + y * width] = (last == 0) ? 0xFF100000 : 0xFFFF0000; } }
			 */

			int[] egaColors = { 0xFF000000, 0xFFAA0000, 0xFF00AA00, 0xFFAAAA00, 0xFF0000AA, 0xFFAA00AA, 0xFF0055AA,
					0xFFAAAAAA, 0xFF555555, 0xFFFF5555, 0xFF55FF55, 0xFFFFFF55, 0xFF5555FF, 0xFFFF55FF, 0xFF55FFFF,
					0xFFFFFFFF };

			VM8086 vm = this.blockEntity.getVM();
			int videomemStart = 0xB8000;

			for (int y = 0; y < 25; y++) {
				for (int x = 0; x < 80; x++) {
					int offset = 2 * (x + y * 80);
					char c = (char) (vm.readMemoryBytePhysical(videomemStart + offset) & 0xFF);
					int colorIdx = (char) (vm.readMemoryBytePhysical(videomemStart + 1 + offset) & 0xFF);

					int colorForeground = egaColors[colorIdx & 0xF];
					int colorBackround = egaColors[(colorIdx & 0xF0) >> 4];

					if (offset == 162) {
						offset = 162;
					}

					for (int yy = 0; yy < 16; yy++) {
						for (int xx = 0; xx < 9; xx++) {
							int vgaRomOffset = c * 16 + yy;
							int vgaData = VM8086.VGA_ROM_F16[vgaRomOffset];
							if ((vgaData & (1 << (8 - xx - 1))) != 0)
								data[xx + x * 9 + (yy + y * 16) * 80 * 9] = colorForeground;
							else
								data[xx + x * 9 + (yy + y * 16) * 80 * 9] = colorBackround;
						}
					}
				}
			}

			for (int y = 0; y < height; y++) {
				for (int x = 0; x < width; x++) {
					this.dynamicTexture.getPixels().setPixelRGBA(width - x - 1, height - y - 1, data[x + y * width]);
				}
			}

			this.dynamicTexture.upload();
		}

		@Override
		public void close() throws Exception {
			dynamicTexture.close();
		}
	}

	@Override
	public void close() throws Exception {
		for (ScreenInstance screen : screenInstanceMap.values()) {
			screen.close();
		}

		screenInstanceMap.clear();
	}
}