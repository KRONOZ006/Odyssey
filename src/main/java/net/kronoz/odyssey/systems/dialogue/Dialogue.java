package net.kronoz.odyssey.systems.dialogue;

import net.kronoz.odyssey.command.DialogueCommand;
import net.kronoz.odyssey.systems.dialogue.data.DialogueLoader;
import net.kronoz.odyssey.systems.dialogue.net.Packets;
import net.kronoz.odyssey.systems.dialogue.server.DialogueScheduler;

public final class Dialogue {
    public static final String MODID = "odyssey";
    public static void init() {
        DialogueLoader.init();
        Packets.initCommon();
        DialogueScheduler.init();
        DialogueCommand.init();
    }
}
