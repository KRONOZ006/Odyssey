package net.kronoz.odyssey.dialogue.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

public record DialogueNode(String key, List<DialogueLine> lines, List<DialogueChoice> choices, boolean allowMulti) {
    public static DialogueNode fromJson(JsonObject obj, String key){
        List<DialogueLine> lines = new ArrayList<>();
        JsonArray arr = obj.getAsJsonArray("lines");
        if (arr != null) for (int i=0;i<arr.size();i++) lines.add(DialogueLine.fromJson(arr.get(i).getAsJsonObject()));
        List<DialogueChoice> choices = new ArrayList<>();
        JsonArray carr = obj.getAsJsonArray("choices");
        if (carr != null) for (int i=0;i<carr.size();i++) choices.add(DialogueChoice.fromJson(carr.get(i).getAsJsonObject()));
        boolean multi = obj.has("allow_multi") && obj.get("allow_multi").getAsBoolean();
        return new DialogueNode(key, lines, choices, multi);
    }
}
