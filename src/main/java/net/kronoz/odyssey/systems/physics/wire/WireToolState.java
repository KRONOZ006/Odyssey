package net.kronoz.odyssey.systems.physics.wire;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import static net.kronoz.odyssey.init.ModItems.WIRE_TOOL;

public final class WireToolState {
    private static final double MARKER_RADIUS = 0.0625;
    private static @Nullable WireAnchor clientPendingAnchor;

    private WireToolState() {
    }

    public static void clientSetPending(@Nullable WireAnchor anchor) {
        clientPendingAnchor = anchor;
    }

    public static void clientClearPending() {
        clientPendingAnchor = null;
    }

    public static @Nullable WireAnchor clientPending() {
        return clientPendingAnchor;
    }

    public static void syncPendingFromHeldStack(ClientPlayerEntity player) {
        if (player == null) {
            clientPendingAnchor = null;
            return;
        }
        ItemStack held = player.getMainHandStack();
        if (held.isEmpty() || held.getItem() != WIRE_TOOL) {
            clientPendingAnchor = null;
            return;
        }
        WireAnchor fromStack = WireToolMath.readPendingAnchor(held);
        if (fromStack != null) {
            clientPendingAnchor = fromStack;
        }
    }

    public static @Nullable WireAnchor hoveredAnchor(MinecraftClient client) {
        if (client == null || client.player == null) {
            return null;
        }
        ItemStack held = client.player.getMainHandStack();
        if (held.isEmpty() || held.getItem() != WIRE_TOOL) {
            return null;
        }
        HitResult hit = client.crosshairTarget;
        if (!(hit instanceof BlockHitResult bhr)) {
            return null;
        }
        return WireToolMath.anchorFromHit(bhr.getBlockPos(), bhr.getSide(), bhr.getPos());
    }

    public static void renderPreview(MatrixStack ignored, VertexConsumerProvider.Immediate buffers) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null || client.player == null) {
            return;
        }
        ItemStack held = client.player.getMainHandStack();
        if (held.isEmpty() || held.getItem() != WIRE_TOOL) {
            return;
        }

        syncPendingFromHeldStack(client.player);
        WireAnchor hovered = hoveredAnchor(client);
        Vec3d pendingPos = clientPendingAnchor != null ? WireToolMath.anchorCenter(clientPendingAnchor) : null;
        Vec3d hoveredPos = hovered != null ? WireToolMath.anchorCenter(hovered) : null;

        Vec3d cam = client.gameRenderer.getCamera().getPos();
        MatrixStack ms = new MatrixStack();
        ms.translate(-cam.x, -cam.y, -cam.z);

        VertexConsumer lineConsumer = buffers.getBuffer(RenderLayer.getLines());
        if (pendingPos != null) {
            drawMarker(ms, lineConsumer, pendingPos, 0.30f, 0.90f, 1.00f, 1.00f);
        }
        if (hoveredPos != null) {
            drawMarker(ms, lineConsumer, hoveredPos, 1.00f, 0.90f, 0.25f, 1.00f);
        }
        if (pendingPos != null && hoveredPos != null) {
            drawLine(ms, lineConsumer, pendingPos, hoveredPos, 0.85f, 0.95f, 1.00f, 0.92f);
        }
    }

    private static void drawMarker(MatrixStack matrices, VertexConsumer consumer, Vec3d pos, float r, float g, float b, float a) {
        Box marker = new Box(
                pos.x - MARKER_RADIUS, pos.y - MARKER_RADIUS, pos.z - MARKER_RADIUS,
                pos.x + MARKER_RADIUS, pos.y + MARKER_RADIUS, pos.z + MARKER_RADIUS
        );
        WorldRenderer.drawBox(matrices, consumer, marker, r, g, b, a);
    }

    private static void drawLine(MatrixStack matrices, VertexConsumer consumer, Vec3d a, Vec3d b, float r, float g, float bl, float alpha) {
        MatrixStack.Entry entry = matrices.peek();
        Matrix4f pm = entry.getPositionMatrix();

        Vec3d normal = b.subtract(a);
        if (normal.lengthSquared() < 1.0E-6) {
            normal = new Vec3d(0.0, 1.0, 0.0);
        } else {
            normal = normal.normalize();
        }

        consumer.vertex(pm, (float) a.x, (float) a.y, (float) a.z);
        consumer.color(r, g, bl, alpha);
        consumer.normal(entry, (float) normal.x, (float) normal.y, (float) normal.z);
        consumer.vertex(pm, (float) b.x, (float) b.y, (float) b.z);
        consumer.color(r, g, bl, alpha);
        consumer.normal(entry, (float) normal.x, (float) normal.y, (float) normal.z);
    }
}
