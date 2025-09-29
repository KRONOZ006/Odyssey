package net.kronoz.odyssey.cca;

import net.minecraft.util.Identifier;
import org.ladysnake.cca.api.v3.component.ComponentV3;

public interface DialogueComponent extends ComponentV3 {
    Identifier currentTree();
    String currentNode();
    boolean inConversation();
    void start(Identifier treeId, String nodeKey);
    void setNode(String nodeKey);
    void end();
    Identifier presetTree();
    void setPreset(Identifier id);
}
