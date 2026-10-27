package com.panoramakit.client;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;

import net.minecraft.client.renderer.texture.AbstractTexture;

/**
 * A six-layer cubemap texture built from a captured panorama. The texture is registered under the
 * vanilla panorama id, which lets the title screen show it without any mixins.
 *
 * <p>26.2 renamed {@code TextureFormat} to {@link GpuFormat} and uploads images through a staging
 * buffer instead of {@code writeToTexture}.
 */
final class PanoramaTexture extends AbstractTexture {
    PanoramaTexture(NativeImage stackedFaces, int faceSize) {
        GpuDevice device = RenderSystem.getDevice();
        try {
            this.texture = device.createTexture(
                    () -> "Panorama Kit cubemap",
                    GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_CUBEMAP_COMPATIBLE,
                    GpuFormat.RGBA8_UNORM,
                    faceSize,
                    faceSize,
                    6,
                    1);
            this.textureView = device.createTextureView(this.texture);
            GpuBufferSlice staging = device.createCommandEncoder()
                    .transientMemory()
                    .uploadStaging(stackedFaces.getPixelBytes(), 1L, 16);
            for (int layer = 0; layer < 6; layer++) {
                device.createCommandEncoder().copyBufferToTexture(
                        staging,
                        0,
                        layer * faceSize,
                        stackedFaces.getWidth(),
                        stackedFaces.getHeight(),
                        this.texture,
                        0,
                        0,
                        faceSize,
                        faceSize,
                        0,
                        layer);
            }
            this.sampler = RenderSystem.getSamplerCache()
                    .getSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, FilterMode.NEAREST, FilterMode.NEAREST, false);
        } catch (Throwable t) {
            this.close();
            throw t;
        } finally {
            stackedFaces.close();
        }
    }
}
