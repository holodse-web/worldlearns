package com.rrm.mobslearn.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.rrm.mobslearn.MobsLearn;
import com.rrm.mobslearn.learn.Skill;
import com.rrm.mobslearn.net.Net;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import com.rrm.mobslearn.client.wiki.WikiScreen;
import net.minecraft.network.chat.Component;

public class MobsLearnClient implements ClientModInitializer {
    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(MobsLearn.id("main"));
    public static KeyMapping treeKey;
    private static Net.AskPayload pendingAsk;

    @Override
    public void onInitializeClient() {
        ModLanguage.initialize();
        treeKey = KeyMappingHelper.registerKeyMapping(
            new KeyMapping("key.mobslearn.tree", InputConstants.Type.KEYBOARD, InputConstants.KEY_K, CATEGORY));

        ClientPlayNetworking.registerGlobalReceiver(Net.SyncPayload.TYPE, (payload, ctx) -> ClientKnowledge.update(payload.json()));
        ClientPlayNetworking.registerGlobalReceiver(Net.LearnPayload.TYPE, (payload, ctx) -> {
            Skill s = Skill.byId(payload.skill());
            if (s != null) BannerHud.push(s, payload.level());
        });
        ClientPlayNetworking.registerGlobalReceiver(Net.NoticePayload.TYPE, (payload, ctx) -> NoticeHud.push(payload.text()));
        ClientPlayNetworking.registerGlobalReceiver(Net.AskPayload.TYPE, (payload, ctx) -> {
            if (ctx.client().gui.screen() == null) ctx.client().gui.setScreen(new AskScreen(payload.villager(), payload.topic(), payload.options()));
            else pendingAsk = payload;
        });
        ClientPlayNetworking.registerGlobalReceiver(Net.ResultPayload.TYPE, (payload, ctx) -> {
            if (ctx.client().gui.screen() instanceof AskScreen ask) ask.result(payload.correct());
        });
        ClientPlayNetworking.registerGlobalReceiver(Net.TradeOpenPayload.TYPE, (payload, ctx) ->
            ctx.client().gui.setScreen(new ChiefTradeScreen(payload.chief(), payload.kind(), payload.offers())));
        ClientPlayNetworking.registerGlobalReceiver(Net.ColoniesPayload.TYPE, (payload, ctx) -> SettlementsScreen.update(payload.json()));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            pendingAsk = null;
            ClientKnowledge.clear();
            BannerHud.clear();
            NoticeHud.clear();
        });

        HudElementRegistry.addLast(MobsLearn.id("banner"), BannerHud::extract);
        HudElementRegistry.addLast(MobsLearn.id("notice"), NoticeHud::extract);

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (pendingAsk != null && mc.gui.screen() == null && mc.player != null) {
                Net.AskPayload ask = pendingAsk;
                pendingAsk = null;
                mc.gui.setScreen(new AskScreen(ask.villager(), ask.topic(), ask.options()));
            }
            while (treeKey.consumeClick()) {
                if (mc.gui.screen() == null && mc.player != null) mc.gui.setScreen(new KnowledgeScreen(null));
            }
        });

        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (screen instanceof TitleScreen) {
                Screens.getWidgets(screen).add(new LogoButton(6, net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("cati") ? 54 : 6, 104, 44,
                    () -> client.gui.setScreen(new WikiScreen(screen))));
            }
            if (screen instanceof PauseScreen) {
                Screens.getWidgets(screen).add(new MlButton(6, h - 26, 130, 20, Component.translatable("mobslearn.pause.button"),
                    b -> client.gui.setScreen(new KnowledgeScreen(screen))));
            }
        });
    }
}
