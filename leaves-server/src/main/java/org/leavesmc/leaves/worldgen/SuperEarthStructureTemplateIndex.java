package org.leavesmc.leaves.worldgen;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

public final class SuperEarthStructureTemplateIndex {

    private static final Path ROOT = Path.of("Datapacks");

    private SuperEarthStructureTemplateIndex() {
    }

    public static Map<String, SuperEarthTemplateResource> scan() {
        Map<String, SuperEarthTemplateResource> merged = new LinkedHashMap<>();
        if (!Files.isDirectory(ROOT)) {
            return merged;
        }
        try (Stream<Path> stream = Files.list(ROOT)) {
            stream.filter(Files::isDirectory).sorted().forEach(pack -> scanPack(pack, merged));
        } catch (IOException ignored) {
        }
        return merged;
    }

    private static void scanPack(Path pack, Map<String, SuperEarthTemplateResource> merged) {
        Path data = pack.resolve("data");
        if (!Files.isDirectory(data)) {
            return;
        }
        try (Stream<Path> namespaces = Files.list(data)) {
            namespaces.filter(Files::isDirectory).sorted().forEach(namespace -> scanNamespace(pack, namespace, merged));
        } catch (IOException ignored) {
        }
    }

    private static void scanNamespace(Path pack, Path namespacePath, Map<String, SuperEarthTemplateResource> merged) {
        String namespace = namespacePath.getFileName().toString();
        Path structureRoot = namespacePath.resolve("structure");
        if (!Files.isDirectory(structureRoot)) {
            return;
        }
        try (Stream<Path> files = Files.walk(structureRoot)) {
            files.filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".nbt"))
                .sorted()
                .forEach(path -> mergeTemplate(pack, namespace, structureRoot, path, merged));
        } catch (IOException ignored) {
        }
    }

    private static void mergeTemplate(Path pack, String namespace, Path structureRoot, Path path, Map<String, SuperEarthTemplateResource> merged) {
        String relative = structureRoot.relativize(path).toString().replace('\\', '/');
        String templatePath = relative.substring(0, relative.length() - 4);
        String key = namespace + ":" + templatePath;
        merged.put(key, new SuperEarthTemplateResource(pack.getFileName().toString(), namespace, templatePath, path, read(path)));
    }

    private static CompoundTag read(Path path) {
        try (InputStream inputStream = Files.newInputStream(path)) {
            return NbtIo.readCompressed(inputStream, NbtAccounter.unlimitedHeap());
        } catch (IOException ignored) {
            return new CompoundTag();
        }
    }

    public record SuperEarthTemplateResource(String packName, String namespace, String path, Path file, CompoundTag tag) {
    }
}
