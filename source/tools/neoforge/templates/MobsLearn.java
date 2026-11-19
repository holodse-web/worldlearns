package com.rrm.mobslearn;

import com.rrm.mobslearn.learn.Learning;
import com.rrm.mobslearn.learn.Milestones;
import com.rrm.mobslearn.learn.WorldBrain;
import com.rrm.mobslearn.mob.Behaviors;
import com.rrm.mobslearn.mob.Camps;
import com.rrm.mobslearn.mob.Colonies;
import com.rrm.mobslearn.mob.Critters;
import com.rrm.mobslearn.mob.NewSkills;
import com.rrm.mobslearn.mob.Traits;
import com.rrm.mobslearn.mob.VillageDefense;
import com.rrm.mobslearn.net.Net;
import com.rrm.mobslearn.util.Perf;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.resources.@IDENTIFIER@;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
@CHUNK_IMPORT@
import @MOD_IMPORT@;
import @BUS_IMPORT@;
import @LOADER@.event.RegisterCommandsEvent;
import @LOADER@.event.ServerChatEvent;
import @LOADER@.event.entity.EntityJoinLevelEvent;
import @LOADER@.event.entity.EntityLeaveLevelEvent;
import @LOADER@.event.entity.living.@INCOMING_DAMAGE@;
import @LOADER@.event.entity.living.LivingDamageEvent;
import @LOADER@.event.entity.living.LivingDeathEvent;
import @LOADER@.event.entity.player.PlayerEvent;
import @LOADER@.event.entity.player.PlayerInteractEvent;
import @BREAK_EVENT_IMPORT@;
import @LOADER@.event.level.ChunkEvent;
import @LOADER@.event.server.ServerAboutToStartEvent;
import @LOADER@.event.server.ServerStoppingEvent;
@TICK_IMPORTS@
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(MobsLearn.MOD_ID)
public class MobsLearn {
    public static final String MOD_ID = "mobslearn";
    public static final Logger LOG = LoggerFactory.getLogger("MobsLearn");
    private static final ConcurrentLinkedQueue<Runnable> levelTasks = new ConcurrentLinkedQueue<>();

    public static @IDENTIFIER@ id(String path) {
        return @MAKE_ID@;
    }

    public MobsLearn(@CONSTRUCTOR_ARG@) {
        @CONSTRUCTOR_BODY@
        @RULE_REGISTRATION@
        @NET_REGISTRATION@
        var bus = @EVENT_BUS@;
        bus.addListener(MobsLearn::start);
        bus.addListener(MobsLearn::stop);
        bus.addListener(MobsLearn::join);
        bus.addListener(MobsLearn::loadChunk);
        bus.addListener(MobsLearn::serverTick);
        bus.addListener(MobsLearn::levelTick);
        bus.addListener(MobsLearn::loadEntity);
        bus.addListener(MobsLearn::unloadEntity);
        bus.addListener(MobsLearn::incomingDamage);
        bus.addListener(MobsLearn::damage);
        bus.addListener(MobsLearn::death);
        bus.addListener(MobsLearn::interact);
        bus.addListener(MobsLearn::useBlock);
        bus.addListener(MobsLearn::breakBlock);
        bus.addListener(MobsLearn::chat);
        bus.addListener(MobsLearn::commands);
        @CLIENT_REGISTRATION@
    }

    private static void start(ServerAboutToStartEvent event) {
        MinecraftServer server = event.getServer();
        levelTasks.clear();
        Behaviors.reset();
        WorldBrain.start(server);
        Camps.start(server);
        Colonies.start(server);
    }

    private static void stop(ServerStoppingEvent event) {
        levelTasks.clear();
        Colonies.stop();
        Camps.stop();
        WorldBrain.stop();
    }

