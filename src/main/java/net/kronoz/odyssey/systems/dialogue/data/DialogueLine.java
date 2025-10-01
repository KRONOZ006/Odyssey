package net.kronoz.odyssey.systems.dialogue.data;

import com.google.gson.JsonObject;
import net.minecraft.util.Identifier;

public record DialogueLine(String caption, Identifier soundId, int durationMs, int weight) {
    public static DialogueLine fromJson(JsonObject o){
        String c = o.get("caption").getAsString();
        Identifier s = o.has("sound") ? Identifier.of(o.get("sound").getAsString()) : null;
        int d = o.has("duration_ms") ? o.get("duration_ms").getAsInt() : 0;
        int w = o.has("weight") ? o.get("weight").getAsInt() : 1;
        return new DialogueLine(c, s, d, w);
    }
}
