package net.kronoz.odyssey.dialogue.net;

import net.kronoz.odyssey.dialogue.DialogueIds;
import net.kronoz.odyssey.dialogue.net.c2s.SelectChoiceC2S;
import net.kronoz.odyssey.dialogue.net.s2c.PlayLineS2C;
import net.kronoz.odyssey.dialogue.net.s2c.ShowChoicesS2C;
import net.minecraft.util.Identifier;

public final class Packets {
    public static final Identifier PLAY_LINE_ID   = DialogueIds.id("play_line");
    public static final Identifier SHOW_CHOICES_ID= DialogueIds.id("show_choices");
    public static final Identifier SELECT_CHOICE_ID= DialogueIds.id("select_choice");

    public static void initCommon(){
        // C2S
        SelectChoiceC2S.register();
    }
    public static void initClient(){
        // S2C
        PlayLineS2C.registerClient();
        ShowChoicesS2C.registerClient();
    }
}
