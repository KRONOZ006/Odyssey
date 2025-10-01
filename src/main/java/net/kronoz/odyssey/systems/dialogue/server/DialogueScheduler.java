package net.kronoz.odyssey.systems.dialogue.server;

public final class DialogueScheduler {
    static final class Task {
        final Runnable run; int ticks;
        Task(Runnable r, int ms){ this.run=r; this.ticks = Math.max(1, (ms + 49) / 50); } // ceil(ms/50)
    }
    private static final java.util.List<Task> TASKS = new java.util.ArrayList<>();

    public static void init(){
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(DialogueScheduler::tick);
    }

    // nouveau: planifie n’importe quelle action
    public static void queue(Runnable r, int ms){
        TASKS.add(new Task(r, ms));
    }

    private static void tick(net.minecraft.server.MinecraftServer server){
        var it = TASKS.iterator();
        while(it.hasNext()){
            var tk = it.next();
            if (--tk.ticks <= 0){ tk.run.run(); it.remove(); }
        }
    }
}

