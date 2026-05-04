package org.leavesmc.leaves.worldgen;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public final class SuperEarthDatapackIndex {

    private static final Path ROOT = Path.of("Datapacks");
    private static final Set<String> WORLDGEN_DIRECTORIES = Set.of(
        "worldgen/biome",
        "worldgen/configured_carver",
        "worldgen/configured_feature",
        "worldgen/density_function",
        "worldgen/flat_level_generator_preset",
        "worldgen/multi_noise_biome_source_parameter_list",
        "worldgen/noise",
        "worldgen/noise_settings",
        "worldgen/placed_feature",
        "worldgen/processor_list",
        "worldgen/structure",
        "worldgen/structure_set",
        "worldgen/template_pool",
        "dimension",
        "dimension_type"
    );

    private SuperEarthDatapackIndex() {
    }

    public static SuperEarthPackIndex scan() {
        Map<String, SuperEarthResource> merged = new LinkedHashMap<>();
        List<String> packOrder = new ArrayList<>();
        Map<String, SuperEarthStructureTemplateIndex.SuperEarthTemplateResource> templates = SuperEarthStructureTemplateIndex.scan();
        if (!Files.isDirectory(ROOT)) {
            return new SuperEarthPackIndex(packOrder, merged, templates);
        }
        try (Stream<Path> stream = Files.list(ROOT)) {
            stream.filter(Files::isDirectory).sorted().forEach(packPath -> scanPack(packPath, merged, packOrder));
        } catch (IOException ignored) {
        }
        return new SuperEarthPackIndex(packOrder, merged, templates);
    }

    private static void scanPack(Path packPath, Map<String, SuperEarthResource> merged, List<String> packOrder) {
        Path dataPath = packPath.resolve("data");
        if (!Files.isDirectory(dataPath)) {
            return;
        }
        packOrder.add(packPath.getFileName().toString());
        try (Stream<Path> namespaces = Files.list(dataPath)) {
            namespaces.filter(Files::isDirectory).sorted().forEach(namespacePath -> scanNamespace(packPath, namespacePath, merged));
        } catch (IOException ignored) {
        }
    }

    private static void scanNamespace(Path packPath, Path namespacePath, Map<String, SuperEarthResource> merged) {
        String namespace = namespacePath.getFileName().toString();
        for (String directory : WORLDGEN_DIRECTORIES) {
            Path target = namespacePath.resolve(directory.replace('/', java.io.File.separatorChar));
            if (!Files.isDirectory(target)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(target)) {
                files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".json"))
                    .sorted()
                    .forEach(path -> mergeResource(packPath, namespace, directory, target, path, merged));
            } catch (IOException ignored) {
            }
        }
    }

    private static void mergeResource(
        Path packPath,
        String namespace,
        String directory,
        Path root,
        Path file,
        Map<String, SuperEarthResource> merged
    ) {
        String relative = root.relativize(file).toString().replace('\\', '/');
        String pathWithoutJson = relative.substring(0, relative.length() - 5);
        String resourceKey = directory + "/" + namespace + "/" + pathWithoutJson;
        JsonObject json = readJson(file);
        SuperEarthResource resource = new SuperEarthResource(
            packPath.getFileName().toString(),
            namespace,
            directory,
            pathWithoutJson,
            Identifier.fromNamespaceAndPath(namespace, pathWithoutJson),
            json
        );
        SuperEarthResource previous = merged.get(resourceKey);
        if (previous == null) {
            merged.put(resourceKey, resource);
            return;
        }
        if (isTagDirectory(directory)) {
            merged.put(resourceKey, previous.mergeTag(resource));
            return;
        }
        if (directory.equals("worldgen/structure_set")) {
            merged.put(resourceKey, previous.mergeWith(resource, SuperEarthConflictResolver.mergeStructureSet(previous.json, resource.json)));
            return;
        }
        if (directory.equals("worldgen/template_pool")) {
            merged.put(resourceKey, previous.mergeWith(resource, SuperEarthConflictResolver.mergeTemplatePool(previous.json, resource.json)));
            return;
        }
        if (directory.equals("worldgen/structure")) {
            merged.put(resourceKey, previous.mergeWith(resource, SuperEarthStructureMerger.mergeStructure(previous.json, resource.json)));
            return;
        }
        merged.put(resourceKey, resource);
    }

    private static JsonObject readJson(Path file) {
        try (Reader reader = Files.newBufferedReader(file)) {
            JsonElement element = JsonParser.parseReader(reader);
            if (element.isJsonObject()) {
                return element.getAsJsonObject();
            }
        } catch (IOException ignored) {
        }
        return new JsonObject();
    }

    private static boolean isTagDirectory(String directory) {
        return directory.startsWith("tags/");
    }

    public record SuperEarthPackIndex(
        List<String> packOrder,
        Map<String, SuperEarthResource> resources,
        Map<String, SuperEarthStructureTemplateIndex.SuperEarthTemplateResource> templates
    ) {
        public Set<String> packs() {
            return new LinkedHashSet<>(this.packOrder);
        }
    }

    public record SuperEarthResource(
        String packName,
        String namespace,
        String directory,
        String path,
        Identifier id,
        JsonObject json
    ) {
        public SuperEarthResource mergeTag(SuperEarthResource override) {
            JsonObject merged = this.json.deepCopy();
            merged.addProperty("replace", false);
            Set<String> values = new LinkedHashSet<>();
            collectValues(this.json, values);
            collectValues(override.json, values);
            com.google.gson.JsonArray array = new com.google.gson.JsonArray();
            for (String value : values) {
                array.add(value);
            }
            merged.add("values", array);
            return new SuperEarthResource(override.packName, override.namespace, override.directory, override.path, override.id, merged);
        }

        public SuperEarthResource mergeWith(SuperEarthResource override, JsonObject mergedJson) {
            return new SuperEarthResource(override.packName, override.namespace, override.directory, override.path, override.id, mergedJson);
        }

        private static void collectValues(JsonObject object, Set<String> values) {
            JsonElement element = object.get("values");
            if (element == null || !element.isJsonArray()) {
                return;
            }
            for (JsonElement value : element.getAsJsonArray()) {
                values.add(value.getAsString());
            }
        }
    }
}
