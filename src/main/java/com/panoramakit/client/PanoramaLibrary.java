package com.panoramakit.client;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.slf4j.Logger;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.CubeMapTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

/**
 * Finds captured panoramas and swaps the texture used by the main menu.
 *
 * <p>The title screen renders its background from the texture registered at
 * {@code minecraft:textures/gui/title/background/panorama}. Vanilla registers a {@link CubeMapTexture}
 * there; replacing that texture is enough to change the background immediately, with no reload and
 * no mixins.
 */
public final class PanoramaLibrary {
    public static final String VANILLA = "vanilla";

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Identifier PANORAMA_TEXTURE = Identifier.withDefaultNamespace("textures/gui/title/background/panorama");
    private static final int FACE_COUNT = 6;

    /**
     * The cubemap layer order used by {@link CubeMapTexture#loadContents}. Vanilla does not load the
     * faces in numeric order, so captured faces must be stacked in exactly this order.
     */
    private static final int[] LAYER_TO_FACE = {1, 3, 5, 4, 0, 2};

    private static String requestedName = "";
    private static String appliedName = "";
    private static long generation;
    private static boolean configApplyPending = true;

    private PanoramaLibrary() {
        throw new UnsupportedOperationException();
    }

    public static Path panoramasDirectory() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("screenshots").resolve("panoramas");
    }

    /**
     * @return names of all folders that contain a complete set of panorama faces, newest first.
     */
    public static List<String> listPanoramas() {
        Path root = panoramasDirectory();
        if (!Files.isDirectory(root)) {
            return List.of();
        }

        List<Path> folders = new ArrayList<>();
        try (Stream<Path> children = Files.list(root)) {
            children.filter(Files::isDirectory).filter(PanoramaLibrary::hasAllFaces).forEach(folders::add);
        } catch (IOException e) {
            LOGGER.warn("Could not list panoramas in {}", root, e);
            return List.of();
        }

        folders.sort(Comparator.comparingLong(PanoramaLibrary::lastModified).reversed());
        List<String> names = new ArrayList<>(folders.size());
        for (Path folder : folders) {
            names.add(folder.getFileName().toString());
        }
        return names;
    }

    /**
     * Applies the configured panorama if it changed. Called on client ticks so config changes made
     * through NeoForge's config screen are picked up without writing extra UI code.
     */
    public static void request(String name) {
        apply(name, false);
    }

    /**
     * Applies a panorama chosen by the player. Unlike {@link #request(String)} this always retries,
     * so selecting the same entry twice still recovers from a transient load error.
     */
    public static void select(String name) {
        apply(name, true);
    }

    /**
     * Applies a panorama as a temporary preview of the picker screen. The selection is not saved, so
     * closing the picker can simply re-apply the configured value.
     */
    public static void preview(String name) {
        apply(name, true);
    }

    public static void markConfigDirty() {
        configApplyPending = true;
    }

    public static boolean consumeConfigDirty() {
        boolean pending = configApplyPending;
        configApplyPending = false;
        return pending;
    }

    public static Path facePath(Path folder, int face) {
        Path direct = folder.resolve("panorama_" + face + ".png");
        if (Files.isRegularFile(direct)) {
            return direct;
        }
        // Vanilla's panorama grabber always appends a "screenshots" directory to the folder it is
        // given. Accepted as a fallback if the files could not be moved up after capture.
        return folder.resolve("screenshots").resolve("panorama_" + face + ".png");
    }

    public static boolean hasAllFaces(Path folder) {
        for (int face = 0; face < FACE_COUNT; face++) {
            if (!Files.isRegularFile(facePath(folder, face))) {
                return false;
            }
        }
        return true;
    }

    private static long lastModified(Path folder) {
        try {
            return Files.getLastModifiedTime(folder).toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }

    private static void apply(String name, boolean force) {
        Minecraft minecraft = Minecraft.getInstance();
        String key = name == null || name.isBlank() ? VANILLA : name.trim();

        if (!force && key.equals(requestedName)) {
            return;
        }

        if (!VANILLA.equals(key)) {
            Path folder = panoramasDirectory().resolve(key);
            if (!hasAllFaces(folder)) {
                LOGGER.warn("Panorama '{}' is missing one or more faces in {}", key, folder);
                if (force) {
                    PanoramaKitClient.notifyError(Component.translatable("panoramakit.message.not_found", key));
                }
                if (appliedName.isEmpty()) {
                    long requestGeneration = ++generation;
                    minecraft.execute(() -> applyVanilla(requestGeneration));
                }
                requestedName = key;
                return;
            }

            requestedName = key;
            long requestGeneration = ++generation;
            Util.ioPool().execute(() -> {
                LoadedPanorama loaded;
                try {
                    loaded = LoadedPanorama.read(folder);
                } catch (Throwable t) {
                    LOGGER.error("Could not read panorama '{}'", key, t);
                    minecraft.execute(() -> PanoramaKitClient.notifyError(
                            Component.translatable("panoramakit.message.load_failed", key)));
                    return;
                }

                minecraft.execute(() -> {
                    if (requestGeneration != generation) {
                        loaded.discard();
                        return;
                    }
                    try {
                        loaded.register(minecraft.getTextureManager());
                        appliedName = key;
                        LOGGER.info("Showing panorama '{}' behind the main menu", key);
                    } catch (Throwable t) {
                        loaded.discard();
                        LOGGER.error("Could not upload panorama '{}'", key, t);
                        PanoramaKitClient.notifyError(
                                Component.translatable("panoramakit.message.load_failed", key));
                    }
                });
            });
            return;
        }

        requestedName = key;
        long requestGeneration = ++generation;
        minecraft.execute(() -> applyVanilla(requestGeneration));
    }

    private static void applyVanilla(long requestGeneration) {
        if (requestGeneration != generation) {
            return;
        }

        try {
            // Loading through the texture manager honours the active resource pack, so "vanilla"
            // means whatever the pack stack provides for the title panorama.
            CubeMapTexture texture = new CubeMapTexture(PANORAMA_TEXTURE);
            Minecraft.getInstance().getTextureManager().registerAndLoad(PANORAMA_TEXTURE, texture);
            appliedName = VANILLA;
            LOGGER.info("Showing the vanilla panorama behind the main menu");
        } catch (Throwable t) {
            LOGGER.error("Could not restore the vanilla panorama", t);
        }
    }

    /** Faces loaded off-thread; uploaded to the GPU on the render thread. */
    private static final class LoadedPanorama {
        private final NativeImage stackedFaces;
        private final int faceSize;
        private boolean discarded;

        private LoadedPanorama(NativeImage stackedFaces, int faceSize) {
            this.stackedFaces = stackedFaces;
            this.faceSize = faceSize;
        }

        static LoadedPanorama read(Path folder) throws IOException {
            NativeImage stacked = null;
            int size = -1;

            try {
                for (int layer = 0; layer < FACE_COUNT; layer++) {
                    Path file = facePath(folder, LAYER_TO_FACE[layer]);
                    NativeImage face;
                    try (InputStream in = Files.newInputStream(file)) {
                        face = NativeImage.read(in);
                    }

                    try {
                        int width = face.getWidth();
                        int height = face.getHeight();
                        if (width != height) {
                            throw new IOException("Panorama face is not square: " + file);
                        }
                        if (stacked == null) {
                            size = width;
                            stacked = new NativeImage(size, size * FACE_COUNT, false);
                        } else if (width != size) {
                            throw new IOException("Panorama face sizes do not match: " + file);
                        }
                        face.copyRect(stacked, 0, 0, 0, layer * size, size, size, false, true);
                    } finally {
                        face.close();
                    }
                }
            } catch (Throwable t) {
                if (stacked != null) {
                    stacked.close();
                }
                if (t instanceof IOException ioException) {
                    throw ioException;
                }
                if (t instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                throw new IOException(t);
            }

            return new LoadedPanorama(stacked, size);
        }

        void register(TextureManager textureManager) {
            if (this.discarded) {
                return;
            }
            this.discarded = true;
            PanoramaTexture texture = new PanoramaTexture(this.stackedFaces, this.faceSize);
            textureManager.register(PANORAMA_TEXTURE, texture);
        }

        void discard() {
            if (!this.discarded) {
                this.discarded = true;
                this.stackedFaces.close();
            }
        }
    }
}
