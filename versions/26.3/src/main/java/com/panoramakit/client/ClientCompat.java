package com.panoramakit.client;

import java.io.IOException;
import java.nio.file.Path;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.Util;

/**
 * The Minecraft 26.3 flavour of the few client APIs that changed in 26.2 and 26.3.
 *
 * <p>26.3 replaced GLFW with SDL, so the keyboard key type is {@code KEYBOARD}, and {@code Util}'s
 * {@code openPath}/{@code openUri} helpers are gone, so the folder is opened directly.
 */
final class ClientCompat {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Keyboard key type; {@code InputConstants.Type.KEYSYM} in 26.1 and 26.2. */
    static final InputConstants.Type KEY_TYPE = InputConstants.Type.KEYBOARD;

    private ClientCompat() {
    }

    static @Nullable Screen currentScreen() {
        return Minecraft.getInstance().gui.screen();
    }

    static void setScreen(@Nullable Screen screen) {
        Minecraft.getInstance().gui.setScreen(screen);
    }

    /**
     * Opens a folder in the platform's file browser. Minecraft 26.1 and 26.2 do this through
     * {@code Util.OS#openPath}; 26.3 dropped those helpers, so the same commands are started here.
     */
    static void openFolder(Path folder) {
        String uri = folder.toUri().toString();
        String[] command = switch (Util.getPlatform()) {
            case WINDOWS -> new String[] { "rundll32", "url.dll,FileProtocolHandler", uri };
            case OSX -> new String[] { "open", uri };
            default -> new String[] { "xdg-open", uri };
        };

        try {
            Process process = Runtime.getRuntime().exec(command);
            process.getInputStream().close();
            process.getErrorStream().close();
            process.getOutputStream().close();
        } catch (IOException e) {
            LOGGER.warn("Could not open {}", folder, e);
        }
    }
}
