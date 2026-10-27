package com.panoramakit.client;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client configuration. The active panorama is stored here so it can be changed from the config
 * file, NeoForge's config screen, or the in-game panorama picker.
 */
public final class PanoramaConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.ConfigValue<String> SELECTED_PANORAMA = BUILDER
            .comment("Which panorama is shown behind the main menu.",
                    "\"vanilla\" keeps the default Minecraft panorama.",
                    "Any other value must match a folder name inside screenshots/panoramas.",
                    "Easier to change in game: press the Choose... button above, click Panoramas on the",
                    "title screen, or press F7 while playing. The change applies on the next frame.")
            .define("selectedPanorama", PanoramaLibrary.VANILLA);

    public static final ModConfigSpec.BooleanValue NOTIFY_ON_CAPTURE = BUILDER
            .comment("Show a chat message when a panorama capture starts and finishes.",
                    "Failures are always reported in chat, whatever this is set to.")
            .define("notifyOnCapture", true);

    public static final ModConfigSpec.BooleanValue AUTO_SELECT_CAPTURED = BUILDER
            .comment("Use a newly captured panorama behind the main menu right away.",
                    "Turn this off to keep the current panorama and pick the new capture yourself",
                    "in the picker (title screen Panoramas button, or F7 while playing).")
            .define("autoSelectCaptured", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private PanoramaConfig() {
        throw new UnsupportedOperationException();
    }

    /**
     * @return the configured panorama key; {@code "vanilla"} is used when the config is not loaded yet.
     */
    public static String selectedPanorama() {
        try {
            return SELECTED_PANORAMA.get();
        } catch (IllegalStateException e) {
            // The client config is not loaded yet (very early startup).
            return PanoramaLibrary.VANILLA;
        }
    }

    public static boolean notifyOnCapture() {
        try {
            return NOTIFY_ON_CAPTURE.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    public static boolean autoSelectCaptured() {
        try {
            return AUTO_SELECT_CAPTURED.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    /**
     * Updates and saves the active panorama. Saving fires {@code ModConfigEvent.Reloading}, which
     * makes the client apply the new value even if this is called from the config UI.
     */
    public static void setSelectedPanorama(String name) {
        try {
            if (!name.equals(SELECTED_PANORAMA.get())) {
                SELECTED_PANORAMA.set(name);
                SPEC.save();
            }
        } catch (IllegalStateException e) {
            // Not loaded yet; the value will be picked up from the config on first apply.
        }
    }
}
