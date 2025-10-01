package net.kronoz.odyssey.systems.dialogue.data;

import com.google.gson.JsonObject;

public record DialogueChoice(String id, String text, String gotoNode) {
    public static DialogueChoice fromJson(JsonObject o){
        String id = o.has("id") ? o.get("id").getAsString() : o.get("text").getAsString();
        String txt = o.get("text").getAsString();
        String next = o.get("goto").getAsString();
        return new DialogueChoice(id, txt, next);
    }
}
