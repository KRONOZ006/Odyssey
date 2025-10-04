package net.kronoz.odyssey.item.client.renderer;

import net.kronoz.odyssey.Odyssey;
import net.kronoz.odyssey.item.custom.JetpackTorso;
import net.kronoz.odyssey.systems.physics.jetpack.JetpackExhaustManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.renderer.specialty.DynamicGeoItemRenderer;

import java.util.Set;

public class JetpackRenderer extends DynamicGeoItemRenderer<JetpackTorso> {
    private static final Identifier MODEL_ID = Identifier.of(Odyssey.MODID, "jetpack");
    private static final Set<String> BOOSTERS = Set.of("booster_1","booster_2","booster_3");

    public JetpackRenderer() {
        super(new DefaultedItemGeoModel<>(MODEL_ID));
    }

    @Override
    protected boolean boneRenderOverride(
            MatrixStack ms,
            GeoBone bone,
            VertexConsumerProvider buffers,
            VertexConsumer buffer,
            float pt,
            int packedLight,
            int packedOverlay,
            int colour
    ) {
        if (!BOOSTERS.contains(bone.getName())) return false;

        ItemStack stack = this.getCurrentItemStack();
        if (stack == null || stack.isEmpty()) return false;

        Matrix4f m = ms.peek().getPositionMatrix();
        Vec3d originWS = toWorld(bone, m);

        // derive a stable context id from the camera entity if present
        String ownerId = "nocam";
        var mc = MinecraftClient.getInstance();
        if (mc != null && mc.getCameraEntity() != null) {
            ownerId = mc.getCameraEntity().getUuidAsString();
        }

        // per-stack identity + bone name => 3 distinct emitters
        String stackId = Integer.toHexString(System.identityHashCode(stack));
        String key = ownerId + ":" + stackId + ":" + bone.getName();

        JetpackExhaustManager.emit(key, originWS);
        return false;
    }

    private static Vec3d toWorld(GeoBone bone, Matrix4f m) {
        float lx = bone.getPosX() / 16f;
        float ly = bone.getPosY() / 16f;
        float lz = bone.getPosZ() / 16f;
        Vector4f v = new Vector4f(lx, ly, lz, 1f).mul(m);
        Vec3d cam = MinecraftClient.getInstance().gameRenderer.getCamera().getPos();
        return new Vec3d(cam.x + v.x, cam.y + v.y, cam.z + v.z);
    }
}
