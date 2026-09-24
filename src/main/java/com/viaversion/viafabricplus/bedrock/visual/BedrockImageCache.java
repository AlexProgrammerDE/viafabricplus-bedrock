/*
 * This file is part of ViaFabricPlus Bedrock - https://github.com/florianreuth/viafabricplus-bedrock
 * Copyright (C) 2023-2026 ViaVersion and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.viaversion.viafabricplus.bedrock.visual;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/** Loads Xbox profile pictures and locally installed Bedrock artwork without blocking the game thread. */
public final class BedrockImageCache {

    private static final int MAX_IMAGES = 128;
    private static final int MAX_BYTES = 4 * 1024 * 1024;
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private static final Map<String, Image> IMAGES = new LinkedHashMap<>(16, 0.75F, true);
    private static final Set<String> LOADING = new HashSet<>();
    private static final Set<String> FAILED = new HashSet<>();

    private BedrockImageCache() {
    }

    public static boolean drawRemote(final GuiGraphicsExtractor graphics, final String url, final int x,
                                     final int y, final int width, final int height) {
        if (url.isBlank()) return false;
        final URI uri;
        try {
            final URI parsed = URI.create(url);
            uri = URI.create(parsed.toString().replaceFirst("^http:", "https:"));
        } catch (Exception exception) {
            return false;
        }
        final String host = uri.getHost();
        if (!"https".equals(uri.getScheme()) || uri.getUserInfo() != null || uri.getPort() != -1
            || host == null || !(host.equals("xboxlive.com") || host.endsWith(".xboxlive.com"))) return false;
        return draw(graphics, uri.toString(), () -> {
            final HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15)).GET().build();
            final HttpResponse<byte[]> response = HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) throw new IOException("Xbox image returned HTTP " + response.statusCode());
            return response.body();
        }, x, y, width, height);
    }

    public static boolean drawLocal(final GuiGraphicsExtractor graphics, final Path path, final int x,
                                    final int y, final int width, final int height) {
        return draw(graphics, path.toUri().toString(), () -> Files.readAllBytes(path), x, y, width, height);
    }

    private static boolean draw(final GuiGraphicsExtractor graphics, final String key, final ImageSource source,
                                final int x, final int y, final int width, final int height) {
        final Image image = IMAGES.get(key);
        if (image != null) {
            int sourceWidth = image.width();
            int sourceHeight = image.height();
            if ((long) sourceWidth * height > (long) sourceHeight * width) {
                sourceWidth = Math.max(1, sourceHeight * width / height);
            } else {
                sourceHeight = Math.max(1, sourceWidth * height / width);
            }
            final float u = (image.width() - sourceWidth) / 2F;
            final float v = (image.height() - sourceHeight) / 2F;
            graphics.blit(RenderPipelines.GUI_TEXTURED, image.id(), x, y, u, v,
                width, height, sourceWidth, sourceHeight, image.width(), image.height());
            return true;
        }
        if (!FAILED.contains(key) && LOADING.add(key)) {
            CompletableFuture.supplyAsync(() -> {
                try {
                    final byte[] bytes = source.read();
                    if (bytes.length > MAX_BYTES) throw new IOException("Image is too large");
                    final NativeImage pixels = NativeImage.read(bytes);
                    if (pixels.getWidth() > 2048 || pixels.getHeight() > 2048) {
                        pixels.close();
                        throw new IOException("Image dimensions are too large");
                    }
                    return pixels;
                } catch (Exception exception) {
                    throw new IllegalStateException("Could not load image", exception);
                }
            }).whenComplete((pixels, error) -> Minecraft.getInstance().execute(() -> {
                LOADING.remove(key);
                if (error != null) {
                    FAILED.add(key);
                    return;
                }
                final Identifier id = Identifier.fromNamespaceAndPath("viafabricplus-bedrock",
                    "image/" + UUID.randomUUID());
                Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> key, pixels));
                IMAGES.put(key, new Image(id, pixels.getWidth(), pixels.getHeight()));
                if (IMAGES.size() > MAX_IMAGES) {
                    final Iterator<Image> oldest = IMAGES.values().iterator();
                    Minecraft.getInstance().getTextureManager().release(oldest.next().id());
                    oldest.remove();
                }
            }));
        }
        return false;
    }

    @FunctionalInterface
    private interface ImageSource {
        byte[] read() throws Exception;
    }

    private record Image(Identifier id, int width, int height) {
    }

}
