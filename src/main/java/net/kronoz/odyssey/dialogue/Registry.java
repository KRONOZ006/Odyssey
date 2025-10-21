package net.kronoz.odyssey.dialogue;

import net.minecraft.util.Identifier;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class Registry {
    private static final Map<Identifier, BaseDialogue> BY_ID = new HashMap<>();

    public static void register(BaseDialogue d) {
        var id = d.id();
        if (BY_ID.containsKey(id)) throw new IllegalStateException("Dialogue already registered: " + id);
        BY_ID.put(id, d);
    }

    public static BaseDialogue get(Identifier id) { return BY_ID.get(id); }

    public static Collection<BaseDialogue> all() { return Collections.unmodifiableCollection(BY_ID.values()); }
}
