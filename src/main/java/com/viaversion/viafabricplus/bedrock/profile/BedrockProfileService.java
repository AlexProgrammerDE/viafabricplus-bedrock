/*
 * This file is part of ViaFabricPlus Bedrock - https://github.com/florianreuth/viafabricplus-bedrock
 * Copyright (C) 2021-2026 the original authors
 *                         - Florian Reuth <git@florianreuth.de>
 *                         - RK_01/RaphiMC
 * Copyright (C) 2023-2026 ViaVersion and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.viaversion.viafabricplus.bedrock.profile;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.viaversion.viafabricplus.bedrock.friends.BedrockXboxError;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import net.raphimc.minecraftauth.bedrock.BedrockAuthManager;

/** Xbox achievement data available to the signed-in Bedrock account. */
public final class BedrockProfileService {

    private static final URI ACHIEVEMENTS = URI.create("https://achievements.xboxlive.com/");
    private static final URI USER_STATS = URI.create("https://userstats.xboxlive.com/");
    private static final String MINECRAFT_WINDOWS_TITLE_ID = "896928775";
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    private BedrockProfileService() {
    }

    public static CompletableFuture<List<Achievement>> achievements(final BedrockAuthManager account) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                final String xuid = account.getXboxUserProfile().refresh().getId();
                if (!xuid.matches("[0-9]+")) {
                    throw new IOException("Xbox returned an invalid user ID");
                }
                final List<Achievement> achievements = new ArrayList<>();
                String continuation = "";
                for (int page = 0; page < 10; page++) {
                    final String path = "users/xuid(" + xuid + ")/achievements?titleId=" + MINECRAFT_WINDOWS_TITLE_ID
                        + "&maxItems=100" + (continuation.isBlank() ? "" : "&continuationToken="
                        + URLEncoder.encode(continuation, StandardCharsets.UTF_8));
                    final HttpRequest request = HttpRequest.newBuilder(ACHIEVEMENTS.resolve(path))
                        .timeout(Duration.ofSeconds(15))
                        .header("Authorization", account.getXboxLiveXstsToken().refresh().getAuthorizationHeader())
                        .header("X-Xbl-Contract-Version", "2")
                        .header("Accept", "application/json")
                        .header("Accept-Language", "en-US,en;q=0.9")
                        .GET().build();
                    final HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
                    if (response.statusCode() / 100 != 2) {
                        throw BedrockXboxError.response("Minecraft achievements", response);
                    }
                    final JsonObject data = JsonParser.parseString(response.body()).getAsJsonObject();
                    for (final JsonElement element : array(data, "achievements")) {
                        final JsonObject achievement = element.getAsJsonObject();
                        final boolean achieved = "Achieved".equalsIgnoreCase(string(achievement, "progressState"));
                        int gamerscore = 0;
                        for (final JsonElement rewardElement : array(achievement, "rewards")) {
                            final JsonObject reward = rewardElement.getAsJsonObject();
                            if ("Gamerscore".equalsIgnoreCase(string(reward, "type"))) {
                                try {
                                    gamerscore = Integer.parseInt(string(reward, "value"));
                                } catch (NumberFormatException ignored) {
                                    // Some rewards do not provide a numeric score.
                                }
                            }
                        }
                        achievements.add(new Achievement(string(achievement, "id"), string(achievement, "name"),
                            achieved ? string(achievement, "description") : string(achievement, "lockedDescription"),
                            achieved, gamerscore, string(object(achievement, "progression"), "timeUnlocked")));
                    }
                    continuation = string(object(data, "pagingInfo"), "continuationToken");
                    if (continuation.isBlank()) {
                        break;
                    }
                }
                return List.copyOf(achievements);
            } catch (Exception exception) {
                throw new IllegalStateException("Could not load Minecraft achievements", exception);
            }
        });
    }

    /** Four Minecraft profile statistics, subject to the player's Xbox privacy settings. */
    public static CompletableFuture<Map<Statistic, String>> statistics(final BedrockAuthManager account,
                                                                       final String xuid) {
        if (!xuid.matches("[0-9]+")) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid Xbox user ID"));
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                final String ownXuid = account.getXboxUserProfile().refresh().getId();
                final HttpRequest titleRequest = HttpRequest.newBuilder(ACHIEVEMENTS.resolve("users/xuid("
                        + ownXuid + ")/achievements?titleId=" + MINECRAFT_WINDOWS_TITLE_ID + "&maxItems=1"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", account.getXboxLiveXstsToken().refresh().getAuthorizationHeader())
                    .header("X-Xbl-Contract-Version", "2")
                    .header("Accept", "application/json")
                    .GET().build();
                final JsonObject title = send("Minecraft title details", titleRequest);
                final JsonArray titleAchievements = array(title, "achievements");
                if (titleAchievements.isEmpty()) {
                    throw new IOException("Minecraft has no achievement service configuration");
                }
                final String scid = string(titleAchievements.get(0).getAsJsonObject(), "serviceConfigId");
                if (!scid.matches("[0-9a-fA-F-]{36}")) {
                    throw new IOException("Xbox returned an invalid service configuration ID");
                }
                final String path = "users/xuid(" + xuid + ")/scids/" + scid
                    + "/stats/MinutesPlayed,BlockBroken,MobKilled,DistanceTravelled";
                final HttpRequest request = HttpRequest.newBuilder(USER_STATS.resolve(path))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", account.getXboxLiveXstsToken().refresh().getAuthorizationHeader())
                    .header("Accept", "application/json")
                    .GET().build();
                final JsonObject response = send("Minecraft statistics", request);
                final JsonObject user = object(response, "user");
                final Map<Statistic, String> stats = new HashMap<>();
                for (final JsonElement element : array(user, "stats")) {
                    final JsonObject stat = element.getAsJsonObject();
                    for (final Statistic known : Statistic.values()) {
                        if (known.apiName.equalsIgnoreCase(string(stat, "statname"))) {
                            stats.put(known, string(stat, "value"));
                        }
                    }
                }
                return Map.copyOf(stats);
            } catch (Exception exception) {
                throw new IllegalStateException("Could not load Minecraft statistics", exception);
            }
        });
    }

    private static JsonObject send(final String operation, final HttpRequest request) throws IOException, InterruptedException {
        final HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw BedrockXboxError.response(operation, response);
        }
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    private static JsonArray array(final JsonObject data, final String key) {
        return data.has(key) && data.get(key).isJsonArray() ? data.getAsJsonArray(key) : new JsonArray();
    }

    private static JsonObject object(final JsonObject data, final String key) {
        return data.has(key) && data.get(key).isJsonObject() ? data.getAsJsonObject(key) : new JsonObject();
    }

    private static String string(final JsonObject data, final String key) {
        return data.has(key) && data.get(key).isJsonPrimitive() ? data.get(key).getAsString() : "";
    }

    public record Achievement(String id, String name, String description, boolean achieved, int gamerscore,
                              String unlockedAt) {
    }

    public enum Statistic {
        MINUTES_PLAYED("MinutesPlayed"), BLOCKS_BROKEN("BlockBroken"), MOBS_DEFEATED("MobKilled"),
        DISTANCE_TRAVELED("DistanceTravelled");

        private final String apiName;

        Statistic(final String apiName) {
            this.apiName = apiName;
        }
    }

}
