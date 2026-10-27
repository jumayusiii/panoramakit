package com.panoramakit.client;

import org.slf4j.Logger;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;

import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

import com.panoramakit.PanoramaKit;

/**
 * Client entrypoint: keybinds, config screen registration, and the title-screen shortcut.
 */
@Mod(value = PanoramaKit.MOD_ID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = PanoramaKit.MOD_ID, value = Dist.CLIENT)
public final class PanoramaKitClient {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final KeyMapping.Category KEY_CATEGORY =
            new KeyMapping.Category(Identifier.fromNamespaceAndPath(PanoramaKit.MOD_ID, "main"));

    private static final KeyMapping CAPTURE_KEY = new KeyMapping(
            "key.panoramakit.capture",
            ClientCompat.KEY_TYPE,
            InputConstants.KEY_F6,
            KEY_CATEGORY);

    private static final KeyMapping MENU_KEY = new KeyMapping(
            "key.panoramakit.menu",
            ClientCompat.KEY_TYPE,
            InputConstants.KEY_F7,
            KEY_CATEGORY);

    public PanoramaKitClient(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, PanoramaConfig.SPEC);

        modBus.addListener(this::registerKeyMappings);
        modBus.addListener(this::onConfigLoading);
        modBus.addListener(this::onConfigReloading);

        // Adds the "Choose Panorama..." row to NeoForge's generated config screen, which is opened
        // from the mod list (Mods -> Panorama Kit -> Config).
        container.registerExtensionPoint(IConfigScreenFactory.class,
                (IConfigScreenFactory) (modContainer, modListScreen) -> new ConfigurationScreen(
                        modContainer,
                        modListScreen,
                        (ConfigurationScreen screen, ModConfig.Type type, ModConfig config, Component title) ->
                                new PanoramaConfigSectionScreen(screen, type, config, title)));
    }

    private void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.registerCategory(KEY_CATEGORY);
        event.register(CAPTURE_KEY);
        event.register(MENU_KEY);
    }

    private void onConfigLoading(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == PanoramaConfig.SPEC) {
            PanoramaLibrary.markConfigDirty();
        }
    }

    private void onConfigReloading(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == PanoramaConfig.SPEC) {
            PanoramaLibrary.markConfigDirty();
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (PanoramaLibrary.consumeConfigDirty()) {
            PanoramaLibrary.request(PanoramaConfig.selectedPanorama());
        }

        PanoramaCapture.tick();

        while (CAPTURE_KEY.consumeClick()) {
            if (ClientCompat.currentScreen() == null) {
                PanoramaCapture.capture();
            }
        }
        while (MENU_KEY.consumeClick()) {
            if (ClientCompat.currentScreen() == null) {
                ClientCompat.setScreen(new PanoramaSelectScreen(null));
            }
        }
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof TitleScreen titleScreen)) {
            return;
        }

        Button button = Button.builder(
                        Component.translatable("panoramakit.button.open"),
                        pressed -> ClientCompat.setScreen(new PanoramaSelectScreen(titleScreen)))
                .bounds(6, Math.max(6, titleScreen.height - 44), 96, 18)
                .tooltip(Tooltip.create(Component.translatable("panoramakit.button.open.tooltip")))
                .build();
        event.addListener(button);
    }

    /** Applies a panorama and persists it as the configured selection. */
    public static void selectPanorama(String name) {
        PanoramaConfig.setSelectedPanorama(name);
        PanoramaLibrary.select(name);
    }

    static void onCaptureFinished(String name) {
        if (PanoramaConfig.autoSelectCaptured()) {
            selectPanorama(name);
            Minecraft minecraft = Minecraft.getInstance();
            if (PanoramaConfig.notifyOnCapture() && minecraft.player != null) {
                minecraft.player.sendSystemMessage(Component.translatable("panoramakit.message.applied", name));
            }
        }
    }

    static void notifyError(Component message) {
        Minecraft minecraft = Minecraft.getInstance();
        Component styled = message.copy().withStyle(ChatFormatting.RED);
        if (minecraft.player != null) {
            minecraft.player.sendSystemMessage(styled);
        } else {
            LOGGER.warn(styled.getString());
        }
    }
}
