package net.kronoz.odyssey.init;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.kronoz.odyssey.movement.MovementVisuals;
import net.kronoz.odyssey.net.DashC2SPayload;
import net.kronoz.odyssey.net.SliceAttackC2SPayload;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

public final class ModKeybinds {
    private static KeyBinding DASH;
    private static KeyBinding SLICE;
    private static long lastPressMs = 0;

    public static void init() {
        DASH = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.odyssey.dash",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_R,
                "key.categories.movement"
        ));


        SLICE = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.odyssey.slice",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_Y,
                "key.categories.gameplay"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            var p = client.player;
            MovementVisuals.clientTick(p);

            while (DASH.wasPressed()) {
                long now = System.currentTimeMillis();
                if (now - lastPressMs < 120) continue;
                lastPressMs = now;
                if (p == null) continue;

                var opts = MinecraftClient.getInstance().options;
                boolean f = opts.forwardKey.isPressed();
                boolean b = opts.backKey.isPressed();
                boolean l = opts.leftKey.isPressed();
                boolean r = opts.rightKey.isPressed();
                boolean up = opts.jumpKey.isPressed();
                boolean dn = opts.sneakKey.isPressed();

                Vec3d look = p.getRotationVec(1.0f).normalize();
                Vec3d fwdFlat = new Vec3d(look.x, 0, look.z);
                if (fwdFlat.lengthSquared() < 1e-6) fwdFlat = new Vec3d(0, 0, 1);
                fwdFlat = fwdFlat.normalize();
                Vec3d right = new Vec3d(fwdFlat.z, 0, -fwdFlat.x);

                double ax = (r ? 1 : 0) - (l ? 1 : 0);
                double az = (f ? 1 : 0) - (b ? 1 : 0);
                double keyVy = (up ? 1 : 0) - (dn ? 1 : 0);

                double pitchVy = MathHelper.clamp(look.y, -1.0, 1.0);
                double vy = keyVy != 0 ? keyVy : pitchVy;

                Vec3d horiz = fwdFlat.multiply(az).add(right.multiply(ax));
                if (horiz.lengthSquared() < 1e-6) horiz = fwdFlat;
                horiz = horiz.normalize();

                double hMag = Math.sqrt(Math.max(0.0, 1.0 - vy * vy));
                Vec3d dir = new Vec3d(horiz.x * hMag, vy, horiz.z * hMag).normalize();

                float spd = p.isSprinting() ? 1.2f : 0.95f;
                float tinyUp = p.isOnGround() ? 0.04f : 0.0f;

                ModNetworking.send(new DashC2SPayload((float)dir.x, (float)dir.y, (float)dir.z, spd, tinyUp));
            }

            while (SLICE.wasPressed()) {
                if (p != null) {
                    ModNetworking.send(new SliceAttackC2SPayload(1, 1, 1, 1));
                }
            }
        });
    }
}