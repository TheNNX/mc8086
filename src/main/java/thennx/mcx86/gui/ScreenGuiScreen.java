package thennx.mcx86.gui;

import com.mojang.blaze3d.vertex.PoseStack;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.math.Axis;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import thennx.mcx86.packets.KeypressPacket;
import thennx.mcx86.packets.MCx86PacketHandler;
import thennx.mcx86.screen.ScreenBlockEntity;
import thennx.mcx86.screen.ScreenRenderer;

public class ScreenGuiScreen extends Screen {
	private final ScreenBlockEntity screenEntity;

	public ScreenGuiScreen(ScreenBlockEntity p_277842_) {
		this(p_277842_, Component.translatable("sign.edit"));
	}

	public ScreenGuiScreen(ScreenBlockEntity blockEntity, Component p_277393_) {
		super(p_277393_);
		this.screenEntity = blockEntity;
	}

	@Override
	protected void init() {
		this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, (p_251194_) -> {
			this.onDone();
		}).bounds(this.width / 2 - 100, this.height / 4 + 144, 200, 20).build());
	}

	@Override
	public void tick() {
		if (!this.isValid()) {
			this.onDone();
		}
	}

	private boolean isValid() {
		if (minecraft == null || minecraft.player == null) {
			return false;
		}
		if (screenEntity.getBlockPos().distToCenterSqr(minecraft.player.getX(), minecraft.player.getY(), minecraft.player.getZ()) > KeypressPacket.KEYPRESS_VALID_DISTSQUARE) {
			return false;
		}
		return !this.screenEntity.isRemoved();
	}

	@Override
	public boolean keyPressed(int key, int scancode, int modifiers) {
		MCx86PacketHandler.INSTANCE.sendToServer(new KeypressPacket(screenEntity, (char) key, scancode, true));
		return super.keyPressed(key, scancode, modifiers);
	}

	@Override
	public boolean keyReleased(int key, int scancode, int modifiers) {
		MCx86PacketHandler.INSTANCE.sendToServer(new KeypressPacket(screenEntity, (char) key, scancode, false));
		return super.keyReleased(key, scancode, modifiers);
	}

	@Override
	public void render(GuiGraphics guiGraphics, int p_281700_, int p_283040_, float p_282799_) {
		Lighting.setupForFlatItems();

		this.renderBackground(guiGraphics);

		BlockState blockstate = this.screenEntity.getBlockState();
		PoseStack pose = guiGraphics.pose();
		pose.pushPose();

		float effectiveWidth;
		float effectiveHeight;
		float desiredScale = 0.90f;
		float aspectRatio = 4.0f / 3.0f;

		effectiveWidth = this.width * desiredScale;
		effectiveHeight = effectiveWidth / aspectRatio;

		if (effectiveHeight > this.height) {
			effectiveHeight = this.height * desiredScale;
			effectiveWidth = effectiveHeight * aspectRatio;
		}

		pose.translate((this.width - effectiveWidth) / 2, (this.height - effectiveHeight) / 2, 10.0F);
		pose.scale(effectiveWidth, effectiveHeight, 1.0f);
		pose.translate(-1.0f, -1.0f, 0);
		pose.rotateAround(Axis.ZP.rotation(3.141592f), 1.0f, 1.0f, 0);
		ScreenRenderer.renderNoTranslate(pose, guiGraphics.bufferSource(), screenEntity);

		pose.popPose();

		Lighting.setupFor3DItems();
		super.render(guiGraphics, p_281700_, p_283040_, p_282799_);
	}

	@Override
	public void onClose() {
		this.onDone();
	}

	@Override
	public void removed() {
		super.removed();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private void onDone() {
		this.minecraft.setScreen((Screen) null);
	}
}
