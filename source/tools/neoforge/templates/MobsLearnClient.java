package com.rrm.mobslearn.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.rrm.mobslearn.MobsLearn;
import com.rrm.mobslearn.client.wiki.WikiScreen;
import com.rrm.mobslearn.learn.Skill;
import com.rrm.mobslearn.net.Net;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import @BUS_IMPORT@;
import @LOADER@.client.event.ClientPlayerNetworkEvent;
import @LOADER@.client.event.RegisterKeyMappingsEvent;
import @LOADER@.client.event.RenderGuiEvent;
import @LOADER@.client.event.ScreenEvent;
@CLIENT_TICK_IMPORT@
@CLIENT_PAYLOAD_IMPORT@

public final class MobsLearnClient {
    public static final @CATEGORY_TYPE@ CATEGORY = @CATEGORY_VALUE@;
    public static KeyMapping treeKey;
    private static Net.AskPayload pendingAsk;

    private MobsLearnClient() {}

    public static void register(IEventBus modBus, IEventBus gameBus) {
        ModLanguage.initialize();
        modBus.addListener(MobsLearnClient::registerKeys);
        @CLIENT_PAYLOAD_LISTENER@
        gameBus.addListener(MobsLearnClient::logout);
        gameBus.addListener(MobsLearnClient::renderHud);
        gameBus.addListener(MobsLearnClient::tick);
        gameBus.addListener(MobsLearnClient::screen);
    }

    private static void registerKeys(RegisterKeyMappingsEvent event) {
        @CATEGORY_REGISTRATION@
        treeKey = new KeyMapping("key.mobslearn.tree", InputConstants.Type.@KEY_TYPE@, InputConstants.KEY_K, CATEGORY);
        event.register(treeKey);
    }

    @CLIENT_PAYLOAD_REGISTRATION@

    public static void handle(Net.SyncPayload payload) {
        ClientKnowledge.update(payload.json());
    }

    public static void handle(Net.LearnPayload payload) {
        Skill s = Skill.byId(payload.skill());
        if (s != null) BannerHud.push(s, payload.level());
    }

    public static void handle(Net.NoticePayload payload) {
        NoticeHud.push(payload.text());
    }

    public static void handle(Net.AskPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.@GET_SCREEN@ == null) mc.@SET_SCREEN@(new AskScreen(payload.villager(), payload.topic(), payload.options()));
        else pendingAsk = payload;
    }

    public static void handle(Net.ResultPayload payload) {
        if (Minecraft.getInstance().@GET_SCREEN@ instanceof AskScreen ask) ask.result(payload.correct());
    }

    public static void handle(Net.TradeOpenPayload payload) {
        Minecraft.getInstance().@SET_SCREEN@(new ChiefTradeScreen(payload.chief(), payload.kind(), payload.offers()));
    }

    public static void handle(Net.ColoniesPayload payload) {
        SettlementsScreen.update(payload.json());
    }

    private static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        pendingAsk = null;
        ClientKnowledge.clear();
        BannerHud.clear();
        NoticeHud.clear();
    }

    private static void renderHud(RenderGuiEvent.Post event) {
        BannerHud.extract(event.getGuiGraphics(), event.getPartialTick());
        NoticeHud.extract(event.getGuiGraphics(), event.getPartialTick());
    }

    private static void tick(@CLIENT_TICK_TYPE@ event) {
        @END_TICK_CHECK@
        Minecraft mc = Minecraft.getInstance();
        if (pendingAsk != null && mc.@GET_SCREEN@ == null && mc.player != null) {
            Net.AskPayload ask = pendingAsk;
            pendingAsk = null;
            mc.@SET_SCREEN@(new AskScreen(ask.villager(), ask.topic(), ask.options()));
        }
        while (treeKey != null && treeKey.consumeClick()) {
            if (mc.@GET_SCREEN@ == null && mc.player != null) mc.@SET_SCREEN@(new KnowledgeScreen(null));
        }
    }

    private static void screen(ScreenEvent.Init.Post event) {
        Minecraft mc = Minecraft.getInstance();
        var screen = event.getScreen();
        if (screen instanceof TitleScreen) {
            event.addListener(new LogoButton(6, @FML@.ModList.get().isLoaded("cati") ? 54 : 6, 104, 44,
                () -> mc.@SET_SCREEN@(new WikiScreen(screen))));
        }
        if (screen instanceof PauseScreen) {
            event.addListener(new MlButton(6, screen.height - 26, 130, 20, Component.translatable("mobslearn.pause.button"),
                b -> mc.@SET_SCREEN@(new KnowledgeScreen(screen))));
        }
    }
}
