package com.rrm.mobslearn.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.rrm.mobslearn.MobsLearn;
import com.rrm.mobslearn.learn.Skill;
import com.rrm.mobslearn.net.Net;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import com.rrm.mobslearn.client.wiki.WikiScreen;
import net.minecraft.network.chat.Component;

public class MobsLearnClient implements ClientModInitializer {
    public static final String CATEGORY = "key.category.mobslearn.main";
    public static KeyMapping treeKey;
    private static Net.AskPayload pendingAsk;

    @Override
    public void onInitializeClient() {
        ModLanguage.initialize();
        treeKey = KeyBindingHelper.registerKeyBinding(
            new KeyMapping("key.mobslearn.tree", InputConstants.Type.KEYSYM, InputConstants.KEY_K, CATEGORY));

        com.rrm.mobslearn.client.CNet.registerGlobalReceiver(Net.SyncPayload.TYPE, (payload, ctx) -> ClientKnowledge.update(payload.json()));
        com.rrm.mobslearn.client.CNet.registerGlobalReceiver(Net.LearnPayload.TYPE, (payload, ctx) -> {
            Skill s = Skill.byId(payload.skill());
            if (s != null) BannerHud.push(s, payload.level());
        });
        com.rrm.mobslearn.client.CNet.registerGlobalReceiver(Net.NoticePayload.TYPE, (payload, ctx) -> NoticeHud.push(payload.text()));
        com.rrm.mobslearn.client.CNet.registerGlobalReceiver(Net.AskPayload.TYPE, (payload, ctx) -> {
            if (ctx.client().screen == null) ctx.client().setScreen(new AskScreen(payload.villager(), payload.topic(), payload.options()));
            else pendingAsk = payload;
        });
        com.rrm.mobslearn.client.CNet.registerGlobalReceiver(Net.ResultPayload.TYPE, (payload, ctx) -> {
            if (ctx.client().screen instanceof AskScreen ask) ask.result(payload.correct());
        });
        com.rrm.mobslearn.client.CNet.registerGlobalReceiver(Net.TradeOpenPayload.TYPE, (payload, ctx) ->
            ctx.client().setScreen(new ChiefTradeScreen(payload.chief(), payload.kind(), payload.offers())));
        com.rrm.mobslearn.client.CNet.registerGlobalReceiver(Net.ColoniesPayload.TYPE, (payload, ctx) -> SettlementsScreen.update(payload.json()));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            pendingAsk = null;
            ClientKnowledge.clear();
            BannerHud.clear();
            NoticeHud.clear();
        });

        HudRenderCallback.EVENT.register(BannerHud::extract);
        HudRenderCallback.EVENT.register(NoticeHud::extract);

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (pendingAsk != null && mc.screen == null && mc.player != null) {
                Net.AskPayload ask = pendingAsk;
                pendingAsk = null;
                mc.setScreen(new AskScreen(ask.villager(), ask.topic(), ask.options()));
            }
            while (treeKey.consumeClick()) {
                if (mc.screen == null && mc.player != null) mc.setScreen(new KnowledgeScreen(null));
            }
        });

        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (screen instanceof TitleScreen) {
                Screens.getButtons(screen).add(new LogoButton(6, net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("cati") ? 54 : 6, 104, 44,
                    () -> client.setScreen(new WikiScreen(screen))));
            }
            if (screen instanceof PauseScreen) {
                Screens.getButtons(screen).add(new MlButton(6, h - 26, 130, 20, Component.translatable("mobslearn.pause.button"),
                    b -> client.setScreen(new KnowledgeScreen(screen))));
            }
        });
    }
}
