package org.leavesmc.leaves.worldgen;

import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.FolderRepositorySource;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.world.level.validation.DirectoryValidator;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class SuperEarthPackRepository {

    private static final Path DATAPACKS_PATH = Path.of("Datapacks");

    private SuperEarthPackRepository() {
    }

    public static PackRepository create(DirectoryValidator validator, RepositorySource... sources) {
        List<RepositorySource> merged = new ArrayList<>();
        merged.add(new ServerPacksSource(validator));
        merged.add(new FolderRepositorySource(DATAPACKS_PATH, PackType.SERVER_DATA, PackSource.DEFAULT, validator));
        for (RepositorySource source : sources) {
            merged.add(source);
        }
        return new PackRepository(validator, merged.toArray(RepositorySource[]::new));
    }
}
