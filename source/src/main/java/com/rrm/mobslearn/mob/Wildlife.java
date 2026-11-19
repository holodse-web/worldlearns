package com.rrm.mobslearn.mob;

import com.rrm.mobslearn.learn.MobGroup;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

final class Wildlife {
    private static final Map<String, Long> cooldowns = new HashMap<>();

    private Wildlife() {
    }

    private static boolean ready(String key, long now, long every) {
        Long last = cooldowns.get(key);
        if (last != null && now - last < every) return false;
        cooldowns.put(key, now);
        if (cooldowns.size() > 2048) cooldowns.clear();
        return true;
    }

    private static boolean ready(Entity e, String what, long now, long every) {
        return ready(e.getUUID() + what, now, every);
    }

    static void tick(ServerLevel level, Mob mob, MobGroup g, long t, RandomSource r) {
        int lvl;
        switch (g) {
            case FISH -> {
                if ((lvl = TraitTags.get(mob, "fish_school")) > 0 && t % 10 == 0) {
                    Player p = level.getNearestPlayer(mob, 4 + lvl);
                    if (p != null && (p.getMainHandItem().is(Items.WATER_BUCKET) || p.getMainHandItem().is(Items.BUCKET) || p.getMainHandItem().is(Items.TRIDENT))) {
                        Vec3 away = mob.position().subtract(p.position()).normalize().scale(0.25 + 0.08 * lvl);
                        mob.setDeltaMovement(mob.getDeltaMovement().add(away.x, 0.02, away.z));
                    }
                }
                if ((lvl = TraitTags.get(mob, "fish_dodge")) > 0) dodge(level, mob, lvl, 0.35);
            }
            case HORSE -> horse(level, mob, t);
            case CAT -> cat(level, mob, t);
            case PARROT -> parrot(level, mob, t, r);
            case RABBIT -> {
                if ((lvl = TraitTags.get(mob, "rabbit_thief")) > 0 && t % 20 == 0) rabbitThief(level, mob, lvl, r);
            }
            case WITCH -> {
                if ((lvl = TraitTags.get(mob, "witch_curse")) > 0 && mob.getTarget() instanceof Player p && mob.distanceTo(p) < 10
                    && mob.hasLineOfSight(p) && ready(mob, "curse", level.getGameTime(), 220 - 40L * lvl)) curse(level, mob, p, lvl);
                if ((lvl = TraitTags.get(mob, "witch_familiar")) > 0 && t % 100 == 0) familiar(level, mob, r);
            }
            case SLIME -> {
                if ((lvl = TraitTags.get(mob, "slime_merge")) > 0 && t % 40 == 0 && mob instanceof net.minecraft.world.entity.monster.cubemob.Slime slime) merge(level, slime, lvl);
            }
            case PHANTOM -> {
                if ((lvl = TraitTags.get(mob, "phantom_dive")) > 0 && mob.getTarget() instanceof Player p && p.isFallFlying() && t % 20 == 0) {
                    mob.addEffect(new MobEffectInstance(MobEffects.SPEED, 60, lvl - 1, false, false));
                    if (mob.distanceTo(p) > 6) mob.setDeltaMovement(p.position().subtract(mob.position()).normalize().scale(0.4 + 0.15 * lvl));
                }
            }
            case PILLAGER -> {
                if ((lvl = TraitTags.get(mob, "pillager_horn")) > 0 && mob.getTarget() instanceof Player p && level.isDarkOutside()
                    && ready("horn" + (mob.getBlockX() >> 6) + "," + (mob.getBlockZ() >> 6), level.getGameTime(), 6000)) horn(level, mob, p, lvl, r);
            }
            case IRON_GOLEM -> {
                if ((lvl = TraitTags.get(mob, "golem_repair")) > 0 && t % 40 == 0 && mob.getTarget() == null && mob.getHealth() < mob.getMaxHealth() * 0.9f) {
                    List<net.minecraft.world.entity.npc.villager.Villager> near = level.getEntitiesOfClass(net.minecraft.world.entity.npc.villager.Villager.class,
                        mob.getBoundingBox().inflate(6), v -> !v.isBaby() && !v.isSleeping());
                    if (!near.isEmpty()) {
                        mob.heal(1.0f * lvl);
                        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, mob.getX(), mob.getY() + 1.8, mob.getZ(), 3, 0.5, 0.4, 0.5, 0);
                        if (r.nextInt(4) == 0) level.playSound(null, mob.blockPosition(), SoundEvents.IRON_GOLEM_REPAIR, SoundSource.NEUTRAL, 0.6f, 1.1f);
                    }
                }
            }
            case WOLF -> {
                if ((lvl = TraitTags.get(mob, "wolf_howl")) > 0 && t % 100 == 0 && level.isDarkOutside() && mob instanceof TamableAnimal wolf && wolf.isTame()
                    && wolf.getOwner() instanceof Player owner && owner.distanceTo(wolf) < 16 && ready("howl" + owner.getUUID() + Saga.day(level), level.getGameTime(), 24000)) {
                    net.minecraft.sounds.SoundEvent howl = Saga.sound("entity.wolf.howl");
                    if (howl != null) level.playSound(null, wolf.blockPosition(), howl, SoundSource.NEUTRAL, 1.4f, 0.9f);
                    for (Mob m : level.getEntitiesOfClass(Mob.class, owner.getBoundingBox().inflate(16))) {
                        if (m instanceof TamableAnimal pack && pack.isTame() && pack.getOwner() == owner && MobGroup.of(m.getType()) == MobGroup.WOLF) {
                            m.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 1200 * lvl, 0, false, true));
                            m.addEffect(new MobEffectInstance(MobEffects.SPEED, 1200 * lvl, 0, false, true));
                        }
                    }
                }
            }
            case BEE -> {
                if ((lvl = TraitTags.get(mob, "bee_guard")) > 0 && t % 20 == 0 && mob.getTarget() == null) {
                    Player p = level.getNearestPlayer(mob, 10);
                    if (p != null && p.getLastHurtByMob() instanceof Mob attacker && attacker.isAlive() && MobGroup.of(attacker.getType()) != MobGroup.BEE
                        && attacker.distanceTo(p) < 8 && level.getRandom().nextFloat() < 0.25f * lvl) mob.setTarget(attacker);
                }
            }
            case CHICKEN -> {
                if ((lvl = TraitTags.get(mob, "chicken_alarm")) > 0 && t % 40 == 0 && level.isDarkOutside()) {
                    for (Mob m : level.getEntitiesOfClass(Mob.class, mob.getBoundingBox().inflate(6 + 2 * lvl))) {
                        MobGroup mg = MobGroup.of(m.getType());
                        if (mg == null || !mg.hostile || !ready(mob, "cluck", level.getGameTime(), 200)) continue;
                        level.playSound(null, mob.blockPosition(), SoundEvents.CHICKEN_HURT_BABY.value(), SoundSource.NEUTRAL, 1.6f, 1.6f);
                        level.sendParticles(ParticleTypes.NOTE, mob.getX(), mob.getY() + 0.8, mob.getZ(), 3, 0.3, 0.2, 0.3, 1);
                        m.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60 * lvl, 0, false, false));
                        break;
                    }
                }
            }
            default -> {
            }
        }
    }

    private static void dodge(ServerLevel level, Mob mob, int lvl, double strength) {
        for (Projectile pr : level.getEntitiesOfClass(Projectile.class, mob.getBoundingBox().inflate(4 + lvl))) {
            Vec3 v = pr.getDeltaMovement();
            if (v.lengthSqr() < 0.04 || pr.onGround()) continue;
            Vec3 to = mob.position().subtract(pr.position());
            if (to.normalize().dot(v.normalize()) < 0.8) continue;
            if (level.getRandom().nextFloat() > 0.25f * lvl) return;
            Vec3 side = new Vec3(-v.z, 0, v.x).normalize().scale(strength + 0.1 * lvl);
            if (level.getRandom().nextBoolean()) side = side.scale(-1);
            mob.setDeltaMovement(mob.getDeltaMovement().add(side.x, 0.05, side.z));
            return;
        }
    }

    private static void horse(ServerLevel level, Mob horse, long t) {
        if (t % 20 != 0) return;
        if (horse.getControllingPassenger() instanceof Player rider) {
            String mine = "mobslearn.rider=" + rider.getUUID();
            if (!horse.entityTags().contains(mine)) {
                for (String tag : List.copyOf(horse.entityTags())) if (tag.startsWith("mobslearn.rider=")) horse.removeTag(tag);
                horse.addTag(mine);
            }
            return;
        }
        int lvl = TraitTags.get(horse, "horse_return");
        if (lvl <= 0 || horse.isLeashed() || horse.isVehicle()) return;
        String rider = null;
        for (String tag : horse.entityTags()) if (tag.startsWith("mobslearn.rider=")) rider = tag.substring("mobslearn.rider=".length());
        if (rider == null) return;
        Player owner;
        try {
            owner = level.getPlayerByUUID(UUID.fromString(rider));
        } catch (IllegalArgumentException e) {
            return;
        }
        if (owner == null || owner.isSpectator()) return;
        double d = horse.distanceTo(owner);
        if (d > 18 && d < 32 + 32 * lvl) horse.getNavigation().moveTo(owner, 1.2 + 0.1 * lvl);
        else if (d < 5) horse.getNavigation().stop();
    }

    private static void cat(ServerLevel level, Mob mob, long t) {
        if (!(mob instanceof TamableAnimal cat) || !cat.isTame() || !(cat.getOwner() instanceof ServerPlayer owner) || owner.level() != level) return;
        int guard = TraitTags.get(mob, "cat_guard");
        if (guard > 0 && t % 10 == 0 && owner.distanceTo(cat) < 16) {
            for (Creeper c : level.getEntitiesOfClass(Creeper.class, cat.getBoundingBox().inflate(6 + 3 * guard))) {
                if (c.getTarget() != owner) continue;
                c.setSwellDir(-1);
                Vec3 away = c.position().subtract(cat.position()).normalize().scale(6);
                c.getNavigation().moveTo(c.getX() + away.x, c.getY(), c.getZ() + away.z, 1.3);
                if (ready(cat, "hiss", level.getGameTime(), 60)) level.playSound(null, cat.blockPosition(), SoundEvents.CAT_HISS_BABY.value(), SoundSource.NEUTRAL, 1f, 1f);
            }
        }
        int alarm = TraitTags.get(mob, "cat_alarm");
        if (alarm > 0 && t % 20 == 0 && owner.isSleeping() && owner.distanceTo(cat) < 24) {
            for (Mob m : level.getEntitiesOfClass(Mob.class, owner.getBoundingBox().inflate(6 + 3 * alarm))) {
                MobGroup g = MobGroup.of(m.getType());
                if (g == null || !g.hostile || Colonies.peaceful(m)) continue;
                level.playSound(null, cat.blockPosition(), SoundEvents.CAT_HISS_BABY.value(), SoundSource.NEUTRAL, 1.5f, 1.2f);
                owner.stopSleepInBed(true, true);
                Saga.notice(owner, Component.translatable("mobslearn.wild.cat_alarm", m.getDisplayName()));
                m.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0, false, false));
                break;
            }
        }
    }

    private static void parrot(ServerLevel level, Mob mob, long t, RandomSource r) {
        int scout = TraitTags.get(mob, "parrot_scout");
        if (scout > 0 && t % 40 == 0 && mob instanceof TamableAnimal parrot && parrot.isTame() && parrot.getOwner() instanceof Player owner && owner.distanceTo(parrot) < 20) {
            for (Mob m : level.getEntitiesOfClass(Mob.class, owner.getBoundingBox().inflate(10 + 4 * scout))) {
                MobGroup g = MobGroup.of(m.getType());
                if (g == null || !g.hostile || m.hasEffect(MobEffects.GLOWING) || Colonies.peaceful(m)) continue;
                m.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60 + 20 * scout, 0, false, false));
                if (ready(parrot, "squawk", level.getGameTime(), 200)) level.playSound(null, parrot.blockPosition(), SoundEvents.PARROT_IMITATE_ZOMBIE, SoundSource.NEUTRAL, 1f, 1.3f);
            }
        }
        int thief = TraitTags.get(mob, "parrot_thief");
        if (thief > 0 && t % 20 == 0 && !mob.entityTags().contains("mobslearn.loot")) {
            for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, mob.getBoundingBox().inflate(4 + 2 * thief))) {
                ItemStack s = item.getItem();
                if (!(s.is(Items.GOLD_NUGGET) || s.is(Items.GOLD_INGOT) || s.is(Items.EMERALD) || s.is(Items.DIAMOND) || s.is(Items.IRON_NUGGET) || s.is(Items.AMETHYST_SHARD))) continue;
                ItemStack one = s.split(1);
                if (s.isEmpty()) item.discard();
                mob.setItemSlot(EquipmentSlot.MAINHAND, one);
                mob.setDropChance(EquipmentSlot.MAINHAND, 1.0f);
                mob.addTag("mobslearn.loot");
                level.playSound(null, mob.blockPosition(), SoundEvents.PARROT_AMBIENT, SoundSource.NEUTRAL, 1f, 1.5f);
                Behaviors.later(400 + r.nextInt(400), () -> {
                    if (!mob.isAlive()) return;
                    ItemStack held = mob.getItemBySlot(EquipmentSlot.MAINHAND);
                    if (!held.isEmpty()) level.addFreshEntity(new ItemEntity(level, mob.getX(), mob.getY(), mob.getZ(), held.copy()));
                    mob.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                    mob.removeTag("mobslearn.loot");
                });
                break;
            }
        }
    }

    private static void rabbitThief(ServerLevel level, Mob rabbit, int lvl, RandomSource r) {
        if (rabbit.entityTags().contains("mobslearn.loot")) return;
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, rabbit.getBoundingBox().inflate(4 + 2 * lvl))) {
            ItemStack s = item.getItem();
            if (!s.is(Items.CARROT) && !s.is(Items.GOLDEN_CARROT) && !s.is(Items.BEETROOT)) continue;
            if (rabbit.distanceTo(item) > 1.5) {
                rabbit.getNavigation().moveTo(item, 1.4);
                return;
            }
            ItemStack one = s.split(1);
            if (s.isEmpty()) item.discard();
            rabbit.addTag("mobslearn.loot");
            level.sendParticles(ParticleTypes.HEART, rabbit.getX(), rabbit.getY() + 0.5, rabbit.getZ(), 2, 0.2, 0.2, 0.2, 0);
            Vec3 away = new Vec3(r.nextGaussian(), 0, r.nextGaussian()).normalize().scale(10);
            rabbit.getNavigation().moveTo(rabbit.getX() + away.x, rabbit.getY(), rabbit.getZ() + away.z, 1.8);
            Behaviors.later(200 + r.nextInt(200), () -> {
                rabbit.removeTag("mobslearn.loot");
                if (rabbit.isAlive() && r.nextFloat() < 0.5f) level.addFreshEntity(new ItemEntity(level, rabbit.getX(), rabbit.getY(), rabbit.getZ(), one));
            });
            return;
        }
    }

    private static void curse(ServerLevel level, Mob witch, Player p, int lvl) {
        Vec3 from = witch.position().add(0, 1.6, 0), to = p.position().add(0, 1, 0);
        for (int i = 0; i <= 10; i++) {
            Vec3 at = from.lerp(to, i / 10.0);
            level.sendParticles(ParticleTypes.WITCH, at.x, at.y, at.z, 2, 0.05, 0.05, 0.05, 0);
        }
        level.playSound(null, witch.blockPosition(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.HOSTILE, 0.8f, 1.4f);
        p.addEffect(new MobEffectInstance(MobEffects.HUNGER, 160, 0), witch);
        if (lvl >= 2) p.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 160, 0), witch);
        if (lvl >= 3) p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 0), witch);
    }

    private static void familiar(ServerLevel level, Mob witch, RandomSource r) {
        String key = "mobslearn.familiar=" + witch.getUUID();
        for (Mob m : level.getEntitiesOfClass(Mob.class, witch.getBoundingBox().inflate(24))) {
            if (!m.entityTags().contains(key)) continue;
            if (m.distanceTo(witch) > 5) {
                Vec3 to = witch.position().add(0, 2.2, 0).subtract(m.position()).normalize().scale(0.3);
                m.setDeltaMovement(to);
            }
            return;
        }
        if (witch.entityTags().contains("mobslearn.had_familiar")) return;
        Mob bat = EntityTypes.BAT.create(level, EntitySpawnReason.TRIGGERED);
        if (bat == null) return;
        bat.snapTo(witch.getX(), witch.getY() + 2.2, witch.getZ(), 0f, 0f);
        bat.addTag(key);
        bat.addTag(TraitTags.SEEN);
        bat.setPersistenceRequired();
        level.addFreshEntity(bat);
        witch.addTag("mobslearn.had_familiar");
    }

    private static void merge(ServerLevel level, net.minecraft.world.entity.monster.cubemob.Slime slime, int lvl) {
        int size = slime.getSize();
        if (size >= 2 + lvl) return;
        List<net.minecraft.world.entity.monster.cubemob.Slime> twins = level.getEntitiesOfClass(net.minecraft.world.entity.monster.cubemob.Slime.class,
            slime.getBoundingBox().inflate(3), s -> s != slime && s.isAlive() && s.getSize() == size && s.getType() == slime.getType());
        if (twins.size() < 2) return;
        twins.get(0).discard();
        twins.get(1).discard();
        slime.setSize(size + 1, true);
        level.sendParticles(ParticleTypes.ITEM_SLIME, slime.getX(), slime.getY() + 0.5, slime.getZ(), 20, 0.5, 0.5, 0.5, 0.1);
        level.playSound(null, slime.blockPosition(), SoundEvents.SLIME_SQUISH, SoundSource.HOSTILE, 1f, 0.6f);
    }

    private static void horn(ServerLevel level, Mob mob, Player p, int lvl, RandomSource r) {
        level.playSound(null, mob.blockPosition(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 2.5f, 1.2f);
        int n = Math.min(lvl, 2);
        for (int i = 0; i < n; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            int x = mob.getBlockX() + (int) (Math.cos(a) * 14), z = mob.getBlockZ() + (int) (Math.sin(a) * 14);
            BlockPos at = new BlockPos(x, Camps.groundY(level, x, z), z);
            if (!level.isLoaded(at)) continue;
            Mob v = (i == 0 ? EntityTypes.VINDICATOR : EntityTypes.PILLAGER).create(level, EntitySpawnReason.REINFORCEMENT);
            if (v == null) continue;
            v.snapTo(x + 0.5, at.getY(), z + 0.5, r.nextFloat() * 360f, 0f);
            v.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(i == 0 ? Items.IRON_AXE : Items.CROSSBOW));
            level.addFreshEntity(v);
            v.setTarget(p);
        }
        if (p instanceof ServerPlayer sp) Saga.overlay(sp, Component.translatable("mobslearn.wild.horn"));
    }

    static void afterDamage(ServerLevel level, LivingEntity e, DamageSource src, float taken) {
        if (!(e instanceof Mob mob)) {
            if (e instanceof Player p && src.getEntity() instanceof Mob phantom && MobGroup.of(phantom.getType()) == MobGroup.PHANTOM) {
                int dive = TraitTags.get(phantom, "phantom_dive");
                ItemStack chest = p.getItemBySlot(EquipmentSlot.CHEST);
                if (dive >= 2 && p.isFallFlying() && chest.is(Items.ELYTRA) && chest.isDamageableItem()) {
                    chest.setDamageValue(Math.min(chest.getMaxDamage() - 1, chest.getDamageValue() + 8 * dive));
                }
            }
            return;
        }
        MobGroup g = MobGroup.of(mob.getType());
        if (g == null || !mob.isAlive()) return;
        int lvl;
        Entity attacker = src.getEntity();
        switch (g) {
            case SQUID -> {
                if ((lvl = TraitTags.get(mob, "squid_jet")) > 0 && attacker != null && mob.isInWater()) {
                    Vec3 away = mob.position().subtract(attacker.position()).normalize().scale(0.6 + 0.3 * lvl);
                    mob.setDeltaMovement(away);
                    level.sendParticles(ParticleTypes.BUBBLE, mob.getX(), mob.getY() + 0.4, mob.getZ(), 12, 0.3, 0.3, 0.3, 0.1);
                }
                if ((lvl = TraitTags.get(mob, "squid_school")) > 0 && attacker instanceof Player p) {
                    for (Mob o : level.getEntitiesOfClass(Mob.class, mob.getBoundingBox().inflate(8))) {
                        if (o == mob || MobGroup.of(o.getType()) != MobGroup.SQUID || o.distanceTo(p) > 6) continue;
                        level.sendParticles(ParticleTypes.SQUID_INK, o.getX(), o.getY() + 0.5, o.getZ(), 15, 0.4, 0.4, 0.4, 0.05);
                        if (level.getRandom().nextFloat() < 0.2f * lvl) p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0), o);
                    }
                }
            }
            case RABBIT -> {
                if ((lvl = TraitTags.get(mob, "rabbit_burrow")) > 0 && attacker instanceof Player && level.getRandom().nextFloat() < 0.3f * lvl) {
                    mob.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 40 + 20 * lvl, 0, false, false));
                    mob.addEffect(new MobEffectInstance(MobEffects.SPEED, 60 + 20 * lvl, 2, false, false));
                    level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(mob.blockPosition().below())),
                        mob.getX(), mob.getY() + 0.2, mob.getZ(), 20, 0.3, 0.1, 0.3, 0.1);
                    level.playSound(null, mob.blockPosition(), SoundEvents.ROOTED_DIRT_BREAK, SoundSource.NEUTRAL, 1f, 1.2f);
                }
            }
            case HORSE -> {
                if ((lvl = TraitTags.get(mob, "horse_dodge")) > 0 && src.is(DamageTypeTags.IS_PROJECTILE) && mob.isVehicle()) {
                    mob.heal(taken * 0.25f * lvl);
                    Vec3 look = mob.getLookAngle();
                    Vec3 side = new Vec3(-look.z, 0, look.x).normalize().scale(0.4 * (level.getRandom().nextBoolean() ? 1 : -1));
                    mob.setDeltaMovement(mob.getDeltaMovement().add(side.x, 0.1, side.z));
                }
            }
            default -> {
            }
        }
    }
}
