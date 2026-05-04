package org.leavesmc.leaves.worldgen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public final class SuperEarthStructureMerger {

    private SuperEarthStructureMerger() {
    }

    public static JsonObject mergeStructure(JsonObject base, JsonObject override) {
        JsonObject merged = base.deepCopy();
        copyIfPresent(override, merged, "type");
        copyIfPresent(override, merged, "biomes");
        copyIfPresent(override, merged, "step");
        copyIfPresent(override, merged, "terrain_adaptation");
        copyIfPresent(override, merged, "spawn_overrides");
        copyIfPresent(override, merged, "start_height");
        copyIfPresent(override, merged, "start_jigsaw_name");
        copyIfPresent(override, merged, "max_distance_from_center");
        copyIfPresent(override, merged, "dimension_padding");
        copyIfPresent(override, merged, "liquid_settings");
        copyIfPresent(override, merged, "use_expansion_hack");
        copyIfPresent(override, merged, "project_start_to_heightmap");
        if (override.has("start_pool")) {
            merged.add("start_pool", override.get("start_pool"));
        }
        if (base.has("pool_aliases") || override.has("pool_aliases")) {
            merged.add("pool_aliases", mergeAliases(base.getAsJsonArray("pool_aliases"), override.getAsJsonArray("pool_aliases")));
        }
        return merged;
    }

    private static JsonArray mergeAliases(JsonArray base, JsonArray override) {
        JsonArray merged = new JsonArray();
        appendAliases(merged, base);
        appendAliases(merged, override);
        return merged;
    }

    private static void appendAliases(JsonArray target, JsonArray source) {
        if (source == null) {
            return;
        }
        for (JsonElement element : source) {
            JsonObject object = element.getAsJsonObject();
            String key = aliasKey(object);
            if (!containsAlias(target, key)) {
                target.add(element);
            }
        }
    }

    private static boolean containsAlias(JsonArray array, String key) {
        for (JsonElement element : array) {
            if (aliasKey(element.getAsJsonObject()).equals(key)) {
                return true;
            }
        }
        return false;
    }

    private static String aliasKey(JsonObject object) {
        if (object.has("target")) {
            return object.get("target").getAsString();
        }
        return object.toString();
    }

    private static void copyIfPresent(JsonObject source, JsonObject target, String key) {
        if (source.has(key)) {
            target.add(key, source.get(key));
        }
    }
}
