package net.kronoz.odyssey.item.custom;

import net.kronoz.odyssey.item.client.renderer.JetpackRenderer;
import net.kronoz.odyssey.item.client.renderer.SpearRenderer;
import net.minecraft.client.render.item.BuiltinModelItemRenderer;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterial;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.instance.SingletonAnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.constant.DefaultAnimations;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class SpearDashItem extends SwordItem implements GeoItem {

    private static final String KEY_ACTIVE = "Active";
    private static final String KEY_EXPIRE = "Expire";
    private static final String KEY_DASH = "DashTicks";

    private static final int DASH_TICKS = 10;
    private static final double DASH_SPEED = 1.25;
    private static final float DASH_DAMAGE = 8.0f;
    private static final int ACTIVE_TIMEOUT = 200;

    private final AnimatableInstanceCache cache = new SingletonAnimatableInstanceCache(this);

    public SpearDashItem (ToolMaterial material, int attackDamage, float attackSpeed, Settings settings) {
        super(material, withAttributes(settings, attackDamage, attackSpeed));
    }
    private static Settings withAttributes(Settings base, int attackDamage, float attackSpeed) {
        AttributeModifiersComponent.Builder b = AttributeModifiersComponent.builder();

        b.add(
                net.minecraft.entity.attribute.EntityAttributes.GENERIC_ATTACK_DAMAGE,
                new net.minecraft.entity.attribute.EntityAttributeModifier(
                        Identifier.of("randomstuff", "avogaglaive_damage"),
                        (double) attackDamage,
                        net.minecraft.entity.attribute.EntityAttributeModifier.Operation.ADD_VALUE
                ),
                AttributeModifierSlot.MAINHAND
        );

        b.add(
                net.minecraft.entity.attribute.EntityAttributes.GENERIC_ATTACK_SPEED,
                new net.minecraft.entity.attribute.EntityAttributeModifier(
                        Identifier.of("randomstuff", "avogaglaive_speed"),
                        (double) attackSpeed,
                        net.minecraft.entity.attribute.EntityAttributeModifier.Operation.ADD_VALUE
                ),
                AttributeModifierSlot.MAINHAND
        );

        return base.component(DataComponentTypes.ATTRIBUTE_MODIFIERS, b.build());
    }



    private static NbtCompound getOrCreate(ItemStack stack) {
        NbtComponent comp = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(new NbtCompound()));
        NbtCompound tag = comp.copyNbt();
        if (tag == null) tag = new NbtCompound();
        return tag;
    }

    public static boolean isRenderActive(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;

        NbtCompound tag = getOrCreate(stack);

        if (!tag.contains(KEY_ACTIVE)) return false;
        boolean active = tag.getBoolean(KEY_ACTIVE);

        if (active) return true;

        return false;
    }

    private static void save(ItemStack stack, NbtCompound tag) {
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(tag));
    }

    private static boolean isActive(ItemStack s) { return getOrCreate(s).getBoolean(KEY_ACTIVE); }
    private static long now(World w) { return w.getTime(); }

    private static void setActive(ItemStack s, World w, boolean v) {
        NbtCompound n = getOrCreate(s);
        n.putBoolean(KEY_ACTIVE, v);
        if (v) n.putLong(KEY_EXPIRE, now(w) + ACTIVE_TIMEOUT);
        save(s, n);
    }

    private static void bumpExpire(ItemStack s, World w) {
        NbtCompound n = getOrCreate(s);
        n.putBoolean(KEY_ACTIVE, true);
        n.putLong(KEY_EXPIRE, now(w) + ACTIVE_TIMEOUT);
        save(s, n);
    }

    private static void autoDeactivate(ItemStack s, World w) {
        NbtCompound n = getOrCreate(s);
        if (n.getBoolean(KEY_ACTIVE) && now(w) >= n.getLong(KEY_EXPIRE)) {
            n.putBoolean(KEY_ACTIVE, false);
            save(s, n);
        }
    }

    private static void setDash(ItemStack s, int t) {
        NbtCompound n = getOrCreate(s);
        n.putInt(KEY_DASH, t);
        save(s, n);
    }

    private static int getDash(ItemStack s) {
        return getOrCreate(s).getInt(KEY_DASH);
    }

    // ======== Behavior ========

    @Override
    public net.minecraft.util.TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);

        if (!world.isClient) {
            Vec3d look = user.getRotationVec(1.0f).normalize();
            Vec3d horiz = new Vec3d(look.x, 0, look.z);
            if (horiz.lengthSquared() > 0) horiz = horiz.normalize();

            user.addVelocity(horiz.multiply(DASH_SPEED));
            user.velocityModified = true;

            setDash(stack, DASH_TICKS);
            user.getItemCooldownManager().set(this, 14);

            world.playSound(null, user.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 0.9f, 1.1f);
            user.incrementStat(Stats.USED.getOrCreateStat(this));
        }
        return net.minecraft.util.TypedActionResult.success(stack, world.isClient);
    }

    @Override
    public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (!attacker.getWorld().isClient) {
            if (!isActive(stack)) setActive(stack, attacker.getWorld(), true);
            else bumpExpire(stack, attacker.getWorld());
        }
        return super.postHit(stack, target, attacker);
    }

    @Override
    public ActionResult useOnEntity(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand) {
        if (!user.getWorld().isClient) {
            if (!isActive(stack)) setActive(stack, user.getWorld(), true);
            else bumpExpire(stack, user.getWorld());
        }
        return ActionResult.PASS;
    }

    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        if (world.isClient || !(entity instanceof PlayerEntity player)) return;

        autoDeactivate(stack, world);

        int dt = getDash(stack);
        if (dt > 0) {
            setDash(stack, dt - 1);

            if (player.horizontalCollision) {
                if (!isActive(stack)) setActive(stack, world, true);
                else bumpExpire(stack, world);
                world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_ANVIL_PLACE, SoundCategory.PLAYERS, 0.5f, 1.8f);
            }

            if (isActive(stack)) {
                dealDashDamage(world, player, stack);
            } else {
                HitResult hr = player.raycast(1.5, 0.0f, false);
                if (hr.getType() == HitResult.Type.BLOCK) {
                    setActive(stack, world, true);
                    world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_WOOD_HIT, SoundCategory.PLAYERS, 0.6f, 1.3f);
                }
            }
        }
    }

    private void dealDashDamage(World world, PlayerEntity player, ItemStack stack) {
        Vec3d pos = player.getPos();
        Vec3d look = player.getRotationVec(1.0f).normalize();
        Vec3d ahead = pos.add(look.multiply(1.2));
        Box box = new Box(pos, ahead).expand(0.6, 0.6, 0.6);

        Predicate<Entity> valid = e -> e.isAlive() && e != player && e instanceof LivingEntity && player.canSee(e);

        List<Entity> hits = world.getOtherEntities(player, box, valid);
        if (!hits.isEmpty()) {
            for (Entity e : hits) {
                if (e instanceof LivingEntity le) {
                    e.damage(player.getDamageSources().playerAttack(player), DASH_DAMAGE);
                    Vec3d kb = look.multiply(0.5).add(0, 0.1, 0);
                    e.addVelocity(kb.x, kb.y, kb.z);
                    e.velocityModified = true;
                }
            }
            bumpExpire(stack, world);
            world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 1.0f, 1.2f);
        }
    }

    // ======== Animations ========

    private static final RawAnimation ANIM_INACTIVE = RawAnimation.begin().thenLoop("inactive_loop");
    private static final RawAnimation ANIM_ACTIVATE = RawAnimation.begin().thenPlay("activate");
    private static final RawAnimation ANIM_ACTIVE   = RawAnimation.begin().thenLoop("active_loop");

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar c) {
        c.add(new AnimationController<>(this, "spear", 5, state -> {
            ItemStack s = state.getData(DataTickets.ITEMSTACK);
            if (s == null) return state.setAndContinue(DefaultAnimations.IDLE);
            boolean active = isActive(s);

            if (active) {
                if (state.getController().getCurrentAnimation() == null)
                    return state.setAndContinue(ANIM_ACTIVATE);
                if (state.getController().hasAnimationFinished())
                    return state.setAndContinue(ANIM_ACTIVE);
                return state.setAndContinue(ANIM_ACTIVE);
            } else {
                return state.setAndContinue(ANIM_INACTIVE);
            }
        }));
    }
    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new GeoRenderProvider() {
            private SpearRenderer renderer;
            @Override
            public BuiltinModelItemRenderer getGeoItemRenderer() {
                if (renderer == null) renderer = new SpearRenderer();
                return renderer;
            }
        });
    }
    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
