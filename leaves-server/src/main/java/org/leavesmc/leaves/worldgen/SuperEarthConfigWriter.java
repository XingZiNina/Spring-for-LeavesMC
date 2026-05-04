package org.leavesmc.leaves.worldgen;

import org.bukkit.configuration.file.YamlConfiguration;
import org.leavesmc.leaves.LeavesConfig;
import org.leavesmc.leaves.config.GlobalConfigCreator;

import java.io.File;
import java.io.IOException;

public final class SuperEarthConfigWriter {

    private SuperEarthConfigWriter() {
    }

    public static void ensureChineseCommentedConfig(File file) {
        if (file.exists()) {
            return;
        }
        try {
            GlobalConfigCreator.main(new String[0]);
            if (!file.getName().equals("leaves.yml")) {
                File generated = new File("leaves.yml");
                if (generated.exists() && !generated.equals(file)) {
                    generated.renameTo(file);
                }
            }
        } catch (Exception ignored) {
            try {
                YamlConfiguration config = new YamlConfiguration();
                config.options().setHeader(SuperEarthConfigComments.header());
                config.set("config-version", LeavesConfig.CURRENT_CONFIG_VERSION);
                config.save(file);
            } catch (IOException ignoredAgain) {
            }
        }
    }
}
