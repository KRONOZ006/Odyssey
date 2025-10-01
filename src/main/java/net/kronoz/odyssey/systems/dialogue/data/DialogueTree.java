package net.kronoz.odyssey.systems.dialogue.data;

import net.minecraft.util.Identifier;

import java.util.Map;

public record DialogueTree(Identifier id, String startNode, Map<String, DialogueNode> nodes) {
    public DialogueNode node(String key){ return nodes.get(key); }
}
