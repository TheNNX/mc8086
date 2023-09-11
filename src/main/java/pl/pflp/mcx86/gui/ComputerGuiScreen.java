package pl.pflp.mcx86.gui;

import org.joml.Vector3f;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.math.Axis;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import pl.pflp.mcx86.DebugComputerBlockEntity;
import pl.pflp.mcx86.DebugComputerRenderer;
import pl.pflp.mcx86.KeypressPacket;
import pl.pflp.mcx86.MCx86PacketHandler;

public class ComputerGuiScreen extends Screen {

	public static final float MAGIC_SCALE_NUMBER = 62.500004F;
	public static final float MAGIC_TEXT_SCALE = 0.9765628F;
	private static final Vector3f TEXT_SCALE = new Vector3f(0.9765628F, 0.9765628F, 0.9765628F);

	protected void renderSignBackground(GuiGraphics p_281440_, BlockState p_282401_) {
		DebugComputerRenderer.renderNoTranslate(p_281440_.pose(), p_281440_.bufferSource(), sign);
	}

	protected Vector3f getSignTextScale() {
		return TEXT_SCALE;
	}

	private final DebugComputerBlockEntity sign;

	public ComputerGuiScreen(DebugComputerBlockEntity p_277842_) {
		this(p_277842_, Component.translatable("sign.edit"));
	}

	public ComputerGuiScreen(DebugComputerBlockEntity p_277792_, Component p_277393_) {
		super(p_277393_);
		this.sign = p_277792_;
	}

	protected void init() {
		this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, (p_251194_) -> {
			this.onDone();
		}).bounds(this.width / 2 - 100, this.height / 4 + 144, 200, 20).build());
	}

	public void tick() {
		if (!this.isValid()) {
			this.onDone();
		}

	}

	private boolean isValid() {
		return this.minecraft != null && this.minecraft.player != null && !this.sign.isRemoved()
				&& !this.sign.playerIsTooFarAwayToEdit(this.minecraft.player.getUUID());
	}

	@Override
	public boolean keyPressed(int key, int scancode, int modifiers) {
		MCx86PacketHandler.INSTANCE.sendToServer(new KeypressPacket(sign, (char) key, scancode, true));
		return super.keyPressed(key, scancode, modifiers);
	}

	@Override
	public boolean keyReleased(int key, int scancode, int modifiers) {
		MCx86PacketHandler.INSTANCE.sendToServer(new KeypressPacket(sign, (char) key, scancode, false));
		return super.keyReleased(key, scancode, modifiers);
	}

	public void render(GuiGraphics p_282418_, int p_281700_, int p_283040_, float p_282799_) {
		Lighting.setupForFlatItems();
		this.renderBackground(p_282418_);
		this.renderSign(p_282418_);
		Lighting.setupFor3DItems();
		super.render(p_282418_, p_281700_, p_283040_, p_282799_);
	}

	public void onClose() {
		this.onDone();
	}

	public void removed() {
		super.removed();
	}

	public boolean isPauseScreen() {
		return false;
	}

	protected void offsetSign(GuiGraphics p_282672_, BlockState p_283056_) {
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

		p_282672_.pose().translate(0, 0, 10.0F);
		p_282672_.pose().translate((this.width - effectiveWidth) / 2, (this.height - effectiveHeight) / 2, 10.0F);
		p_282672_.pose().scale(effectiveWidth, effectiveHeight, 1.0f);
		p_282672_.pose().translate(-1.0f, -1.0f, 0);
		p_282672_.pose().rotateAround(Axis.ZP.rotation(3.141592f), 1.0f, 1.0f, 0);
	}

	private void renderSign(GuiGraphics p_282006_) {
		BlockState blockstate = this.sign.getBlockState();
		p_282006_.pose().pushPose();

		this.offsetSign(p_282006_, blockstate);
		// p_282006_.pose().rotateAround(Axis.ZP.rotation(3.14f), this.width / 2,
		// this.height / 2, 0);

		this.renderSignBackground(p_282006_, blockstate);

		p_282006_.pose().popPose();
	}

	private void onDone() {
		this.minecraft.setScreen((Screen) null);
	}
}
