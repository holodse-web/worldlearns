package com.rrm.mobslearn;

import com.rrm.mobslearn.learn.Learning;
import com.rrm.mobslearn.learn.WorldBrain;
import com.rrm.mobslearn.mob.Behaviors;
import com.rrm.mobslearn.mob.Traits;
import com.rrm.mobslearn.net.Net;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MobsLearn implements ModInitializer {
    public static final String MOD_ID = "mobslearn";
    public static final Logger LOG = LoggerFactory.getLogger("MobsLearn");

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        MobsLearnRules.register();
        Net.register();

        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            Behaviors.reset();
            WorldBrain.start(server);
            com.rrm.mobslearn.mob.Camps.start(server);
            com.rrm.mobslearn.mob.Colonies.start(server);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            com.rrm.mobslearn.mob.Colonies.stop();
            com.rrm.mobslearn.mob.Camps.stop();
            WorldBrain.stop();
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            WorldBrain.syncTo(handler.player);
            com.rrm.mobslearn.learn.Milestones.checkWorld(handler.player);
        });

        net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents.CHUNK_LOAD.register((level, chunk, generated) -> com.rrm.mobslearn.mob.Colonies.chunkLoaded(level, chunk, generated));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            WorldBrain.serverTick();
            if (WorldBrain.get().enabled) Behaviors.serverTick(server);
        });
        ServerTickEvents.END_LEVEL_TICK.register(level -> {
            if (WorldBrain.get().enabled) Behaviors.levelTick(level);
        });

        ServerEntityEvents.ENTITY_LOAD.register(Traits::onLoad);
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
            if (WorldBrain.get().enabled) Behaviors.onUnload(entity, level);
        });

        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!WorldBrain.get().enabled) return true;
            Learning.onDamage(entity, source);
            return Behaviors.allowDamage(entity, source, amount);
        });
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
            if (WorldBrain.get().enabled) Behaviors.afterDamage(entity, source, base, taken, blocked);
        });
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> !WorldBrain.get().enabled || com.rrm.mobslearn.mob.Critters.allowDeath(entity, source));
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!WorldBrain.get().enabled) return;
            Learning.onDeath(entity, source);
            Behaviors.onDeath(entity, source);
        });

        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> com.rrm.mobslearn.mob.VillageDefense.interact(player, world, hand, entity));
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> com.rrm.mobslearn.mob.Colonies.interact(player, world, hand, entity));
        UseBlockCallback.EVENT.register(com.rrm.mobslearn.mob.Colonies::chestOpened);
        UseEntityCallback.EVENT.register(Learning::useEntity);
        UseBlockCallback.EVENT.register(Learning::useBlock);
        net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, be) -> {
            Learning.afterBreak(world, player, pos);
            if (WorldBrain.get().enabled) com.rrm.mobslearn.mob.Colonies.blockBroken(world, player, pos, state);
        });
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            if (WorldBrain.get().enabled) Behaviors.onChat(message, sender);
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, ctx, selection) -> MobsLearnCommands.register(dispatcher));
        LOG.info("[MobsLearn] World Learns by RRM Network loaded");
    }
}
