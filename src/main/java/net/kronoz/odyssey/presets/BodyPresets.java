package net.kronoz.odyssey.presets;

import net.minecraft.util.Identifier;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class BodyPresets {
    private static final Map<String, Map<String, Identifier>> PRESETS = new HashMap<>();

    public static void init() {
        Map<String, Identifier> scout = new HashMap<>();
        scout.put("right_arm", Identifier.of("odyssey","example_arm_upgrade"));
        PRESETS.put("scout", Collections.unmodifiableMap(scout));

        Map<String, Identifier> assault = new HashMap<>();
        assault.put("right_arm", Identifier.of("odyssey","example_arm_upgrade"));
        PRESETS.put("assault", Collections.unmodifiableMap(assault));
    }

    public static Map<String, Identifier> get(String name) { return PRESETS.get(name); }
    public static java.util.Set<String> names() { return PRESETS.keySet(); }
}
