package net.kronoz.odyssey.systems.cinematics.runtime;

import net.minecraft.client.MinecraftClient;

import java.util.HashMap;
import java.util.Map;

public final class CutsceneManager {
    public static final CutsceneManager I = new CutsceneManager();

    private final Map<String, Cutscene> scenes = new HashMap<>();
    private CutscenePlayer current;
    private String currentId = "";

    private CutsceneManager(){}

    public void register(Cutscene cs){ scenes.put(cs.id(), cs); }

    public boolean play(String id){
        var cs = scenes.get(id);
        if(cs == null) return false;

        stop();

        InputBlocker.apply(cs.lockHud(), cs.lockInput());

        current = new CutscenePlayer(cs);
        currentId = id;
        current.play();
        return true;
    }

    public boolean setSpeed(double mult){
        if(current == null) return false;
        current.setSpeed(mult);
        return true;
    }

    public void stop(){
        if(current != null){
            boolean restore = scenes.getOrDefault(currentId, new Cutscene("", null, 0, true, true, true, Cutscene.CameraMode.ADDITIVE_FOLLOW, false)).restoreOnEnd();
            current.stop();
            current = null;
            currentId = "";
            CameraOverrideController.I.clear();
            if(restore){
                InputBlocker.release();
            }
        } else {
            InputBlocker.release();
            CameraOverrideController.I.clear();
        }
    }

    public void clientTick(){
        var mc = MinecraftClient.getInstance();
        if(mc == null) return;
        if(current == null) return;

        var cs = scenes.get(currentId);
        if(cs != null){
            InputBlocker.apply(cs.lockHud(), cs.lockInput());
        }

        double dtSec = mc.getRenderTickCounter().getLastFrameDuration() / 20.0;
        current.tick(dtSec);

        if(!current.isPlaying()){
            stop();
        }
    }
}
