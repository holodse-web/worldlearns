package com.rrm.mobslearn.mob;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.rrm.mobslearn.learn.Milestones;
import com.rrm.mobslearn.learn.WorldBrain;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;

final class Frameups {
    static final String KEY = "mobslearn.frameup.banner";

    private static final class Planted {
        String dim, colony, player;
        BlockPos pos;
        long at;
    }

    private static final List<Planted> planted = new ArrayList<>();

    private Frameups() {
    }

    static void load(JsonObject o) {
        planted.clear();
        if (!o.has("planted")) return;
        for (JsonElement e : o.getAsJsonArray("planted")) {
            try {
                JsonObject j = e.getAsJsonObject();
                Planted p = new Planted();
                p.dim = j.get("dim").getAsString();
                p.colony = j.get("colony").getAsString();
                p.player = j.get("player").getAsString();
                p.pos = BlockPos.of(j.get("pos").getAsLong());
                p.at = j.get("at").getAsLong();
                planted.add(p);
            } catch (RuntimeException ignored) {
            }
        }
    }

    static JsonObject save() {
        JsonObject o = new JsonObject();
        JsonArray arr = new JsonArray();
        for (Planted p : planted) {
            JsonObject j = new JsonObject();
            j.addProperty("dim", p.dim);
            j.addProperty("colony", p.colony);
            j.addProperty("player", p.player);
            j.addProperty("pos", p.pos.asLong());
            j.addProperty("at", p.at);
            arr.add(j);
        }
        o.add("planted", arr);
        return o;
    }

    private static Item bannerItem(String kind) {
        return switch (kind) {
            case "skeleton" -> Items.BANNER.lightGray();
            case "creeper" -> Items.BANNER.lime();
            case "pillager" -> Items.BANNER.gray();
            case "witch" -> Items.BANNER.purple();
            case "nether" -> Items.BANNER.red();
            case "enderman" -> Items.BANNER.black();
            default -> Items.BANNER.green();
        };
    }

    static ItemStack bannerFor(String colony, String kind) {
        ItemStack s = new ItemStack(bannerItem(kind));
        Saga.name(s, Component.translatable(KEY, Component.translatable("mobslearn.settlements.kind." + kind), Component.literal(colony)));
        return s;
    }

    static String colonyOf(ItemStack s) {
        if (s.isEmpty()) return null;
        Component name = s.getHoverName();
        if (!(name.getContents() instanceof TranslatableContents tc) || !tc.getKey().equals(KEY) || tc.getArgs().length < 2) return null;
        Object arg = tc.getArgs()[1];
        return arg instanceof Component c ? c.getString() : String.valueOf(arg);
    }

    static void useBlock(ServerLevel level, ServerPlayer p, InteractionHand hand, BlockHitResult hit) {
        ItemStack held = p.getItemInHand(hand);
        String colony = colonyOf(held);
        if (colony == null) return;
        BlockPos at = hit.getBlockPos().relative(hit.getDirection());
        Behaviors.later(2, () -> {
            if (!level.getBlockState(at).is(BlockTags.BANNERS)) return;
            Planted pl = new Planted();
            pl.dim = Saga.dimId(level);
            pl.colony = colony;
            pl.player = p.getUUID().toString();
            pl.pos = at.immutable();
            pl.at = level.getGameTime();
            planted.add(pl);
            Saga.markDirty();
            Colonies.View victim = victimNear(level, pl);
            if (victim != null) Saga.overlay(p, Component.translatable("mobslearn.frameup.planted", Colonies.kindNameOf(victim.id())));
        });
    }

    private static Colonies.View victimNear(ServerLevel level, Planted pl) {
        Colonies.View best = null;
        double bd = 44 * 44;
        for (Colonies.View v : Colonies.views(level)) {
            if (!v.hostile() || Colonies.sameSettlement(v.id(), pl.colony)) continue;
            double d = v.center().distSqr(pl.pos);
            if (d < bd) {
                bd = d;
                best = v;
            }
        }
        return best;
    }

    static void tick(ServerLevel level, long t) {
        if (t % 100 != 0 || planted.isEmpty()) return;
        String dim = Saga.dimId(level);
        for (Iterator<Planted> it = planted.iterator(); it.hasNext(); ) {
            Planted pl = it.next();
            if (!pl.dim.equals(dim) || !level.isLoaded(pl.pos)) continue;
            if (!level.getBlockState(pl.pos).is(BlockTags.BANNERS) || Colonies.viewById(pl.colony) == null) {
                it.remove();
                Saga.markDirty();
                continue;
            }
            Colonies.View victim = victimNear(level, pl);
            if (victim == null) {
                if (level.getGameTime() - pl.at > 24000) it.remove();
                continue;
            }
            if (level.getGameTime() - pl.at < 400) continue;
            it.remove();
            Saga.markDirty();
            level.destroyBlock(pl.pos, false);
            level.sendParticles(ParticleTypes.ANGRY_VILLAGER, pl.pos.getX() + 0.5, pl.pos.getY() + 1.2, pl.pos.getZ() + 0.5, 10, 0.5, 0.5, 0.5, 0);
            level.playSound(null, pl.pos, SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 2f, 1.3f);
            ServerPlayer planter = level.getServer().getPlayerList().getPlayer(UUID.fromString(pl.player));
            boolean war = Colonies.declareWar(level, victim.id(), pl.colony);
            if (planter == null) continue;
            if (war) {
                Saga.notice(planter, Component.translatable("mobslearn.frameup.war", Colonies.kindNameOf(victim.id()), Colonies.kindNameOf(pl.colony)));
                Milestones.award(planter, "frame_up");
                WorldBrain.chronicle(Component.translatable("mobslearn.frameup.chronicle", Colonies.kindNameOf(victim.id()), Colonies.kindNameOf(pl.colony)), "white_banner");
            } else {
                Saga.notice(planter, Component.translatable("mobslearn.frameup.already", Colonies.kindNameOf(victim.id()), Colonies.kindNameOf(pl.colony)));
            }
        }
    }

    static void onDeath(ServerLevel level, LivingEntity e, DamageSource src) {
        if (!(e instanceof Mob m) || !(src.getEntity() instanceof ServerPlayer)) return;
        Colonies.View home = Colonies.viewOf(m);
        if (home == null || !home.hostile()) return;
        boolean chief = Colonies.isChief(m);
        if (!chief && level.getRandom().nextFloat() > 0.06f) return;
        level.addFreshEntity(new ItemEntity(level, e.getX(), e.getY() + 0.5, e.getZ(), bannerFor(home.id(), home.kind())));
    }
}
