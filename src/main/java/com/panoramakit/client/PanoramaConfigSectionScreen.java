package com.panoramakit.client;

import java.util.Collection;
import java.util.List;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.ConfigurationScreen.ConfigurationSectionScreen;
import net.neoforged.neoforge.client.gui.ConfigurationScreen.ConfigurationSectionScreen.Element;

/**
 * NeoForge's generated config screen with one extra row that opens the panorama picker, so players
 * can switch panoramas without leaving the mod list.
 */
public final class PanoramaConfigSectionScreen extends ConfigurationSectionScreen {
    public PanoramaConfigSectionScreen(Screen parent, ModConfig.Type type, ModConfig modConfig, Component title) {
        super(parent, type, modConfig, title);
    }

    @Override
    protected Collection<? extends Element> createSyntheticValues() {
        Component tooltip = Component.translatable("panoramakit.config.choose.tooltip");
        Button openPicker = Button.builder(
                        Component.translatable("panoramakit.config.choose"),
                        button -> ClientCompat.setScreen(new PanoramaSelectScreen(this)))
                // NeoForge shows the row tooltip on the label, so the button explains itself too.
                .tooltip(Tooltip.create(tooltip))
                .width(Button.DEFAULT_WIDTH)
                .build();

        return List.of(new Element(
                Component.translatable("panoramakit.config.panorama"),
                tooltip,
                openPicker));
    }
}
