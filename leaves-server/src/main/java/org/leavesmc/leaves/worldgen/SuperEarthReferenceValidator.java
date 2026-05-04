package org.leavesmc.leaves.worldgen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class SuperEarthReferenceValidator {

    private SuperEarthReferenceValidator() {
    }

    public static List<String> validate(SuperEarthDatapackIndex.SuperEarthPackIndex index) {
        List<String> problems = new ArrayList<>();
        Map<String, SuperEarthStructureTemplateIndex.SuperEarthTemplateResource> templates = index.templates();
        for (SuperEarthDatapackIndex.SuperEarthResource resource : index.resources().values()) {
            if (resource.directory().equals("worldgen/template_pool")) {
                validateTemplatePool(resource, templates, problems);
                continue;
            }
            if (resource.directory().equals("worldgen/structure")) {
                validateStructure(resource, index.resources(), problems);
            }
        }
        return problems;
    }

    private static void validateTemplatePool(
        SuperEarthDatapackIndex.SuperEarthResource resource,
        Map<String, SuperEarthStructureTemplateIndex.SuperEarthTemplateResource> templates,
        List<String> problems
    ) {
        JsonArray elements = resource.json().getAsJsonArray("elements");
        if (elements == null) {
            return;
        }
        for (JsonElement element : elements) {
            JsonObject wrapper = element.getAsJsonObject();
            JsonObject poolElement = wrapper.getAsJsonObject("element");
            if (poolElement == null) {
                continue;
            }
            String elementType = poolElement.has("element_type") ? poolElement.get("element_type").getAsString() : "";
            if (elementType.endsWith("single_pool_element") || elementType.endsWith("legacy_single_pool_element")) {
                String location = string(poolElement, "location");
                if (location != null && !templates.containsKey(location)) {
                    problems.add("missing_template:" + resource.id() + " -> " + location);
                }
            }
            if (elementType.endsWith("list_pool_element")) {
                JsonArray children = poolElement.getAsJsonArray("elements");
                if (children != null) {
                    for (JsonElement child : children) {
                        JsonObject childObject = child.getAsJsonObject();
                        String location = string(childObject, "location");
                        if (location != null && !templates.containsKey(location)) {
                            problems.add("missing_template:" + resource.id() + " -> " + location);
                        }
                    }
                }
            }
        }
    }

    private static void validateStructure(
        SuperEarthDatapackIndex.SuperEarthResource resource,
        Map<String, SuperEarthDatapackIndex.SuperEarthResource> resources,
        List<String> problems
    ) {
        JsonObject json = resource.json();
        String type = string(json, "type");
        if (type == null || !type.endsWith("jigsaw")) {
            return;
        }
        String startPool = string(json, "start_pool");
        if (startPool != null && !containsResource(resources, "worldgen/template_pool", startPool)) {
            problems.add("missing_start_pool:" + resource.id() + " -> " + startPool);
        }
        JsonArray aliases = json.getAsJsonArray("pool_aliases");
        if (aliases != null) {
            for (JsonElement alias : aliases) {
                JsonObject aliasObject = alias.getAsJsonObject();
                String target = string(aliasObject, "target");
                if (target != null && !containsResource(resources, "worldgen/template_pool", target)) {
                    problems.add("missing_pool_alias:" + resource.id() + " -> " + target);
                }
            }
        }
    }

    private static boolean containsResource(Map<String, SuperEarthDatapackIndex.SuperEarthResource> resources, String directory, String id) {
        int separator = id.indexOf(':');
        if (separator < 0) {
            return false;
        }
        String namespace = id.substring(0, separator);
        String path = id.substring(separator + 1);
        return resources.containsKey(directory + "/" + namespace + "/" + path);
    }

    private static String string(JsonObject object, String key) {
        if (object == null || !object.has(key)) {
            return null;
        }
        JsonElement element = object.get(key);
        return element.isJsonPrimitive() ? element.getAsString() : null;
    }
}
