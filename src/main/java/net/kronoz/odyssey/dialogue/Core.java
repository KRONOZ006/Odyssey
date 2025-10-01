package net.kronoz.odyssey.dialogue;


import net.kronoz.odyssey.client.ClientHooks;
import net.kronoz.odyssey.dialogue.net.Packets2;
import net.kronoz.odyssey.dialogue.sample.ExampleIntro;

public final class Core {
    public static void initCommon() {
        Packets2.initCommon();
        ServerTickScheduler.init();
        // register your dialogues here:
        Registry.register(new ExampleIntro());
        // Registry.register(new AnotherOne());
    }

    public static void initClient() {
        Packets2.initClient();
        ClientHooks.init();
    }
}
