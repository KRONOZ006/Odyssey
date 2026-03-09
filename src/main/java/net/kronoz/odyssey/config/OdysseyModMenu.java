package net.kronoz.odyssey.config;

import eu.midnightdust.lib.config.MidnightConfig;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.kronoz.odyssey.Odyssey;

public final class OdysseyModMenu implements ModMenuApi {
    @Override public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> MidnightConfig.getScreen(parent, Odyssey.MODID);
    }

}
