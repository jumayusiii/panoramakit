package com.panoramakit.client;

import java.nio.file.Path;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.Util;

/**
 * The Minecraft 26.2 flavour of the few client APIs that changed in 26.2 and 26.3.
 *
 * <p>26.2 moved the current screen from {@code Minecraft} to {@code Gui}; 26.3 then replaced GLFW
 * with SDL and dropped {@code Util}'s open helpers.
 */
final class ClientCompat {
    /** Keyboard key type; {@code InputConstants.Type.KEYBOARD} from 26.3 on. */
    static final InputConstants.Type KEY_TYPE = InputConstants.Type.KEYSYM;

    private ClientCompat() {
    }

    static @Nullable Screen currentScreen() {
        return Minecraft.getInstance().gui.screen();
    }

    static void setScreen(@Nullable Screen screen) {
        Minecraft.getInstance().gui.setScreen(screen);
    }

    /** Opens a folder in the platform's file browser. */
    static void openFolder(Path folder) {
        Util.getPlatform().openPath(folder);
    }
}
