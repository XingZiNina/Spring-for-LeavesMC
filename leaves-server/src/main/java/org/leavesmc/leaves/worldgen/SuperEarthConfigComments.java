package org.leavesmc.leaves.worldgen;

import org.leavesmc.leaves.LeavesConfig;
import org.leavesmc.leaves.config.GlobalConfigManager;

import java.util.ArrayList;
import java.util.List;

public final class SuperEarthConfigComments {

    private SuperEarthConfigComments() {
    }

    public static List<String> header() {
        List<String> lines = new ArrayList<>();
        lines.add("Leaves 配置文件。");
        lines.add("以下中文注释尽量对齐 Leaves 当前配置语义。\n");
        lines.addAll(allConfigPaths());
        return lines;
    }

    public static List<String> allConfigPaths() {
        List<String> lines = new ArrayList<>();
        for (String key : LeavesConfig.config.getKeys(true)) {
            if (key.startsWith(GlobalConfigManager.CONFIG_START)) {
                lines.add(key + ": 详见该项下方中文注释。\n");
            }
        }
        return lines;
    }
}
