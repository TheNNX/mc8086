package thennx.mcx86.screen;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ScreenRenderer implements BlockEntityRenderer<ScreenBlockEntity>, AutoCloseable {
	private final Font font;

	private static final Object2ObjectArrayMap<ScreenBlockEntity, ScreenInstance> screenInstanceMap = new Object2ObjectArrayMap<>();

	public ScreenRenderer(BlockEntityRendererProvider.Context p_173636_) {
		font = p_173636_.getFont();
	}

	private static ScreenInstance addScreenInstance(ScreenBlockEntity blockEntity) {
		ScreenInstance newScreen = new ScreenInstance(blockEntity);
		screenInstanceMap.put(blockEntity, newScreen);
		return newScreen;
	}

	private static ScreenInstance getScreenInstance(ScreenBlockEntity blockEntity) {
		if (!screenInstanceMap.containsKey(blockEntity)) {
			return addScreenInstance(blockEntity);
		}
		return screenInstanceMap.get(blockEntity);
	}

	@Override
	public void render(ScreenBlockEntity blockEntity, float p_112498_, PoseStack p_112499_,
					   MultiBufferSource p_112500_, int p_112501_, int p_112502_) {
		renderSignWithText(blockEntity, p_112499_, p_112500_);
	}

	@Override
	public int getViewDistance() {
		return 12345;
	}

	public static void renderNoTranslate(PoseStack poseStack, MultiBufferSource multiBufferSource,
										 ScreenBlockEntity blockEntity) {
		ScreenInstance instance = getScreenInstance(blockEntity);
		instance.draw(poseStack, multiBufferSource, blockEntity.getBlockPos().getCenter());
	}

	public static void renderSignWithText(ScreenBlockEntity blockEntity, PoseStack poseStack,
										  MultiBufferSource multiBufferSource) {
		BlockState blockState = blockEntity.getBlockState();
		Screen block = (Screen) blockState.getBlock();
		poseStack.pushPose();

		translateSign(poseStack, -block.getYRotationDegrees(blockState));
		translateSignText(poseStack, true);

		renderNoTranslate(poseStack, multiBufferSource, blockEntity);
		poseStack.popPose();
	}

	private static void translateSign(PoseStack poseStack, float rotation) {
		poseStack.translate(0.5f, 0.5f, 0.5f);
		poseStack.mulPose(Axis.YP.rotationDegrees(rotation + 180));
		poseStack.scale(1.01f, 1.01f, 1.00f);
		poseStack.translate(-0.5f, -0.5f, -0.51f);
	}

	private static void translateSignText(PoseStack p_279133_, boolean p_279134_) {
		p_279133_.translate(1.0f / 16.0f, 3.0f / 16.0f, 0.01f);
		p_279133_.scale(14.0f / 16.0f, 11.0f / 16.0f, 1.0f);
	}

	static class ScreenInstance implements AutoCloseable {
		private final DynamicTexture dynamicTexture;
		private boolean requiresUpload = true;
		private int[] data = null;
		private final RenderType renderType;
		private static int nextScreenId = 0;
		private final int screenId;
		private final ScreenBlockEntity blockEntity;

		public ScreenInstance(ScreenBlockEntity blockEntity) {
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
			vertexconsumer.vertex(matrix4f, 0.0F, 1.0F, -0.001F).color(255, 255, 255, 255).uv(0.0F, 1.0F).uv2(0xF000F0)
					.endVertex();
			vertexconsumer.vertex(matrix4f, 1.0F, 1.0F, -0.001F).color(255, 255, 255, 255).uv(1.0F, 1.0F).uv2(0xF000F0)
					.endVertex();
			vertexconsumer.vertex(matrix4f, 1.0F, 0.0F, -0.001F).color(255, 255, 255, 255).uv(1.0F, 0.0F).uv2(0xF000F0)
					.endVertex();
			vertexconsumer.vertex(matrix4f, 0.0F, 0.0F, -0.01F).color(255, 255, 255, 255).uv(0.0F, 0.0F).uv2(0xF000F0)
					.endVertex();

		}

		public void updateTexture() {
			int width = this.getWidth();
			int height = this.getHeight();
			int[] colorPalette = { 0xFF000000, 0xFFAA0000, 0xFF00AA00, 0xFFAAAA00, 0xFF0000AA, 0xFFAA00AA, 0xFF0055AA,
					0xFFAAAAAA, 0xFF555555, 0xFFFF5555, 0xFF55FF55, 0xFFFFFF55, 0xFF5555FF, 0xFFFF55FF, 0xFF55FFFF,
					0xFFFFFFFF };

			data = this.blockEntity.getDisplayPixelData(width, height, colorPalette);

			if (data == null)
				return;

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