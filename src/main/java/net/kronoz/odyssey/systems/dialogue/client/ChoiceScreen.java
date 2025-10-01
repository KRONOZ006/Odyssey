package net.kronoz.odyssey.systems.dialogue.client;

import net.kronoz.odyssey.systems.dialogue.data.DialogueChoice;
import net.kronoz.odyssey.systems.dialogue.net.c2s.SelectChoiceC2S;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.List;

public final class ChoiceScreen extends Screen {
    private final boolean multi;
    private final List<DialogueChoice> choices;

    public ChoiceScreen(boolean multi, List<DialogueChoice> choices){
        super(Text.literal("Dialogue"));
        this.multi = multi; this.choices = choices;
    }

    @Override protected void init(){
        int y = this.height/2 - choices.size()*12;
        for (int i=0;i<choices.size();i++){
            DialogueChoice c = choices.get(i);
            this.addDrawableChild(ButtonWidget.builder(Text.literal(c.text()), b -> {
                SelectChoiceC2S.sendClient(c.id());
                if (!multi) this.close();
            }).dimensions(this.width/2-110, y + i*24, 220, 20).build());
        }
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Close"), b -> this.close())
                .dimensions(this.width/2-60, y + choices.size()*24 + 10, 120, 20).build());
    }

    // Screen.close() est public en 1.21 — on ne peut pas baisser la visibilité
    @Override public void close(){
        assert this.client != null;
        this.client.setScreen(null);
        super.close();
    }
}
