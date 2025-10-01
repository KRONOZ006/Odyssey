package net.kronoz.odyssey.systems.dialogue.server;

import net.kronoz.odyssey.cca.DialogueComponent;
import net.kronoz.odyssey.init.ModComponents;
import net.kronoz.odyssey.systems.dialogue.data.*;
import net.kronoz.odyssey.systems.dialogue.net.s2c.PlayLineS2C;
import net.kronoz.odyssey.systems.dialogue.net.s2c.ShowChoicesS2C;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Random;

public final class DialogueManager {
    private static final Random RNG = new Random();

    public static void start(ServerPlayerEntity p, Identifier treeId){
        DialogueTree t = DialogueLoader.get(treeId);
        if (t == null) return;
        DialogueComponent c = ModComponents.DIALOGUE.get(p);
        c.start(treeId, t.startNode());
        playNode(p, t, t.startNode());
    }

    public static void selectChoice(ServerPlayerEntity p, String choiceId){
        DialogueComponent c = ModComponents.DIALOGUE.get(p);
        if (!c.inConversation()) return;
        DialogueTree t = DialogueLoader.get(c.currentTree());
        if (t == null) { c.end(); return; }
        DialogueNode n = t.node(c.currentNode());
        if (n == null) { c.end(); return; }
        DialogueChoice chosen = n.choices().stream().filter(ch -> ch.id().equals(choiceId)).findFirst().orElse(null);
        if (chosen == null) return;
        c.setNode(chosen.gotoNode());
        playNode(p, t, chosen.gotoNode());
    }

    static void playNode(ServerPlayerEntity p, DialogueTree t, String nodeKey){
        var n = t.node(nodeKey);
        if (n == null) return;

        var lines = n.lines();
        if (lines == null || lines.isEmpty()) {
            // pas de ligne -> direct choix ou fin
            if (!n.choices().isEmpty()) ShowChoicesS2C.send(p, n.allowMulti(), n.choices());
            else ModComponents.DIALOGUE.get(p).end();
            return;
        }

        boolean hasWeights = lines.stream().anyMatch(l -> l.weight() > 0);
        if (!hasWeights && lines.size() > 1) {
            // ===== Mode SEQUENCE =====
            int acc = 0;
            for (int i=0; i<lines.size(); i++){
                var l = lines.get(i);
                int dur = Math.max(1, l.durationMs()); // ms
                int delay = acc;
                if (i == 0) {
                    // first now
                    PlayLineS2C.send(p, l);
                } else {
                    // suivantes planifiées
                    DialogueScheduler.queue(() -> PlayLineS2C.send(p, l), delay);
                }
                acc += dur;
            }
            // après la dernière ligne -> choix ou fin
            DialogueScheduler.queue(() -> {
                if (!n.choices().isEmpty()) ShowChoicesS2C.send(p, n.allowMulti(), n.choices());
                else ModComponents.DIALOGUE.get(p).end();
            }, acc);
        } else {
            // ===== Mode RANDOM pondéré (comportement original) =====
            var line = pickWeighted(lines);
            if (line != null) {
                PlayLineS2C.send(p, line);
                int dur = Math.max(1, line.durationMs());
                DialogueScheduler.queue(() -> advanceAfterLine(p, t, n), dur);
            } else {
                if (!n.choices().isEmpty()) ShowChoicesS2C.send(p, n.allowMulti(), n.choices());
                else ModComponents.DIALOGUE.get(p).end();
            }
        }
    }


    static void advanceAfterLine(ServerPlayerEntity p, DialogueTree t, DialogueNode n){
        if (!n.choices().isEmpty()) ShowChoicesS2C.send(p, n.allowMulti(), n.choices());
        else ModComponents.DIALOGUE.get(p).end();
    }

    private static DialogueLine pickWeighted(List<DialogueLine> lines){
        if (lines==null || lines.isEmpty()) return null;
        int sum=0; for (DialogueLine l:lines) sum+=Math.max(1,l.weight());
        int r = RNG.nextInt(sum)+1;
        int run=0;
        for (DialogueLine l:lines){ run+=Math.max(1,l.weight()); if (r<=run) return l; }
        return lines.get(0);
    }
}