    private static void join(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            WorldBrain.syncTo(player);
            Milestones.checkWorld(player);
        }
    }

    private static void loadChunk(ChunkEvent.Load event) {
        if (!event.isNewChunk() || !(event.getLevel() instanceof ServerLevel level)) return;
        @LOAD_CHUNK@
    }

    private static void serverTick(@SERVER_TICK_TYPE@ event) {
        @END_TICK_CHECK@
        long started = Perf.start();
        WorldBrain.serverTick();
        for (int i = 0; i < 256; i++) {
            Runnable task = levelTasks.poll();
            if (task == null) break;
            task.run();
        }
        if (WorldBrain.get().enabled) Behaviors.serverTick(event.getServer());
        Perf.stop("core", started);
        Perf.endTick();
    }

    private static void levelTick(@LEVEL_TICK_TYPE@ event) {
        @END_TICK_CHECK@
        if (@TICK_LEVEL@ instanceof ServerLevel level && WorldBrain.get().enabled) Behaviors.levelTick(level);
    }

    private static void loadEntity(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        var entity = event.getEntity();
        if (!(entity instanceof Mob) && !(entity instanceof Projectile)) return;
        levelTasks.add(() -> {
            if (entity.isRemoved() || entity.level() != level) return;
            Traits.onLoad(entity, level);
            if (WorldBrain.get().enabled) NewSkills.onEntityLoad(entity, level);
        });
    }

    private static void unloadEntity(EntityLeaveLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level && WorldBrain.get().enabled) {
            Behaviors.onUnload(event.getEntity(), level);
        }
    }

    private static void incomingDamage(@INCOMING_DAMAGE@ event) {
        var entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel) || !WorldBrain.get().enabled) return;
        Learning.onDamage(entity, event.getSource());
        if (!Behaviors.allowDamage(entity, event.getSource(), event.getAmount())) event.setCanceled(true);
    }

    private static void damage(LivingDamageEvent@DAMAGE_TYPE@ event) {
        if (event.getEntity().level() instanceof ServerLevel && WorldBrain.get().enabled) {
            Behaviors.afterDamage(event.getEntity(), event.getSource(), @DAMAGE_AMOUNTS@);
        }
    }

    private static void death(LivingDeathEvent event) {
        var entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel) || !WorldBrain.get().enabled) return;
        if (!Critters.allowDeath(entity, event.getSource())) {
            event.setCanceled(true);
            return;
        }
        Learning.onDeath(entity, event.getSource());
        Behaviors.onDeath(entity, event.getSource());
    }

    private static void interact(PlayerInteractEvent.EntityInteract event) {
        var player = event.getEntity();
        var level = event.getLevel();
        var hand = event.getHand();
        var target = event.getTarget();
        var result = VillageDefense.interact(player, level, hand, target);
        if (result == InteractionResult.PASS) result = Colonies.interact(player, level, hand, target);
        if (result == InteractionResult.PASS) result = NewSkills.interact(player, level, hand, target);
        if (result == InteractionResult.PASS) result = Learning.useEntity(player, level, hand, target, null);
        if (result != InteractionResult.PASS) {
            event.setCancellationResult(result);
            event.setCanceled(true);
        }
    }

    private static void useBlock(PlayerInteractEvent.RightClickBlock event) {
        var player = event.getEntity();
        var level = event.getLevel();
        var hand = event.getHand();
        var hit = event.getHitVec();
        var result = Colonies.chestOpened(player, level, hand, hit);
        if (result == InteractionResult.PASS) result = Learning.useBlock(player, level, hand, hit);
        if (result != InteractionResult.PASS) {
            event.setCancellationResult(result);
            event.setCanceled(true);
        }
    }

    private static void breakBlock(@BREAK_EVENT@ event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !WorldBrain.get().enabled) return;
        var player = event.getPlayer();
        var pos = event.getPos();
        var state = event.getState();
        Behaviors.later(1, () -> {
            if (level.getBlockState(pos) == state) return;
            Learning.afterBreak(level, player, pos);
            if (WorldBrain.get().enabled) Colonies.blockBroken(level, player, pos, state);
        });
    }

    private static void chat(ServerChatEvent event) {
        if (WorldBrain.get().enabled) {
            var player = event.getPlayer();
            Behaviors.onChat(@CHAT_MESSAGE@, player);
        }
    }

    private static void commands(RegisterCommandsEvent event) {
        MobsLearnCommands.register(event.getDispatcher());
    }
}
