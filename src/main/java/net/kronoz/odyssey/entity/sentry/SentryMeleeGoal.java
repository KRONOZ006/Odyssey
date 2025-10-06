package net.kronoz.odyssey.entity.sentry;

import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.player.PlayerEntity;

public class SentryMeleeGoal extends MeleeAttackGoal {
    private final SentryEntity mob;

    public SentryMeleeGoal(SentryEntity mob, double speed, boolean pauseWhenIdle) {
        super(mob, speed, pauseWhenIdle);
        this.mob = mob;
    }

    @Override
    public boolean canStart() {
        if (mob.getTarget() instanceof PlayerEntity p) {
            return super.canStart();
        }
        return false;
    }

    @Override
    public boolean shouldContinue() {
        if (mob.getTarget() instanceof PlayerEntity p) {
            return super.shouldContinue();
        }
        return false;
    }
}
