package net.kronoz.odyssey.dialogue;

import net.kronoz.odyssey.dialogue.ServerTickScheduler.Task;
import net.kronoz.odyssey.dialogue.net.s2c.CaptionClearS2C;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public final class Script {
    private final Identifier dialogueId;
    private final List<Step> steps = new ArrayList<>();

    public Script(Identifier dialogueId){ this.dialogueId = dialogueId; }

    // Say caption at tick offset, show for durationTicks; optional sound
    public Script say(int atTicks, int durationTicks, String caption){
        steps.add(new Step(atTicks, durationTicks, caption, null));
        return this;
    }
    public Script say(int atTicks, int durationTicks, String caption, Identifier soundId){
        steps.add(new Step(atTicks, durationTicks, caption, soundId));
        return this;
    }

    // Schedule to server tick timeline for this player
    void scheduleFor(ServerPlayerEntity p){
        // Sort by start time to enforce order
        steps.sort((a,b)->Integer.compare(a.atTicks(), b.atTicks()));
        for (Step st : steps) {
            int start = Math.max(0, st.atTicks());
            int dur   = Math.max(1, st.durationTicks());

            // send play packet at 'start'
            ServerTickScheduler.add(new Task(start, () ->
                    net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p,
                            new net.kronoz.odyssey.dialogue.net.s2c.CaptionPlayS2C(
                                    st.caption(),
                                    java.util.Optional.ofNullable(st.soundId()),
                                    dur
                            ))
            ));

            // clear at start + dur
            ServerTickScheduler.add(new Task(start + dur, () ->
                    net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p,
                            new CaptionClearS2C())
            ));
        }
    }
}
