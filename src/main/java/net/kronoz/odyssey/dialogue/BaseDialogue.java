package net.kronoz.odyssey.dialogue;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public abstract class BaseDialogue {
    public abstract Identifier id();
    protected abstract void define(Script s);

    public final void play(ServerPlayerEntity player) {
        Script s = new Script(this.id());
        define(s);
        s.scheduleFor(player);
    }
}
