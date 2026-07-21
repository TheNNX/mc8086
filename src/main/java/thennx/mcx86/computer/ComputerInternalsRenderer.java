package thennx.mcx86.computer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.particle.SuspendedParticle;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.Minecraft;
import thennx.mcx86.MCx86Mod;
import thennx.mcx86.item.BayItem;
import thennx.mcx86.item.MotherboardItem;

public class ComputerInternalsRenderer implements BlockEntityRenderer<ComputerBlockEntity> {
    public ComputerInternalsRenderer(BlockEntityRendererProvider.Context context) {
    }

    public void renderMotherboard(ComputerBlockEntity blockEntity, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();

        poseStack.pushPose();
        poseStack.rotateAround(Axis.XP.rotationDegrees(90), 0.0f, 0.0f, 0.0f);

        itemRenderer.renderStatic(
                blockEntity.getMotherboard(),
                ItemDisplayContext.FIXED,
                packedLight,
                packedOverlay,
                poseStack,
                bufferSource,
                blockEntity.getLevel(),
                0);
        poseStack.popPose();
    }

    @Override
    public void render(ComputerBlockEntity computerBlockEntity, float p_112308_, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        poseStack.pushPose();
        Direction facing = computerBlockEntity.getBlockState().getValue(ComputerBlock.DIRECTION_PROPERTY);
        boolean caseOff = computerBlockEntity.getBlockState().getValue(ComputerBlock.CASE_OFF);

        poseStack.rotateAround(Axis.YN.rotationDegrees(facing.toYRot() - 90), 0.5f, 0.5f, 0.5f);

        int light = 0;

        for (Direction d : Direction.values()) {
            int l = LevelRenderer.getLightColor(computerBlockEntity.getLevel(), computerBlockEntity.getBlockPos().relative(d));
            if (l > light) {
                light = l;
            }
        }

        int lightFront = LevelRenderer.getLightColor(computerBlockEntity.getLevel(), computerBlockEntity.getBlockPos().relative(facing));

        poseStack.pushPose();
        poseStack.rotateAround(Axis.YN.rotationDegrees(-90), 0.5f, 0.5f, 0.5f);
        poseStack.scale(1.0001f, 1.0001f, 1.0001f);

        for (int i = 0; i < computerBlockEntity.getBayItems().length; i++) {
            renderDriveBay(computerBlockEntity, poseStack, bufferSource, caseOff ? light : lightFront, packedOverlay, i);
        }

        poseStack.popPose();

        poseStack.translate(0.5, 1 / 32.0f, 0.5);

        renderMotherboard(computerBlockEntity, poseStack, bufferSource, packedLight, packedOverlay);

        int i = 0;
        for (ItemStack stack : computerBlockEntity.getCards()) {
            renderCard(computerBlockEntity, poseStack, bufferSource, light, packedOverlay, stack, i++);
        }

        poseStack.popPose();
    }

    private void renderDriveBay(ComputerBlockEntity blockEntity, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay, int bayNum) {
        poseStack.pushPose();

        BakedModel model = null;
        ItemStack bayStackItem = blockEntity.getBayItems()[bayNum];

        if (bayStackItem.isEmpty())
            model = Minecraft.getInstance().getModelManager().getModel(MCx86Mod.BAYSLOT_EMPTY_RES);
        else
            model = Minecraft.getInstance().getModelManager().getModel(((BayItem) bayStackItem.getItem()).getResourceLocation());

        poseStack.translate(0, bayNum * (4.0f / 16.0f), 0);

        ModelBlockRenderer renderer = Minecraft.getInstance().getBlockRenderer().getModelRenderer();
        renderer.renderModel(poseStack.last(), bufferSource.getBuffer(RenderType.solid()), blockEntity.getBlockState(), model, 1, 1, 1, packedLight, packedOverlay);

        poseStack.popPose();
    }

    private void renderCard(ComputerBlockEntity blockEntity, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay, ItemStack itemStack, int i) {
        if (itemStack.isEmpty()) {
            return;
        }

        poseStack.pushPose();

        ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();

        poseStack.translate(1/16.0f + 0.0001, 0.5 - 1 / 32.0f - 1 / 16.0f + 0.01, -1 / 32.0f - (5 - 2 * i)/16.0f);

        itemRenderer.renderStatic(
                itemStack,
                ItemDisplayContext.FIXED,
                packedLight,
                packedOverlay,
                poseStack,
                bufferSource,
                blockEntity.getLevel(),
                0);

        poseStack.popPose();
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
