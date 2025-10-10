package net.kronoz.odyssey.systems.slide;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public final class SlideClient implements ClientModInitializer {
    private static KeyBinding SLIDE_KEY;
    @Override public void onInitializeClient() {
        SLIDE_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.odyssey.slide", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_CONTROL, "key.categories.movement"));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {

        });
    }
}
