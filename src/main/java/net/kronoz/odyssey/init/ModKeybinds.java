package net.kronoz.odyssey.init;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
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
    private static KeyBinding SLICE;

    public static void init() {



        SLICE = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.odyssey.slice",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_Y,
                "key.categories.gameplay"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            var p = client.player;
            MovementVisuals.clientTick(p);

            while (SLICE.wasPressed()) {
                if (p != null) {
                    ModNetworking.send(new SliceAttackC2SPayload(1, 1, 1, 1));
                }
            }
        });
    }
}