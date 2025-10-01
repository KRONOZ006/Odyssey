package net.kronoz.odyssey.dialogue.sample;

import net.kronoz.odyssey.dialogue.BaseDialogue;
import net.kronoz.odyssey.dialogue.Script;
import net.minecraft.util.Identifier;

public final class ExampleIntro extends BaseDialogue {
    private static final Identifier ID = Identifier.of("odyssey", "intro_simple");

    @Override public Identifier id() { return ID; }

    @Override protected void define(Script s) {
        // say(atTick, durationTicks, caption[, soundId])
        s.say(0,   40, "Système actif.", Identifier.of("odyssey", "voice.ai_boot"));
        s.say(45,  50, "Prêt à poursuivre.", Identifier.of("odyssey", "voice.ai_ready"));
        s.say(100, 60, "Récupérez le noyau. Aucune erreur.");
        // Add more lines anytime
    }
}
