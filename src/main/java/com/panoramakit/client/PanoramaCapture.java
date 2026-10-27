package com.panoramakit.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Captures a vanilla-style panorama.
 *
 * <p>Vanilla's own panorama grabber is reused so the output matches the main menu panorama exactly
 * (six cube faces, 85 degree FOV, rendered at 4096x4096 and downscaled to 1024x1024). Vanilla's
 * grabber writes into a {@code screenshots} subfolder of the directory it receives, so the files
 * are polled for completion and then moved up into the panorama folder.
 */
public final class PanoramaCapture {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final DateTimeFormatter FOLDER_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss", Locale.ROOT);
    private static final int FACE_COUNT = 6;
    private static final int POLL_TIMEOUT_TICKS = 400;
    private static final int STABLE_CHECKS_REQUIRED = 5;

    private static @Nullable Path pendingFolder;
    private static int waitedTicks;
    private static long previousSizes = -1L;
    private static int stableChecks;

    private PanoramaCapture() {
        throw new UnsupportedOperationException();
    }

    public static void capture() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || pendingFolder != null) {
            return;
        }

        Path root = PanoramaLibrary.panoramasDirectory();
        String baseName = "panorama_" + LocalDateTime.now().format(FOLDER_FORMAT);
        Path folder = uniqueFolder(root, baseName);

        try {
            Files.createDirectories(folder);
        } catch (IOException e) {
            LOGGER.error("Could not create panorama folder {}", folder, e);
            PanoramaKitClient.notifyError(Component.translatable("panoramakit.message.capture_failed"));
            return;
        }

        LOGGER.info("Capturing panorama into {}", folder);
        Component result;
        try {
            result = minecraft.grabPanoramixScreenshot(folder.toFile());
        } catch (Throwable t) {
            LOGGER.error("Panorama capture failed", t);
            PanoramaKitClient.notifyError(Component.translatable("panoramakit.message.capture_failed"));
            return;
        }
        if (PanoramaConfig.notifyOnCapture() && minecraft.player != null) {
            minecraft.player.sendSystemMessage(result);
        }

        pendingFolder = folder;
        waitedTicks = 0;
        previousSizes = -1L;
        stableChecks = 0;
    }

    public static void tick() {
        if (pendingFolder == null) {
            return;
        }

        if (++waitedTicks < 5) {
            return;
        }

        long sizes = totalFaceSize(pendingFolder);
        if (sizes < 0) {
            if (waitedTicks > POLL_TIMEOUT_TICKS) {
                LOGGER.warn("Timed out waiting for the panorama capture in {}", pendingFolder);
                pendingFolder = null;
            }
            return;
        }

        if (sizes == previousSizes) {
            if (++stableChecks >= STABLE_CHECKS_REQUIRED) {
                finish();
            }
        } else {
            previousSizes = sizes;
            stableChecks = 0;
        }
    }

    private static void finish() {
        Path folder = pendingFolder;
        pendingFolder = null;
        if (folder == null) {
            return;
        }

        flatten(folder);
        String name = folder.getFileName().toString();
        LOGGER.info("Panorama capture finished: {}", name);
        PanoramaKitClient.onCaptureFinished(name);
    }

    /**
     * Moves faces out of the nested {@code screenshots} directory vanilla creates. If this fails,
     * the loader still finds the files in both locations.
     */
    private static void flatten(Path folder) {
        Path nested = folder.resolve("screenshots");
        if (!Files.isDirectory(nested)) {
            return;
        }

        try {
            for (int face = 0; face < FACE_COUNT; face++) {
                Path source = nested.resolve("panorama_" + face + ".png");
                if (Files.isRegularFile(source)) {
                    Files.move(source, folder.resolve(source.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                }
            }
            Files.deleteIfExists(nested);
        } catch (IOException e) {
            LOGGER.warn("Could not move panorama faces out of {}", nested, e);
        }
    }

    private static long totalFaceSize(Path folder) {
        long total = 0L;
        for (int face = 0; face < FACE_COUNT; face++) {
            Path file = PanoramaLibrary.facePath(folder, face);
            if (!Files.isRegularFile(file)) {
                return -1L;
            }
            try {
                long size = Files.size(file);
                if (size <= 0L) {
                    return -1L;
                }
                total += size;
            } catch (IOException e) {
                return -1L;
            }
        }
        return total;
    }

    private static Path uniqueFolder(Path root, String baseName) {
        Path candidate = root.resolve(baseName);
        int suffix = 2;
        while (Files.exists(candidate)) {
            candidate = root.resolve(baseName + "_" + suffix++);
        }
        return candidate;
    }
}
