package com.isaacmod;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashSet;
import java.util.Set;

public class TearEntity extends ThrownItemEntity {
    public float damage = 3.5f;
    public int flags = 0;
    public int maxAge = 24;
    private final Set<Integer> hitIds = new HashSet<>();

    public TearEntity(EntityType<? extends TearEntity> type, World world) {
        super(type, world);
    }

    public TearEntity(World world, LivingEntity owner) {
        super(IsaacMod.TEAR, owner, world);
    }

    @Override
    protected Item getDefaultItem() {
        return Items.GHAST_TEAR; // бесплатная ванильная "слеза"
    }

    @Override
    protected float getGravity() {
        return 0.015f;
    }

    @Override
    protected boolean canHit(Entity entity) {
        return super.canHit(entity) && !hitIds.contains(entity.getId());
    }

    @Override
    public void tick() {
        super.tick();
        if (isRemoved() || getWorld().isClient) return;
        if ((flags & IsaacStats.HOMING) != 0) homing();
        if ((flags & IsaacStats.FIRE) != 0 && age % 2 == 0 && getWorld() instanceof ServerWorld sw)
            sw.spawnParticles(ParticleTypes.FLAME, getX(), getY(), getZ(), 1, 0.02, 0.02, 0.02, 0.0);
        if (age > maxAge) {
            impact();
            discard();
        }
    }

    private void homing() {
        LivingEntity target = null;
        double best = 100;
        for (LivingEntity e : getWorld().getEntitiesByClass(LivingEntity.class, getBoundingBox().expand(10),
                x -> x != getOwner() && x.isAlive() && x instanceof Monster && !hitIds.contains(x.getId()))) {
            double d = e.squaredDistanceTo(this);
            if (d < best) { best = d; target = e; }
        }
        if (target != null) {
            Vec3d v = getVelocity();
            double speed = v.length();
            Vec3d to = target.getPos().add(0, target.getHeight() * 0.5, 0).subtract(getPos()).normalize();
            setVelocity(v.normalize().multiply(0.7).add(to.multiply(0.3)).normalize().multiply(speed));
            velocityDirty = true;
        }
    }

    @Override
    protected void onEntityHit(EntityHitResult hit) {
        super.onEntityHit(hit);
        Entity en = hit.getEntity();
        if (!hitIds.add(en.getId())) return;
        if (en instanceof LivingEntity le) {
            le.timeUntilRegen = 0; // каждая слеза засчитывается
            boolean ok = le.damage(getDamageSources().thrown(this, getOwner()), damage);
            if (!ok) return;
            if ((flags & IsaacStats.FIRE) != 0) le.setOnFireFor(4);
            if ((flags & IsaacStats.POISON) != 0) le.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 100, 0));
            if ((flags & IsaacStats.SLOW) != 0) le.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 80, 2));
            if ((flags & IsaacStats.WITHER) != 0) le.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 80, 0));
            if ((flags & IsaacStats.LIFESTEAL) != 0 && getOwner() instanceof LivingEntity o && random.nextFloat() < 0.3f)
                o.heal(1f);
        }
    }

    @Override
    protected void onCollision(HitResult result) {
        super.onCollision(result);
        if (getWorld().isClient) return;
        if (result.getType() == HitResult.Type.ENTITY && (flags & IsaacStats.PIERCE) != 0) return;
        impact();
        discard();
    }

    private void impact() {
        if (!(getWorld() instanceof ServerWorld sw)) return;
        sw.spawnParticles(ParticleTypes.SPLASH, getX(), getY(), getZ(), 8, 0.1, 0.1, 0.1, 0.1);
        if ((flags & IsaacStats.EXPLODE) != 0) {
            for (LivingEntity e : sw.getEntitiesByClass(LivingEntity.class, getBoundingBox().expand(2.5),
                    x -> x != getOwner() && x.isAlive())) {
                e.timeUntilRegen = 0;
                e.damage(getDamageSources().thrown(this, getOwner()), damage * 0.8f);
            }
            sw.spawnParticles(ParticleTypes.EXPLOSION, getX(), getY(), getZ(), 1, 0, 0, 0, 0);
            sw.playSound(null, getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 0.6f, 1.3f);
        }
    }
}
