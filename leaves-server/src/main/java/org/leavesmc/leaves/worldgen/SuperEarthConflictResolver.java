package org.leavesmc.leaves.worldgen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public final class SuperEarthConflictResolver {

    private SuperEarthConflictResolver() {
    }

    public static JsonObject mergeStructureSet(JsonObject base, JsonObject override) {
        JsonObject merged = base.deepCopy();
        JsonArray structures = new JsonArray();
        appendUniqueStructures(structures, base.getAsJsonArray("structures"));
        appendUniqueStructures(structures, override.getAsJsonArray("structures"));
        merged.add("structures", structures);
        if (override.has("placement")) {
            merged.add("placement", override.get("placement"));
        }
        return merged;
    }

    public static JsonObject mergeTemplatePool(JsonObject base, JsonObject override) {
        JsonObject merged = base.deepCopy();
        JsonArray elements = new JsonArray();
        appendUniquePoolElements(elements, base.getAsJsonArray("elements"));
        appendUniquePoolElements(elements, override.getAsJsonArray("elements"));
        merged.add("elements", elements);
        if (override.has("fallback")) {
            merged.add("fallback", override.get("fallback"));
        }
        return merged;
    }

    private static void appendUniqueStructures(JsonArray target, JsonArray source) {
        if (source == null) {
            return;
        }
        for (JsonElement element : source) {
            String key = structureKey(element.getAsJsonObject());
            if (!containsStructure(target, key)) {
                target.add(element);
            }
        }
    }

    private static void appendUniquePoolElements(JsonArray target, JsonArray source) {
        if (source == null) {
            return;
        }
        for (JsonElement element : source) {
            String key = poolElementKey(element.getAsJsonObject());
            if (!containsPoolElement(target, key)) {
                target.add(element);
            }
        }
    }

    public static String structureKey(JsonObject object) {
        return object.has("structure") ? object.get("structure").getAsString() : object.toString();
    }

    public static String poolElementKey(JsonObject object) {
        JsonObject element = object.getAsJsonObject("element");
        if (element != null && element.has("location")) {
            return element.get("location").getAsString();
        }
        return object.toString();
    }

    private static boolean containsStructure(JsonArray array, String key) {
        for (JsonElement element : array) {
            if (structureKey(element.getAsJsonObject()).equals(key)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsPoolElement(JsonArray array, String key) {
        for (JsonElement element : array) {
            if (poolElementKey(element.getAsJsonObject()).equals(key)) {
                return true;
            }
        }
        return false;
    }
}
