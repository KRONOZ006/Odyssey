package net.kronoz.odyssey.dialogue;

import net.kronoz.odyssey.command.DialogueCommand;
import net.kronoz.odyssey.dialogue.data.DialogueLoader;
import net.kronoz.odyssey.dialogue.net.Packets;
import net.kronoz.odyssey.dialogue.server.DialogueScheduler;

public final class Dialogue {
    public static final String MODID = "odyssey";
    public static void init() {
        DialogueLoader.init();
        Packets.initCommon();
        DialogueScheduler.init();
        DialogueCommand.init();
    }
}
