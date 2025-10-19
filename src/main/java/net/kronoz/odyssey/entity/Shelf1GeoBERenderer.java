package net.kronoz.odyssey.entity;

import net.kronoz.odyssey.block.custom.Shelf1Block;
import net.kronoz.odyssey.block.custom.Shelf1GeoModel;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;
import org.joml.Quaternionf;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;

import java.util.Optional;

public class Shelf1GeoBERenderer implements BlockEntityRenderer<Shelf1BlockEntity> {
    private final Shelf1GeoModel model = new Shelf1GeoModel();

    // Fine tuning: slight downwards nudge so items sit flush; slight push off the wall if needed
    private static final float GLOBAL_ITEM_SCALE = 0.50f;
    private static final float Y_ADJUST = -0.01f;      // -1/100 block (lower a bit)
    private static final float SURFACE_PUSH = 0.001f;  // push forward a hair after rotations
    private static final boolean LAY_FLAT_NON_BLOCKS = true;

    public Shelf1GeoBERenderer(BlockEntityRendererFactory.Context ctx) {}

    @Override
    public void render(Shelf1BlockEntity be, float tickDelta, MatrixStack ms, VertexConsumerProvider buffers, int light, int overlay) {
        if (be.getWorld() == null) return;

        Direction facing = be.getCachedState().get(Shelf1Block.FACING);
        BakedGeoModel baked = model.getBakedModel(model.getModelResource(be));

        for (int i = 0; i < be.size(); i++) {
            ItemStack stack = be.getStack(i);
            if (stack.isEmpty()) continue;

            String boneName = "placeholder" + (i + 1);
            Optional<GeoBone> opt = baked.getBone(boneName);
            if (opt.isEmpty()) continue;
            GeoBone bone = opt.get();

            ms.push();
            rotateToFacing(ms, facing);

            // --- PIVOT (Bedrock pixels) -> Java block space:
            float px = bone.getPivotX();
            float py = bone.getPivotY();
            float pz = bone.getPivotZ();

            // Map [-8..+8] px centered to [0..1] block; flip Z axis
            float bx = (px + 8.0f) / 16.0f;
            float by = (py) / 16.0f;
            float bz = (8.0f - pz) / 16.0f;

            ms.translate(bx, by + Y_ADJUST, bz);

            // Bone rotations are radians; apply Z -> Y -> X (matches Bedrock export)
            ms.multiply(RotationAxis.POSITIVE_Z.rotation(bone.getRotZ()));
            ms.multiply(RotationAxis.POSITIVE_Y.rotation(bone.getRotY()));
            ms.multiply(RotationAxis.POSITIVE_X.rotation(bone.getRotX()));

            // Lay items flat if they aren't blocks
            if (LAY_FLAT_NON_BLOCKS && !(stack.getItem() instanceof BlockItem)) {
                ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90f));
            }

            // A tiny forward push so we don't Z-fight with the shelf surface
            ms.translate(0.0f, 0.0f, SURFACE_PUSH);

            ms.scale(GLOBAL_ITEM_SCALE, GLOBAL_ITEM_SCALE, GLOBAL_ITEM_SCALE);
            renderItem(stack, ms, buffers, light, i);
            ms.pop();
        }
    }

    private void rotateToFacing(MatrixStack ms, Direction facing) {
        float yaw = switch (facing) {
            case NORTH -> 0f;
            case SOUTH -> 180f;
            case WEST  -> 90f;
            case EAST  -> -90f;
            default    -> 0f;
        };
        ms.translate(0.5, 0, 0.5);
        ms.multiply(new Quaternionf().rotationY((float) Math.toRadians(yaw)));
        ms.translate(-0.5, 0, -0.5);
    }

    private void renderItem(ItemStack stack, MatrixStack ms, VertexConsumerProvider buffers, int light, int slotIndex) {
        ItemRenderer ir = MinecraftClient.getInstance().getItemRenderer();
        var world = MinecraftClient.getInstance().world;
        int seed = stack.getItem().hashCode() ^ (slotIndex * 31);
        ir.renderItem(stack, ModelTransformationMode.FIXED, light, OverlayTexture.DEFAULT_UV, ms, buffers, world, seed);
    }
}
