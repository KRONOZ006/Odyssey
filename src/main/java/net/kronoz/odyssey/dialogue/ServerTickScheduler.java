package net.kronoz.odyssey.dialogue;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public final class ServerTickScheduler {
    public static final class Task {
        int ticks; final Runnable run;
        public Task(int delayTicks, Runnable run){ this.ticks = Math.max(0, delayTicks); this.run = run; }
    }

    private static final List<Task> QUEUE = new LinkedList<>();

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            Iterator<Task> it = QUEUE.iterator();
            while (it.hasNext()) {
                Task t = it.next();
                if (t.ticks-- <= 0) { t.run.run(); it.remove(); }
            }
        });
    }

    public static void add(Task t){ QUEUE.add(t); }
    public static void clear(){ QUEUE.clear(); }
}
