package net.kronoz.odyssey.systems.model;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

public final class _MountItem {
    private _MountItem() {}

    private static boolean isSlim(PlayerEntity player) {
        // Client-only skin provider → safe in our client mixin
        var skin = MinecraftClient.getInstance().getSkinProvider().getSkinTextures(player.getGameProfile());
        return skin != null && "slim".equalsIgnoreCase(skin.model().name());
    }

    private static void render(Identifier itemId, PlayerEntity player, MatrixStack matrices,
                               VertexConsumerProvider vertices, int light, Runnable align,
                               ModelTransformationMode mode) {
        if (itemId == null) return;
        Item item = Registries.ITEM.get(itemId);
        if (item == null) { System.out.println("[Odyssey/Mount] item not found: " + itemId); return; }
        ItemStack stack = new ItemStack(item);

        matrices.push();
        align.run();
        MinecraftClient.getInstance().getItemRenderer().renderItem(
                stack, mode, light, OverlayTexture.DEFAULT_UV,
                matrices, vertices,
                (player.getWorld() instanceof ClientWorld cw) ? cw : null,
                player.getId()
        );
        matrices.pop();
    }

    /** RIGHT ARM (pivot -5,2,0) */
    public static void onRightArm(Identifier itemId, PlayerEntity player,
                                  PlayerEntityModel<PlayerEntity> model, MatrixStack ms,
                                  VertexConsumerProvider vcp, int light) {
        render(itemId, player, ms, vcp, light, () -> {
            model.rightArm.rotate(ms);
            double x = isSlim(player) ? -0.055 : -0.06;
            ms.translate(x, 0.45, -0.14);
            ms.scale(0.9f, 0.9f, 0.9f);
        }, ModelTransformationMode.THIRD_PERSON_RIGHT_HAND);
    }

    /** LEFT ARM (pivot 5,2,0) */
    public static void onLeftArm(Identifier itemId, PlayerEntity player,
                                 PlayerEntityModel<PlayerEntity> model, MatrixStack ms,
                                 VertexConsumerProvider vcp, int light) {
        render(itemId, player, ms, vcp, light, () -> {
            model.leftArm.rotate(ms);
            double x = isSlim(player) ? 0.055 : 0.06;
            ms.translate(x, 0.45, -0.14);
            ms.scale(0.9f, 0.9f, 0.9f);
        }, ModelTransformationMode.THIRD_PERSON_LEFT_HAND);
    }

    /** HEAD (pivot 0,0,0) */
    public static void onHead(Identifier itemId, PlayerEntity player,
                              PlayerEntityModel<PlayerEntity> model, MatrixStack ms,
                              VertexConsumerProvider vcp, int light) {
        render(itemId, player, ms, vcp, light, () -> {
            model.head.rotate(ms);
            ms.translate(0.0, -0.25, 0.0);
        }, ModelTransformationMode.HEAD);
    }

    /** TORSO (pivot 0,0,0) */
    public static void onTorso(Identifier itemId, PlayerEntity player,
                               PlayerEntityModel<PlayerEntity> model, MatrixStack ms,
                               VertexConsumerProvider vcp, int light) {
        render(itemId, player, ms, vcp, light, () -> {
            model.body.rotate(ms);
            ms.translate(0.0, 0.0, 0.0);
            ms.scale(0.9f, 0.9f, 0.9f);
        }, ModelTransformationMode.FIXED);
    }

    /** RIGHT LEG (pivot -1.9,12,0) */
    public static void onRightLeg(Identifier itemId, PlayerEntity player,
                                  PlayerEntityModel<PlayerEntity> model, MatrixStack ms,
                                  VertexConsumerProvider vcp, int light) {
        render(itemId, player, ms, vcp, light, () -> {
            model.rightLeg.rotate(ms);
            ms.translate(-0.05, 0.0, -0.08);
            ms.scale(0.9f, 0.9f, 0.9f);
        }, ModelTransformationMode.FIXED);
    }

    /** LEFT LEG (pivot 1.9,12,0) */
    public static void onLeftLeg(Identifier itemId, PlayerEntity player,
                                 PlayerEntityModel<PlayerEntity> model, MatrixStack ms,
                                 VertexConsumerProvider vcp, int light) {
        render(itemId, player, ms, vcp, light, () -> {
            model.leftLeg.rotate(ms);
            ms.translate(0.05, 0.0, -0.08);
            ms.scale(0.9f, 0.9f, 0.9f);
        }, ModelTransformationMode.FIXED);
    }
}
