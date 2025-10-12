package net.kronoz.odyssey.net;

import net.minecraft.util.Identifier;

public final class BossHudPackets {
    public static final Identifier BOSS_HUD_UPDATE = Identifier.of("odyssey","boss_hud_update");
    public static final Identifier BOSS_HUD_CLEAR  = Identifier.of("odyssey","boss_hud_clear");
    private BossHudPackets(){}
}
