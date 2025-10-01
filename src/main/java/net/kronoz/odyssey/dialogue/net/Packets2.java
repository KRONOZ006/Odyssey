package net.kronoz.odyssey.dialogue.net;

import net.kronoz.odyssey.dialogue.net.s2c.CaptionPlayS2C;
import net.kronoz.odyssey.dialogue.net.s2c.CaptionClearS2C;

public final class Packets2 {
    public static void initCommon() {
        // no C2S packets for this simple player-triggered system
    }
    public static void initClient() {
        CaptionPlayS2C.registerClient();
        CaptionClearS2C.registerClient();
    }
}
