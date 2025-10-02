package net.kronoz.odyssey.systems.cinematics.runtime;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
public final class InputBlocker {
    private static boolean hudSaved;
    private static boolean hudPrev;
    private static double sensitivityPrev;
    private static boolean grabbedPrev;

    private InputBlocker(){}

    public static void apply(boolean lockHud, boolean lockInput){
        MinecraftClient mc = MinecraftClient.getInstance();
        if(mc == null) return;
        GameOptions opt = mc.options;

        if(lockHud){
            if(!hudSaved){ hudPrev = opt.hudHidden; hudSaved = true; }
            opt.hudHidden = true;
        }

        if(lockInput){
            if(sensitivityPrev == 0) sensitivityPrev = opt.getMouseSensitivity().getValue();
            grabbedPrev = mc.mouse.isCursorLocked();

            opt.getMouseSensitivity().setValue(0.0);
            if(mc.mouse.isCursorLocked()){
                mc.mouse.unlockCursor();
            }

            opt.attackKey.setPressed(false);
            opt.useKey.setPressed(false);
            opt.forwardKey.setPressed(false);
            opt.backKey.setPressed(false);
            opt.leftKey.setPressed(false);
            opt.rightKey.setPressed(false);
            opt.jumpKey.setPressed(false);
            opt.sneakKey.setPressed(false);
            opt.sprintKey.setPressed(false);
        }
    }

    public static void release(){
        MinecraftClient mc = MinecraftClient.getInstance();
        if(mc == null) return;
        GameOptions opt = mc.options;

        if(hudSaved){
            opt.hudHidden = hudPrev;
            hudSaved = false;
        }

        if(sensitivityPrev != 0){
            opt.getMouseSensitivity().setValue(sensitivityPrev);
            sensitivityPrev = 0;
        }

        if(grabbedPrev && !mc.mouse.isCursorLocked()){
            mc.mouse.lockCursor();
        }
    }
}
