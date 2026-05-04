package org.leavesmc.leaves;

import io.papermc.paper.adventure.PaperAdventure;
import io.papermc.paper.configuration.GlobalConfiguration;
import net.kyori.adventure.text.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.bukkit.Bukkit;
import org.bukkit.configuration.MemorySection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginManager;
import org.jetbrains.annotations.NotNull;
import org.leavesmc.leaves.bot.BotList;
import org.leavesmc.leaves.bot.ServerBot;
import org.leavesmc.leaves.command.bot.BotCommand;
import org.leavesmc.leaves.command.leaves.LeavesCommand;
import org.leavesmc.leaves.config.GlobalConfigManager;
import org.leavesmc.leaves.config.annotations.GlobalConfig;
import org.leavesmc.leaves.config.annotations.GlobalConfigCategory;
import org.leavesmc.leaves.config.annotations.TransferConfig;
import org.leavesmc.leaves.config.api.ConfigTransformer;
import org.leavesmc.leaves.config.api.ConfigValidator;
import org.leavesmc.leaves.config.api.impl.ConfigValidatorImpl.BooleanConfigValidator;
import org.leavesmc.leaves.config.api.impl.ConfigValidatorImpl.DoubleConfigValidator;
import org.leavesmc.leaves.config.api.impl.ConfigValidatorImpl.EnumConfigValidator;
import org.leavesmc.leaves.config.api.impl.ConfigValidatorImpl.IntConfigValidator;
import org.leavesmc.leaves.config.api.impl.ConfigValidatorImpl.ListConfigValidator;
import org.leavesmc.leaves.config.api.impl.ConfigValidatorImpl.LongConfigValidator;
import org.leavesmc.leaves.config.api.impl.ConfigValidatorImpl.StringConfigValidator;
import org.leavesmc.leaves.profile.LeavesMinecraftSessionService;
import org.leavesmc.leaves.protocol.CarpetServerProtocol.CarpetRule;
import org.leavesmc.leaves.protocol.CarpetServerProtocol.CarpetRules;
import org.leavesmc.leaves.protocol.PcaSyncProtocol;
import org.leavesmc.leaves.protocol.bladeren.BladerenProtocol.LeavesFeature;
import org.leavesmc.leaves.protocol.bladeren.BladerenProtocol.LeavesFeatureSet;
import org.leavesmc.leaves.protocol.rei.REIServerProtocol;
import org.leavesmc.leaves.protocol.servux.logger.DataLogger;
import org.leavesmc.leaves.protocol.syncmatica.SyncmaticaProtocol;
import org.leavesmc.leaves.region.IRegionFileFactory;
import org.leavesmc.leaves.region.RegionFileFormat;
import org.leavesmc.leaves.region.linear.LinearVersion;
import org.leavesmc.leaves.util.LeavesUpdateHelper;
import org.leavesmc.leaves.util.MathUtils;
import org.leavesmc.leaves.util.McTechnicalModeHelper;
import org.leavesmc.leaves.util.ServerI18nUtil;
import org.leavesmc.leaves.util.VillagerInfiniteDiscountHelper;
import org.leavesmc.leaves.worldgen.SuperEarthConfigComments;
import org.leavesmc.leaves.worldgen.SuperEarthConfigWriter;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Predicate;

@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal"})
public final class LeavesConfig {

    public static final String CONFIG_HEADER = "Configuration file for SpringLeaves.";
    public static final int CURRENT_CONFIG_VERSION = 10;

    private static File configFile;
    public static YamlConfiguration config;

    public static void init(final @NotNull File file) {
        LeavesConfig.configFile = file;
        SuperEarthConfigWriter.ensureChineseCommentedConfig(file);
        config = new YamlConfiguration();
        config.options().setHeader(SuperEarthConfigComments.header());
        config.options().copyDefaults(true);

        if (!file.exists()) {
            try {
                boolean is = file.createNewFile();
                if (!is) {
                    throw new IOException("Can't create file");
                }
            } catch (final Exception ex) {
                LeavesLogger.LOGGER.severe("Failure to create leaves config", ex);
            }
        } else {
            try {
                config.load(file);
            } catch (final Exception ex) {
                LeavesLogger.LOGGER.severe("Failure to load leaves config", ex);
                throw new RuntimeException(ex);
            }
        }

        LeavesConfig.config.set("config-version", CURRENT_CONFIG_VERSION);

        GlobalConfigManager.init();

        LeavesCommand.INSTANCE.register();
    }

    public static void reload() {
        if (!LeavesConfig.configFile.exists()) {
            throw new RuntimeException("Leaves config file not found, please restart the server");
        }

        try {
            config.load(LeavesConfig.configFile);
        } catch (final Exception ex) {
            LeavesLogger.LOGGER.severe("Failure to reload leaves config", ex);
            throw new RuntimeException(ex);
        }

        GlobalConfigManager.reload();
    }

    public static void save() {
        try {
            config.save(LeavesConfig.configFile);
        } catch (final Exception ex) {
            LeavesLogger.LOGGER.severe("Unable to save leaves config", ex);
        }
    }

    public static AsyncConfig async = new AsyncConfig();

    @GlobalConfigCategory(value = "async", comment = "异步性能优化相关配置")
    public static class AsyncConfig {

        @GlobalConfig(value = "async-chunk-send", comment = "是否启用异步区块发送")
        public boolean asyncChunkSend = false;

        @GlobalConfig(value = "async-chunk-send-threads", comment = "异步区块发送线程数，0 表示自动（CPU 核心数）")
        public int asyncChunkSendThreads = 0;

        @GlobalConfig(value = "async-pathfinding", comment = "是否启用异步寻路")
        public boolean asyncPathfinding = true;

        @GlobalConfig(value = "async-pathfinding-threads", comment = "异步寻路最大线程数，0 表示自动（CPU 核心数 / 4）")
        public int asyncPathfindingThreads = 0;

        @GlobalConfig(value = "async-mob-spawning", comment = "是否启用异步生物生成")
        public boolean asyncMobSpawning = false;

        @GlobalConfig(value = "async-chunk-loading", comment = "是否启用异步区块预加载与回归预算调度")
        public boolean asyncChunkLoading = true;

        @GlobalConfig(value = "async-chunk-loading-extra-radius", comment = "异步区块预加载额外半径（基于玩家视距之外再预取的区块圈数）")
        public int asyncChunkLoadingExtraRadius = 4;

        @GlobalConfig(value = "async-chunk-loading-max-view-distance", comment = "异步区块加载参与计算的最大视距")
        public int asyncChunkLoadingMaxViewDistance = 32;

        @GlobalConfig(value = "async-chunk-loading-max-inflight", comment = "允许同时在途的异步区块总数上限")
        public int asyncChunkLoadingMaxInflight = 512;

        @GlobalConfig(value = "async-chunk-loading-max-requests-per-tick", comment = "每 tick 最多发起的异步区块请求数")
        public int asyncChunkLoadingMaxRequestsPerTick = 16;

        @GlobalConfig(value = "async-chunk-loading-main-thread-budget", comment = "主线程每 tick 最多回收的异步完成任务数")
        public int asyncChunkLoadingMainThreadBudget = 24;

        @GlobalConfig(value = "async-chunk-loading-monitor-interval", comment = "异步区块监控采样间隔（tick）")
        public int asyncChunkLoadingMonitorInterval = 20;

        @GlobalConfig(value = "async-chunk-loading-low-mspt-threshold", comment = "低负载阈值，低于该 MSPT 时可提高主线程回收预算")
        public double asyncChunkLoadingLowMsptThreshold = 32.0D;

        @GlobalConfig(value = "async-chunk-loading-high-mspt-threshold", comment = "高负载阈值，高于该 MSPT 时会降低主线程回收预算")
        public double asyncChunkLoadingHighMsptThreshold = 40.0D;

        @GlobalConfig(value = "async-chunk-loading-reject-mspt-threshold", comment = "拒绝继续投递异步区块请求的 MSPT 阈值")
        public double asyncChunkLoadingRejectMsptThreshold = 60.0D;

        @GlobalConfig(value = "async-chunk-loading-degrade-ticks", comment = "异步区块加载失败后进入降级模式的持续 tick 数")
        public int asyncChunkLoadingDegradeTicks = 200;

        @GlobalConfig(value = "async-chunk-loading-tracked-per-player", comment = "每个玩家最多跟踪的已提交异步区块键数量")
        public int asyncChunkLoadingTrackedPerPlayer = 1024;

        @GlobalConfig(value = "async-chunk-loading-metrics-protocol", comment = "是否启用异步区块加载指标同步协议")
        public boolean asyncChunkLoadingMetricsProtocol = true;

        @GlobalConfig(value = "async-locate", comment = "是否异步执行 locate 命令以减少主线程卡顿")
        public boolean asyncLocate = true;
    }

    public static ModifyConfig modify = new ModifyConfig();

    @GlobalConfigCategory(value = "modify", comment = "修改原版 Minecraft 的行为特性")
    public static class ModifyConfig {

        @GlobalConfig(value = "krypton-network-optimization", comment = "是否启用 Krypton 风格网络栈优化")
        public boolean kryptonNetworkOptimization = false;

        @GlobalConfig(value = "krypton-fast-varint", comment = "是否启用 Krypton 风格快速 VarInt 读写优化")
        public boolean kryptonFastVarInt = true;

        @GlobalConfig(value = "krypton-fast-varint21-frame-decoder", comment = "是否启用 Krypton 风格快速 VarInt21 帧解码优化")
        public boolean kryptonFastVarint21FrameDecoder = true;

        @GlobalConfig(value = "packet-nbt-size-limit", validator = PacketNbtSizeLimitValidator.class, comment = "数据包 NBT 大小限制，单位字节，允许范围 2097152 到 2147483647")
        public int packetNbtSizeLimit = 2097152;

        private static class PacketNbtSizeLimitValidator extends IntConfigValidator {
            @Override
            public void verify(Integer old, Integer value) throws IllegalArgumentException {
                if (value < 2097152) {
                    throw new IllegalArgumentException("packet-nbt-size-limit need >= 2097152");
                }
            }
        }

        public FakeplayerConfig fakeplayer = new FakeplayerConfig();

        @GlobalConfigCategory(value = "fakeplayer", comment = "假人（Bot）系统相关配置")
        public static class FakeplayerConfig {

            @TransferConfig("fakeplayer.enable")
            @GlobalConfig(value = "enable", validator = FakeplayerValidator.class, comment = "是否启用假人功能")
            public boolean enable = true;

            private static class FakeplayerValidator extends BooleanConfigValidator {
                @Override
                public void verify(Boolean old, Boolean value) throws IllegalArgumentException {
                    if (!value.equals(old)) {
                        if (value) {
                            BotCommand.INSTANCE.register();
                        } else {
                            BotCommand.INSTANCE.unregister();
                            BotList.INSTANCE.removeAll();
                        }
                    }
                }
            }

            @TransferConfig("fakeplayer.unable-fakeplayer-names")
            @GlobalConfig(value = "unable-fakeplayer-names", comment = "禁止使用的假人名称列表")
            public List<String> unableNames = List.of("player-name");

            @GlobalConfig(value = "limit", comment = "每个玩家可创建的假人数量上限")
            public int limit = 10;

            @GlobalConfig(value = "prefix", comment = "假人名称前缀")
            public String prefix = "";

            @GlobalConfig(value = "suffix", comment = "假人名称后缀")
            public String suffix = "";

            @GlobalConfig(value = "regen-amount", validator = RegenAmountValidator.class, comment = "假人每 tick 恢复的生命值，0.0 表示不恢复")
            public double regenAmount = 0.0;

            private static class RegenAmountValidator extends DoubleConfigValidator {
                @Override
                public void verify(Double old, Double value) throws IllegalArgumentException {
                    if (value < 0.0) {
                        throw new IllegalArgumentException("regen-amount need >= 0.0");
                    }
                }
            }

            @GlobalConfig(value = "resident-fakeplayer", comment = "假人是否在服务器重启后仍然保留")
            public boolean canResident = false;

            @GlobalConfig(value = "open-fakeplayer-inventory", comment = "是否允许玩家打开假人的物品栏")
            public boolean canOpenInventory = false;

            @GlobalConfig(value = "use-action", validator = CanUseConfigValidator.class, comment = "是否允许假人执行 use 操作")
            public boolean canUseAction = true;

            private static class CanUseConfigValidator extends BotSubcommandValidator {
                private CanUseConfigValidator() {
                    super("use");
                }
            }

            @GlobalConfig(value = "modify-config", validator = CanModifyConfigValidator.class, comment = "是否允许使用假人修改配置的命令")
            public boolean canModifyConfig = false;

            private static class CanModifyConfigValidator extends BotSubcommandValidator {
                private CanModifyConfigValidator() {
                    super("config");
                }
            }

            @GlobalConfig(value = "manual-save-and-load", validator = CanManualSaveAndLoadValidator.class, comment = "是否允许手动保存/加载假人")
            public boolean canManualSaveAndLoad = false;

            private static class CanManualSaveAndLoadValidator extends BotSubcommandValidator {
                private CanManualSaveAndLoadValidator() {
                    super("save", "load");
                }
            }

            private static class BotSubcommandValidator extends BooleanConfigValidator {
                private final List<String> subcommands;

                private BotSubcommandValidator(String... subcommand) {
                    this.subcommands = List.of(subcommand);
                }

                @Override
                public void verify(Boolean old, Boolean value) throws IllegalArgumentException {
                    if (old != null && !old.equals(value)) {
                        Bukkit.getOnlinePlayers().stream()
                            .filter(sender -> subcommands.stream().allMatch(subcommand -> BotCommand.hasPermission(sender, subcommand)))
                            .forEach(org.bukkit.entity.Player::updateCommands);
                    }
                }
            }

            @GlobalConfig(value = "cache-skin", lock = true, comment = "是否缓存假人的皮肤信息")
            public boolean useSkinCache = false;

            public InGameConfig inGame = new InGameConfig();

            @GlobalConfigCategory(value = "in-game", comment = "假人在游戏中的行为配置")
        public static class InGameConfig {

            @TransferConfig("modify.fakeplayer.always-send-data")
            @GlobalConfig(value = "always-send-data", comment = "是否始终向假人发送区块数据")
            public boolean canSendDataAlways = true;

            @TransferConfig("modify.fakeplayer.skip-sleep-check")
            @GlobalConfig(value = "skip-sleep-check", comment = "假人是否跳过睡眠检查")
            public boolean canSkipSleep = false;

            @TransferConfig("modify.fakeplayer.spawn-phantom")
            @GlobalConfig(value = "spawn-phantom", comment = "假人周围是否会生成幻翼")
            public boolean canSpawnPhantom = false;

            @TransferConfig("modify.fakeplayer.tick-type")
            @GlobalConfig(value = "tick-type", comment = "假人的 Tick 类型：NETWORK（跟随网络）或 MAIN（跟随主线程）")
            public ServerBot.TickType tickType = ServerBot.TickType.NETWORK;

            @GlobalConfig(value = "simulation-distance", validator = BotSimulationDistanceValidator.class, comment = "假人的模拟距离，-1 表示使用服务器的模拟距离")
                private int simulationDistance = -1;

                public int getSimulationDistance(ServerBot bot) {
                    return this.simulationDistance == -1 ? bot.getBukkitEntity().getSimulationDistance() : this.simulationDistance;
                }

                public static class BotSimulationDistanceValidator extends IntConfigValidator {
                    @Override
                    public void verify(Integer old, Integer value) throws IllegalArgumentException {
                        if ((value < 2 && value != -1) || value > 32) {
                            throw new IllegalArgumentException("simulation-distance must be a number between 2 and 32, got: " + value);
                        }
                    }
                }

                @GlobalConfig(value = "enable-locator-bar", comment = "是否允许假人显示定位栏")
                public boolean enableLocatorBar = false;
            }
        }

        public MinecraftOLDConfig oldMC = new MinecraftOLDConfig();

        @GlobalConfigCategory(value = "minecraft-old", comment = "还原旧版 Minecraft 的行为")
        public static class MinecraftOLDConfig {

            public BlockUpdaterConfig updater = new BlockUpdaterConfig();

            @GlobalConfigCategory(value = "block-updater", comment = "方块更新器相关配置")
            public static class BlockUpdaterConfig {
                @TransferConfig("modify.instant-block-updater-reintroduced")
                @TransferConfig("modify.minecraft-old.instant-block-updater-reintroduced")
                @GlobalConfig(value = "instant-block-updater-reintroduced", lock = true, comment = "是否重新引入即时方块更新器（1.19 前的行为）")
                public boolean instantBlockUpdaterReintroduced = false;

                @TransferConfig("modify.minecraft-old.cce-update-suppression")
                @GlobalConfig(value = "cce-update-suppression", comment = "是否启用 CCE（ClassCastException）更新抑制")
                public boolean cceUpdateSuppression = false;

                @GlobalConfig(value = "sound-update-suppression", comment = "是否启用声音更新抑制")
                public boolean soundUpdateSuppression = false;

                @TransferConfig("modify.redstone-wire-dont-connect-if-on-trapdoor")
                @TransferConfig("modify.minecraft-old.redstone-wire-dont-connect-if-on-trapdoor")
                @TransferConfig("modify.minecraft-old.block-updater.redstone-wire-dont-connect-if-on-trapdoor")
                @GlobalConfig(value = "redstone-ignore-upwards-update", comment = "红石线是否忽略向上的方块更新")
                public boolean redstoneIgnoreUpwardsUpdate = false;

                @TransferConfig("modify.minecraft-old.old-block-entity-behaviour")
                @TransferConfig("modify.minecraft-old.block-updater.old-block-entity-behaviour")
                @GlobalConfig(value = "old-block-remove-behaviour", comment = "是否使用旧版方块移除行为")
                public boolean oldBlockRemoveBehaviour = false;
            }

            @TransferConfig("shears-in-dispenser-can-zero-amount")
            @TransferConfig("modify.shears-in-dispenser-can-zero-amount")
            @GlobalConfig(value = "shears-in-dispenser-can-zero-amount", comment = "发射器中的剪刀使用后耐久是否可以降为 0")
            public boolean shearsInDispenserCanZeroAmount = false;

            @SuppressWarnings("unused")
            @GlobalConfig(value = "villager-infinite-discounts", validator = VillagerInfiniteDiscountsValidator.class, comment = "村民交易折扣是否可以无限叠加")
            private boolean villagerInfiniteDiscounts = true;

            private static class VillagerInfiniteDiscountsValidator extends BooleanConfigValidator {
                @Override
                public void verify(Boolean old, Boolean value) throws IllegalArgumentException {
                    VillagerInfiniteDiscountHelper.doVillagerInfiniteDiscount(value);
                }
            }

            @GlobalConfig(value = "copper-bulb-1gt-delay", comment = "铜灯是否使用 1gt 延迟（旧版行为）")
            public boolean copperBulb1gt = false;

            @GlobalConfig(value = "crafter-1gt-delay", comment = "合成器是否使用 1gt 延迟（旧版行为）")
            public boolean crafter1gt = false;

            @TransferConfig("modify.zero-tick-plants")
            @GlobalConfig(value = "zero-tick-plants", comment = "是否允许零刻植物生长（1.15 前的行为）")
            public boolean zeroTickPlants = false;

            @TransferConfig("modify.minecraft-old.loot-world-random")
            @GlobalConfig(value = "rng-fishing", lock = true, validator = RNGFishingValidator.class, comment = "是否启用 RNG 钓鱼（可操控钓鱼随机数）")
            public boolean rngFishing = false;

            private static class RNGFishingValidator extends BooleanConfigValidator {
                @Override
                public void verify(Boolean old, Boolean value) throws IllegalArgumentException {
                    LeavesFeatureSet.register(LeavesFeature.of("rng_fishing", value));
                }
            }

            @GlobalConfig(value = "allow-entity-portal-with-passenger", comment = "骑乘实体时是否允许通过传送门")
            public boolean allowEntityPortalWithPassenger = true;

            @GlobalConfig(value = "disable-gateway-portal-entity-ticking", comment = "是否禁止末地折跃门实体的 Tick")
            public boolean disableGatewayPortalEntityTicking = false;

            @GlobalConfig(value = "disable-LivingEntity-ai-step-alive-check", comment = "是否禁用 LivingEntity aiStep 中的存活检查")
            public boolean disableLivingEntityAiStepAliveCheck = true;

            @GlobalConfig(value = "spawn-invulnerable-time", comment = "是否使用旧版生物出生无敌时间行为")
            public boolean spawnInvulnerableTime = false;

            @GlobalConfig(value = "old-hopper-suck-in-behavior", comment = "是否使用旧版漏斗吸取行为")
            public boolean oldHopperSuckInBehavior = false;

            @GlobalConfig(value = "old-zombie-piglin-drop", comment = "是否使用旧版僵尸猪灵掉落行为")
            public boolean oldZombiePiglinDrop = false;

            @TransferConfig(value = "modify.minecraft-old.revert-raid-changes", transformer = RaidConfigTransformer.class)
            @GlobalConfig(value = "old-raid-behavior", comment = "是否使用旧版袭击行为")
            public boolean oldRaidBehavior = false;

            public static class RaidConfigTransformer implements ConfigTransformer<MemorySection, Boolean> {
                @Override
                public Boolean transform(@NotNull MemorySection raidConfig) {
                    return raidConfig.getBoolean("allow-bad-omen-trigger-raid")
                        || raidConfig.getBoolean("give-bad-omen-when-kill-patrol-leader")
                        || raidConfig.getBoolean("skip-height-check")
                        || raidConfig.getBoolean("use-old-find-spawn-position");
                }
            }

            @GlobalConfig(value = "old-zombie-reinforcement", comment = "是否使用旧版僵尸增援行为")
            public boolean oldZombieReinforcement = false;

            @GlobalConfig(value = "allow-anvil-destroy-item-entities", comment = "是否允许铁砧破坏掉落物实体")
            public boolean allowAnvilDestroyItemEntities = false;

            public TripwireConfig tripwire = new TripwireConfig();

            @GlobalConfigCategory(value = "tripwire-and-hook-behavior", comment = "绊线和绊线钩的行为配置")
            public static class TripwireConfig {
                @TransferConfig("modify.minecraft-old.string-tripwire-hook-duplicate")
                @GlobalConfig(value = "string-tripwire-hook-duplicate", comment = "是否启用线-绊线钩复制行为")
                public boolean stringTripwireHookDuplicate = true;

                @GlobalConfig(value = "tripwire-behavior", comment = "绊线行为模式：VANILLA_20、VANILLA_21 或 MIXED")
                public TripwireBehavior tripwireBehavior = TripwireBehavior.VANILLA_21;

                public enum TripwireBehavior {
                    VANILLA_20, VANILLA_21, MIXED
                }
            }

            @GlobalConfig(value = "void-trade", validator = VoidTradeValidator.class, comment = "是否启用虚空交易（在虚空中与村民交易）")
            public boolean voidTrade = true;

            private static class VoidTradeValidator extends BooleanConfigValidator {
                @Override
                public void verify(Boolean old, Boolean value) throws IllegalArgumentException {
                    if (!value && old != null && LeavesConfig.modify.forceVoidTrade) {
                        throw new IllegalArgumentException("force-void-trade is enable, void-trade always need true");
                    }
                }
            }

            @GlobalConfig(value = "disable-item-damage-check", comment = "是否禁用物品损伤检查")
            public boolean disableItemDamageCheck = false;

            @GlobalConfig(value = "old-throwable-projectile-tick-order", comment = "是否使用旧版投掷物 Tick 顺序")
            public boolean oldThrowableProjectileTickOrder = false;

            @GlobalConfig(value = "keep-leash-connect-when-use-firework", comment = "使用烟花时是否保持拴绳连接")
            public boolean keepLeashConnectWhenUseFirework = false;

            @GlobalConfig(value = "tnt-wet-explosion-no-item-damage", comment = " TNT 在水中爆炸时是否不破坏物品")
            public boolean tntWetExplosionNoItemDamage = false;

            @GlobalConfig(value = "old-projectile-explosion-behavior", comment = "是否使用旧版投射物爆炸行为")
            public boolean oldProjectileExplosionBehavior = false;

            @GlobalConfig(value = "ender-dragon-part-can-use-end-portal", comment = "末影龙的部分实体是否可以使用末地传送门")
            public boolean enderDragonPartCanUseEndPortal = false;

            @GlobalConfig(value = "old-minecart-motion-behavior", comment = "是否使用旧版矿车运动行为")
            public boolean oldMinecartMotionBehavior = false;

            @GlobalConfig(value = "allow-inf-nan-motion-values", comment = "是否允许无穷大和 NaN 的运动值")
            public boolean allowInfNanMotionValues = true;
        }

        public ElytraAeronauticsConfig elytraAeronautics = new ElytraAeronauticsConfig();

        @GlobalConfigCategory(value = "elytra-aeronautics", comment = "鞘翅巡航模式配置")
        public static class ElytraAeronauticsConfig {
            @GlobalConfig(value = "no-chunk-load", comment = "启用后鞘翅飞行时不加载新区块")
            public boolean enableNoChunkLoad = false;

            @GlobalConfig(value = "no-chunk-height", comment = "不加载区块的高度阈值，超过此高度触发巡航模式")
            public double noChunkHeight = 500.0D;

            @GlobalConfig(value = "no-chunk-speed", comment = "不加载区块的速度阈值，-1 表示不限制")
            public double noChunkSpeed = -1.0D;

            @GlobalConfig(value = "message", comment = "进入/退出巡航模式时是否发送消息")
            public boolean doSendMessages = true;

            @GlobalConfig(value = "message-start", comment = "进入巡航模式时发送的消息")
            public String startMessage = "Flight enter cruise mode";

            @GlobalConfig(value = "message-end", comment = "退出巡航模式时发送的消息")
            public String endMessage = "Flight exit cruise mode";
        }

        @TransferConfig("redstone-shears-wrench")
        @GlobalConfig(value = "redstone-shears-wrench", comment = "是否允许用剪刀右键红石元件来调整方向")
        public boolean redstoneShearsWrench = false;

        @TransferConfig("budding-amethyst-can-push-by-piston")
        @TransferConfig("modify.budding-amethyst-can-push-by-piston")
        @GlobalConfig(value = "movable-budding-amethyst", validator = MovableBuddingAmethystValidator.class, comment = "是否允许活塞推动紫水晶芽")
        public boolean movableBuddingAmethyst = true;

        private static class MovableBuddingAmethystValidator extends BooleanConfigValidator {
            @Override
            public void verify(Boolean old, Boolean value) throws IllegalArgumentException {
                CarpetRules.register(CarpetRule.of("carpet", "movableAmethyst", value));
            }
        }

        @TransferConfig("spectator-dont-get-advancement")
        @GlobalConfig(value = "spectator-dont-get-advancement", comment = "旁观者模式是否不会获得进度")
        public boolean spectatorDontGetAdvancement = false;

        @TransferConfig("stick-change-armorstand-arm-status")
        @GlobalConfig(value = "stick-change-armorstand-arm-status", comment = "是否允许用木棍切换盔甲架的手臂状态")
        public boolean stickChangeArmorStandArmStatus = true;

        @TransferConfig("snowball-and-egg-can-knockback-player")
        @GlobalConfig(value = "snowball-and-egg-can-knockback-player", comment = "雪球和鸡蛋是否能击退玩家")
        public boolean snowballAndEggCanKnockback = true;

        @GlobalConfig(value = "flatten-triangular-distribution", comment = "是否将三角形分布改为均匀分布")
        public boolean flattenTriangularDistribution = false;

        @GlobalConfig(value = "player-operation-limiter", comment = "是否限制玩家每秒操作次数")
        public boolean playerOperationLimiter = false;

        @GlobalConfig(value = "renewable-elytra", validator = RenewableElytraValidator.class, comment = "鞘翅可再生概率，-1.0 表示关闭，最大 1.0")
        public double renewableElytra = -1.0F;

        private static class RenewableElytraValidator extends DoubleConfigValidator {
            @Override
            public void verify(Double old, Double value) throws IllegalArgumentException {
                if (value > 1.0) {
                    throw new IllegalArgumentException("renewable-elytra need <= 1.0f");
                }
            }
        }

        public ShulkerBoxConfig shulkerBox = new ShulkerBoxConfig();

        @GlobalConfigCategory(value = "shulker-box", comment = "潜影盒相关配置")
        public static class ShulkerBoxConfig {

            @TransferConfig("modify.stackable-shulker-boxes")
            @GlobalConfig(value = "stackable-shulker-boxes", validator = StackableShulkerValidator.class, comment = "潜影盒最大堆叠数量，1 表示不可堆叠，2+ 表示可堆叠")
            public int stackableShulkerBoxes = 64;

            private static class StackableShulkerValidator implements ConfigValidator<Integer> {

                @Override
                public Integer loadConvert(Object value) throws IllegalArgumentException {
                    return switch (value) {
                        case String stringValue -> stringValue.equals("true") ? 2
                            : !MathUtils.isNumeric(stringValue) ? 1
                            : Integer.parseInt(stringValue);
                        case Integer integerValue -> integerValue;
                        case Boolean boolValue -> boolValue ? 2 : 1;
                        case null, default -> throw new IllegalArgumentException("stackable-shulker-boxes need string or integer or boolean");
                    };
                }

                @Override
                public Object saveConvert(Integer value) {
                    return value == 1 ? false
                        : value == 2 ? true
                        : value;
                }

                @Override
                public Integer stringConvert(String value) throws IllegalArgumentException {
                    return loadConvert(value);
                }

                @Override
                public void verify(Integer old, Integer value) throws IllegalArgumentException {
                    if (value < 1 || value > 99) {
                        throw new IllegalArgumentException("stackable-shulker-boxes need >= 1 and <= 99");
                    }
                }

                @Override
                public List<String> valueSuggest() {
                    return List.of("true", "false", "64", "32");
                }
            }

            @GlobalConfig(value = "same-nbt-stackable", comment = "NBT 相同的物品是否可堆叠")
            public boolean sameNbtStackable = true;
        }

        @GlobalConfig(value = "force-void-trade", validator = ForceVoidTradeValidator.class, comment = "强制启用虚空交易，开启后 void-trade 也会被强制开启")
        public boolean forceVoidTrade = true;

        private static class ForceVoidTradeValidator extends BooleanConfigValidator {
            @Override
            public void verify(Boolean old, Boolean value) throws IllegalArgumentException {
                if (value) {
                    LeavesConfig.modify.oldMC.voidTrade = true;
                }
            }

            @Override
            public void runAfterLoader(Boolean value, boolean reload) {
                if (value) {
                    LeavesConfig.modify.oldMC.voidTrade = true;
                }
            }
        }

        @GlobalConfig(value = "mc-technical-survival-mode", validator = McTechnicalModeValidator.class, lock = true, comment = "是否启用 Minecraft 技术生存模式（限制部分非技术玩法）")
        public boolean mcTechnicalMode = true;

        private static class McTechnicalModeValidator extends BooleanConfigValidator {
            @Override
            public void verify(Boolean old, Boolean value) throws IllegalArgumentException {
                if (value) {
                    McTechnicalModeHelper.doMcTechnicalMode();
                }
            }
        }

        @GlobalConfig(value = "return-nether-portal-fix", comment = "是否修复从下界传送门返回时的位置问题")
        public boolean netherPortalFix = false;

        @GlobalConfig(value = "use-vanilla-random", lock = true, validator = UseVanillaRandomValidator.class, comment = "是否使用原版随机数生成器（影响随机数可预测性）")
        public boolean useVanillaRandom = false;

        private static class UseVanillaRandomValidator extends BooleanConfigValidator {
            @Override
            public void verify(Boolean old, Boolean value) throws IllegalArgumentException {
                LeavesFeatureSet.register(LeavesFeature.of("use_vanilla_random", value));
            }
        }

        @GlobalConfig(value = "fix-update-suppression-crash", comment = "是否修复更新抑制导致的崩溃")
        public boolean updateSuppressionCrashFix = true;

        @GlobalConfig(value = "bedrock-break-list", lock = true, comment = "是否允许在基岩上使用床来破坏基岩")
        public boolean bedrockBreakList = false;

        @GlobalConfig(value = "disable-distance-check-for-use-item", validator = DisableDistanceCheckForUseItemValidator.class, comment = "是否禁用使用物品时的距离检查")
        public boolean disableDistanceCheckForUseItem = false;

        private static class DisableDistanceCheckForUseItemValidator extends BooleanConfigValidator {
            @Override
            public void verify(Boolean old, Boolean value) throws IllegalArgumentException {
                if (!value && old != null && LeavesConfig.protocol.alternativeBlockPlacement != ProtocolConfig.AlternativePlaceType.NONE) {
                    throw new IllegalArgumentException("alternative-block-placement is enable, disable-distance-check-for-use-item always need true");
                }
            }
        }

        @GlobalConfig(value = "no-feather-falling-trample", comment = "穿着摔落保护靴子时是否不会踩坏农田")
        public boolean noFeatherFallingTrample = true;

        @GlobalConfig(value = "shared-villager-discounts", comment = "是否共享村民交易折扣（所有玩家共享同一折扣）")
        public boolean sharedVillagerDiscounts = true;

        @GlobalConfig(value = "disable-check-out-of-order-command", comment = "是否禁用命令乱序检查")
        public boolean disableCheckOutOfOrderCommand = false;

        @GlobalConfig(value = "despawn-enderman-with-block", comment = "手持方块的末影人是否会自然消失")
        public boolean despawnEndermanWithBlock = false;

        @GlobalConfig(value = "creative-no-clip", validator = CreativeNoClipValidator.class, comment = "创造模式玩家是否可以穿透方块（无碰撞）")
        public boolean creativeNoClip = false;

        private static class CreativeNoClipValidator extends BooleanConfigValidator {
            @Override
            public void verify(Boolean old, Boolean value) throws IllegalArgumentException {
                CarpetRules.register(CarpetRule.of("carpet", "creativeNoClip", value));
            }
        }

        @GlobalConfig(value = "shave-snow-layers", comment = "是否允许用铲子逐层去除雪层")
        public boolean shaveSnowLayers = true;

        @GlobalConfig(value = "disable-packet-limit", comment = "是否禁用数据包频率限制")
        public boolean disablePacketLimit = true;

        @GlobalConfig(value = "lava-riptide", validator = LavaRiptideValidator.class, comment = "是否允许在岩浆中使用激流三叉戟")
        public boolean lavaRiptide = true;

        private static class LavaRiptideValidator extends BooleanConfigValidator {
            @Override
            public void verify(Boolean old, Boolean value) throws IllegalArgumentException {
                LeavesFeatureSet.register(LeavesFeature.of("lava_riptide", value));
            }
        }

        @GlobalConfig(value = "no-block-update-command", validator = NoBlockUpdateValidator.class, comment = "是否启用 noblockupdate 命令（禁止方块更新）")
        public boolean noBlockUpdateCommand = true;

        private static class NoBlockUpdateValidator extends BooleanConfigValidator {
            @Override
            public void verify(Boolean old, Boolean value) throws IllegalArgumentException {
                if (old != null && !old.equals(value)) {
                    Bukkit.getOnlinePlayers().stream()
                        .filter(sender -> LeavesCommand.hasPermission(sender, "blockupdate"))
                        .forEach(org.bukkit.entity.Player::updateCommands);
                }
            }
        }

        @GlobalConfig(value = "no-tnt-place-update", comment = "放置 TNT 时是否不触发方块更新")
        public boolean noTNTPlaceUpdate = false;

        @GlobalConfig(value = "container-passthrough", comment = "是否允许在容器上放置方块时仍然可以打开容器")
        public boolean containerPassthrough = true;

        @GlobalConfig(value = "avoid-anvil-too-expensive", validator = AnvilNotExpensiveValidator.class, comment = "是否移除铁砧的「太贵了」限制")
        public boolean avoidAnvilTooExpensive = true;

        private static class AnvilNotExpensiveValidator extends BooleanConfigValidator {
            @Override
            public void verify(Boolean old, Boolean value) throws IllegalArgumentException {
                CarpetRules.register(CarpetRule.of("pca", "avoidAnvilTooExpensive", value));
            }
        }

        @GlobalConfig(value = "bow-infinity-fix", comment = "是否修复无限附魔弓射箭不消耗箭的问题")
        public boolean bowInfinityFix = true;

        public HopperCounterConfig hopperCounter = new HopperCounterConfig();

        @GlobalConfigCategory(value = "hopper-counter", comment = "漏斗计数器配置")
        public static class HopperCounterConfig {
            @TransferConfig(value = "modify.hopper-counter", transformer = HopperCounterTransfer.class)
            @TransferConfig("modify.counter.enable")
            @GlobalConfig(value = "enable", validator = HopperCounterValidator.class, comment = "是否启用漏斗计数器功能")
            public boolean enable = false;

            private static class HopperCounterValidator extends BooleanConfigValidator {
                @Override
                public void verify(Boolean old, Boolean value) throws IllegalArgumentException {
                    if (old != null && !old.equals(value)) {
                        Bukkit.getOnlinePlayers().stream()
                            .filter(sender -> LeavesCommand.hasPermission(sender, "counter"))
                            .forEach(org.bukkit.entity.Player::updateCommands);
                    }
                }
            }

            @TransferConfig("modify.counter.unlimited-speed")
            @GlobalConfig(value = "unlimited-speed", comment = "漏斗计数器是否使用无限制传输速度")
            public boolean unlimitedSpeed = false;

            private static class HopperCounterTransfer implements ConfigTransformer<Object, Boolean> {
                @Override
                public Boolean transform(Object object) throws StopTransformException {
                    if (object instanceof Boolean) {
                        return (Boolean) object;
                    } else {
                        throw new StopTransformException();
                    }
                }
            }
        }

        @GlobalConfig(value = "spider-jockeys-drop-gapples", validator = JockeysDropGAppleValidator.class, comment = "蜘蛛骑士掉落金苹果的概率，-1.0 表示关闭，最大 1.0")
        public double spiderJockeysDropGapples = -1.0;

        private static class JockeysDropGAppleValidator extends DoubleConfigValidator {
            @Override
            public void verify(Double old, Double value) throws IllegalArgumentException {
                if (value > 1.0) {
                    throw new IllegalArgumentException("spider-jockeys-drop-gapples need <= 1.0f");
                }
            }
        }

        @GlobalConfig(value = "renewable-deepslate", comment = "是否启用可再生深板岩（基岩+岩浆+水）")
        public boolean renewableDeepslate = true;

        @GlobalConfig(value = "renewable-sponges", comment = "是否启用可再生海绵")
        public boolean renewableSponges = true;

        @GlobalConfig(value = "renewable-coral", validator = RenewableCoralValidator.class, comment = "可再生珊瑚模式：FALSE 关闭、TRUE 普通、EXPANDED 扩展")
        public RenewableCoralType renewableCoral = RenewableCoralType.EXPANDED;

        public enum RenewableCoralType {
            FALSE, TRUE, EXPANDED
        }

        private static class RenewableCoralValidator extends EnumConfigValidator<RenewableCoralType> {
            @Override
            public void verify(RenewableCoralType old, RenewableCoralType value) throws IllegalArgumentException {
                CarpetRules.register(CarpetRule.of("carpet", "renewableCoral", value));
            }
        }

        @GlobalConfig(value = "disable-vault-blacklist", comment = "是否禁用宝库黑名单")
        public boolean disableVaultBlacklist = false;

        @SuppressWarnings("unused")
        @GlobalConfig(value = "exp-orb-absorb-mode", validator = ExpOrbModeValidator.class, comment = "经验球吸收模式：VANILLA 原版、FAST 快速、FAST_CREATIVE 创造模式快速")
        private ExpOrbAbsorbMode expOrbAbsorbMode = ExpOrbAbsorbMode.VANILLA;

        public Predicate<ServerPlayer> fastAbsorbPredicate = player -> false;

        public enum ExpOrbAbsorbMode {
            VANILLA, FAST, FAST_CREATIVE
        }

        private static class ExpOrbModeValidator extends EnumConfigValidator<ExpOrbAbsorbMode> {
            @Override
            public void verify(ExpOrbAbsorbMode old, ExpOrbAbsorbMode value) throws IllegalArgumentException {
                LeavesConfig.modify.fastAbsorbPredicate = switch (value) {
                    case FAST -> player -> true;
                    case VANILLA -> player -> false;
                    case FAST_CREATIVE -> Player::hasInfiniteMaterials;
                };
            }
        }

        @GlobalConfig(value = "follow-tick-sequence-merge", comment = "是否遵循 Tick 顺序进行实体合并")
        public boolean followTickSequenceMerge = false;

        @GlobalConfig(value = "rain-extinguish-campfires", comment = "下雨时是否熄灭露天营火")
        public boolean rainExtinguishCampfires = true;
    }

    public static PerformanceConfig performance = new PerformanceConfig();

    @GlobalConfigCategory(value = "performance", comment = "性能优化相关配置")
    public static class PerformanceConfig {

        public PerformanceRemoveConfig remove = new PerformanceRemoveConfig();

        @GlobalConfigCategory(value = "remove", comment = "移除特定功能的配置")
        public static class PerformanceRemoveConfig {
            @GlobalConfig(value = "tick-guard-lambda", comment = "是否移除 Tick 守卫的 Lambda 表达式以减少开销")
            public boolean tickGuardLambda = true;

            @GlobalConfig(value = "damage-lambda", comment = "是否移除伤害计算的 Lambda 表达式以减少开销")
            public boolean damageLambda = true;
        }

        @GlobalConfig(value = "optimized-dragon-respawn", comment = "是否优化末影龙重生逻辑")
        public boolean optimizedDragonRespawn = false;

        @GlobalConfig(value = "dont-send-useless-entity-packets", comment = "是否不发送无用的实体数据包")
        public boolean dontSendUselessEntityPackets = true;

        @GlobalConfig(value = "enable-suffocation-optimization", comment = "是否启用实体窒息优化")
        public boolean enableSuffocationOptimization = true;

        @GlobalConfig(value = "check-spooky-season-once-an-hour", comment = "是否每小时仅检查一次万圣节季节")
        public boolean checkSpookySeasonOnceAnHour = true;

        @GlobalConfig(value = "inactive-goal-selector-disable", comment = "是否禁用不活跃实体的目标选择器 Tick")
        public boolean throttleInactiveGoalSelectorTick = false;

        @GlobalConfig(value = "reduce-entity-allocations", comment = "是否减少实体对象分配以降低 GC 压力")
        public boolean reduceEntityAllocations = true;

        @GlobalConfig(value = "cache-climb-check", comment = "是否缓存攀爬检查结果")
        public boolean cacheClimbCheck = true;

        @GlobalConfig(value = "reduce-chuck-load-and-lookup", comment = "是否减少区块加载和查找操作")
        public boolean reduceChuckLoadAndLookup = true;

        @GlobalConfig(value = "cache-ignite-odds", comment = "是否缓存点燃概率计算结果")
        public boolean cacheIgniteOdds = true;

        @GlobalConfig(value = "faster-chunk-serialization", comment = "是否启用更快的区块序列化")
        public boolean fasterChunkSerialization = false;

        @GlobalConfig(value = "skip-secondary-POI-sensor-if-absent", comment = "当次要 POI 不存在时是否跳过传感器检测")
        public boolean skipSecondaryPOISensorIfAbsent = true;

        @GlobalConfig(value = "store-mob-counts-in-array", comment = "是否使用数组存储生物数量以提高性能")
        public boolean storeMobCountsInArray = true;

        @GlobalConfig(value = "optimize-noise-generation", comment = "是否优化噪声生成算法")
        public boolean optimizeNoiseGeneration = true;

        @GlobalConfig(value = "optimize-sun-burn-tick", comment = "是否优化阳光灼烧 Tick")
        public boolean optimizeSunBurnTick = true;

        @GlobalConfig(value = "optimized-CubePointRange", comment = "是否优化 CubePointRange 计算")
        public boolean optimizedCubePointRange = true;

        @GlobalConfig(value = "check-frozen-ticks-before-landing-block", comment = "着陆前是否先检查冰冻 Tick")
        public boolean checkFrozenTicksBeforeLandingBlock = true;

        @GlobalConfig(value = "skip-entity-move-if-movement-is-zero", comment = "当移动向量为零时是否跳过实体移动计算")
        public boolean skipEntityMoveIfMovementIsZero = true;

        @GlobalConfig(value = "skip-cloning-advancement-criteria", comment = "是否跳过进度条件的克隆操作")
        public boolean skipCloningAdvancementCriteria = false;

        @GlobalConfig(value = "skip-negligible-planar-movement-multiplication", comment = "是否跳过可忽略的平面运动乘法计算")
        public boolean skipNegligiblePlanarMovementMultiplication = true;

        @GlobalConfig(value = "sleeping-block-entity", lock = true, comment = "是否启用方块实体休眠（不活跃时跳过 Tick）")
        public boolean sleepingBlockEntity = false;

        @GlobalConfig(value = "equipment-tracking", lock = true, comment = "是否启用装备变更追踪优化")
        public boolean equipmentTracking = false;

        @GlobalConfig(value = "shield-sounds-backport", comment = "是否回移植盾牌格挡音效行为")
        public boolean shieldSoundsBackport = true;

        @GlobalConfig(value = "efficient-hashing", comment = "是否启用 Vec3i 高效哈希优化")
        public boolean efficientHashing = true;

        @GlobalConfig(value = "fix-rabbit-pathfinding", comment = "是否修复兔子寻路性能问题")
        public boolean fixRabbitPathfinding = true;

        @GlobalConfig(value = "command-block-parse-cache", comment = "是否缓存命令方块解析结果")
        public boolean commandBlockParseCache = true;

        @GlobalConfig(value = "anti-crash-book-title", comment = "是否限制异常书本标题防止崩溃")
        public boolean antiCrashBookTitle = true;

        @GlobalConfig(value = "recipe-cooldown-ms", comment = "配方请求冷却时间（毫秒）")
        public long recipeCooldownMs = 0L;

        @GlobalConfig(value = "optimize-inventory-change-trigger", comment = "是否优化物品栏变化进度触发器")
        public boolean optimizeInventoryChangeTrigger = false;

        @GlobalConfig(value = "ict-ignore-emptied-stacks", comment = "物品栏变化触发器是否忽略被清空的物品堆")
        public boolean ictIgnoreEmptiedStacks = true;

        @GlobalConfig(value = "rail-power-limit", comment = "充能铁轨递归传播限制")
        public int railPowerLimit = 16;
    }

    public static ProtocolConfig protocol = new ProtocolConfig();

    @GlobalConfigCategory(value = "protocol", comment = "Mod 协议支持相关配置")
    public static class ProtocolConfig {

        public BladerenConfig bladeren = new BladerenConfig();

        @GlobalConfigCategory(value = "bladeren", comment = "Bladeren 协议配置（Leaves 自有 Mod 通信协议）")
        public static class BladerenConfig {
            @GlobalConfig(value = "protocol", comment = "是否启用 Bladeren 协议")
            public boolean enable = true;

            @GlobalConfig(value = "mspt-sync-protocol", validator = MSPTSyncValidator.class, comment = "是否启用 MSPT 同步协议（向客户端发送服务器 TPS）")
            public boolean msptSyncProtocol = false;

            private static class MSPTSyncValidator extends BooleanConfigValidator {
                @Override
                public void verify(Boolean old, Boolean value) throws IllegalArgumentException {
                    LeavesFeatureSet.register(LeavesFeature.of("mspt_sync", value));
                }
            }

            @GlobalConfig(value = "mspt-sync-tick-interval", validator = MSPTSyncIntervalValidator.class, comment = "MSPT 同步的 Tick 间隔（每隔多少 Tick 发送一次）")
            public int msptSyncTickInterval = 20;

            private static class MSPTSyncIntervalValidator extends IntConfigValidator {
                @Override
                public void verify(Integer old, Integer value) throws IllegalArgumentException {
                    if (value <= 0) {
                        throw new IllegalArgumentException("mspt-sync-tick-interval need > 0");
                    }
                }
            }
        }

        public SyncmaticaConfig syncmatica = new SyncmaticaConfig();

        @GlobalConfigCategory(value = "syncmatica", comment = "Syncmatica 协议配置（Mod 端结构共享）")
        public static class SyncmaticaConfig {
            @GlobalConfig(value = "enable", validator = SyncmaticaValidator.class, comment = "是否启用 Syncmatica 协议")
            public boolean enable = false;

            public static class SyncmaticaValidator extends BooleanConfigValidator {
                @Override
                public void verify(Boolean old, Boolean value) throws IllegalArgumentException {
                    SyncmaticaProtocol.init(value);
                }
            }

            @GlobalConfig(value = "quota", comment = "是否启用 Syncmatica 配额限制")
            public boolean useQuota = false;

            @GlobalConfig(value = "quota-limit", comment = "Syncmatica 配额上限（字节）")
            public int quotaLimit = 40000000;
        }

        public PCAConfig pca = new PCAConfig();

        @GlobalConfigCategory(value = "pca", comment = "PCA 同步协议配置（Puzzle 的客户端 Mod）")
        public static class PCAConfig {
            @TransferConfig("protocol.pca-sync-protocol")
            @GlobalConfig(value = "pca-sync-protocol", validator = PcaValidator.class, comment = "是否启用 PCA 同步协议")
            public boolean enable = false;

            public static class PcaValidator extends BooleanConfigValidator {
                @Override
                public void verify(Boolean old, Boolean value) throws IllegalArgumentException {
                    if (old != null && old != value) {
                        PcaSyncProtocol.onConfigModify(value);
                    }
                }
            }

            @TransferConfig("protocol.pca-sync-player-entity")
            @GlobalConfig(value = "pca-sync-player-entity", comment = "PCA 同步玩家实体的范围：NOBODY、BOT、OPS、OPS_AND_SELF、EVERYONE")
            public PcaPlayerEntityType syncPlayerEntity = PcaPlayerEntityType.OPS;

            public enum PcaPlayerEntityType {
                NOBODY, BOT, OPS, OPS_AND_SELF, EVERYONE
            }
        }

        public AppleSkinConfig appleskin = new AppleSkinConfig();

        @GlobalConfigCategory(value = "appleskin", comment = "AppleSkin 协议配置（客户端显示饱和度/饥饿值）")
        public static class AppleSkinConfig {
            @TransferConfig("protocol.appleskin-protocol")
            @GlobalConfig(value = "protocol", comment = "是否启用 AppleSkin 协议")
            public boolean enable = false;

            @GlobalConfig(value = "sync-tick-interval", comment = "AppleSkin 同步的 Tick 间隔")
            public int syncTickInterval = 20;
        }

        public ServuxConfig servux = new ServuxConfig();

        @GlobalConfigCategory(value = "servux", comment = "Servux 协议配置（结构/实体/HUD 同步）")
        public static class ServuxConfig {
            @TransferConfig("protocol.servux-protocol")
            @GlobalConfig(value = "structure-protocol", comment = "是否启用 Servux 结构协议")
            public boolean structureProtocol = false;

            @GlobalConfig(value = "entity-protocol", comment = "是否启用 Servux 实体协议")
            public boolean entityProtocol = false;

            @GlobalConfig(value = "hud-metadata-protocol", comment = "是否启用 Servux HUD 元数据协议")
            public boolean hudMetadataProtocol = false;

            @GlobalConfig(value = "hud-logger-protocol", comment = "是否启用 Servux HUD 日志协议")
            public boolean hudLoggerProtocol = false;

            @GlobalConfig(value = "hud-enabled-loggers", comment = "HUD 启用的日志类型列表")
            public List<DataLogger.Type> hudEnabledLoggers = List.of(DataLogger.Type.TPS, DataLogger.Type.MOB_CAPS);

            @GlobalConfig(value = "hud-update-interval", comment = "HUD 更新间隔（秒）")
            public int hudUpdateInterval = 1;

            @GlobalConfig(value = "hud-metadata-protocol-share-seed", comment = "HUD 元数据协议是否共享世界种子")
            public boolean hudMetadataShareSeed = true;

            public LitematicsConfig litematics = new LitematicsConfig();

            @GlobalConfigCategory(value = "litematics", comment = "Litematica 投影协议配置")
            public static class LitematicsConfig {

                @TransferConfig("protocol.servux.litematics-protocol")
                @GlobalConfig(value = "enable", validator = LitematicsProtocolValidator.class, comment = "是否启用 Litematica 投影协议")
                public boolean enable = false;

                @GlobalConfig(value = "max-nbt-size", validator = MaxNbtSizeValidator.class, comment = "Litematica 投影的最大 NBT 大小（字节）")
                public long maxNbtSize = 2097152;

                public static class MaxNbtSizeValidator extends LongConfigValidator {

                    @Override
                    public void verify(Long old, Long value) throws IllegalArgumentException {
                        if (value <= 0) {
                            throw new IllegalArgumentException("Max nbt size can not be <= 0");
                        }
                    }
                }

                public static class LitematicsProtocolValidator extends BooleanConfigValidator {
                    @Override
                    public void verify(Boolean old, Boolean value) throws IllegalArgumentException {
                        PluginManager pluginManager = MinecraftServer.getServer().server.getPluginManager();
                        if (value) {
                            if (pluginManager.getPermission("leaves.protocol.litematics") == null) {
                                pluginManager.addPermission(new Permission("leaves.protocol.litematics", PermissionDefault.OP));
                            }
                        } else {
                            pluginManager.removePermission("leaves.protocol.litematics");
                        }
                    }
                }
            }
        }

        @GlobalConfig(value = "bbor-protocol", comment = "是否启用 BBOR（Bounding Box Outline Reloaded）协议")
        public boolean bborProtocol = false;

        @GlobalConfig(value = "jade-protocol", comment = "是否启用 Jade 协议（客户端信息显示 Mod）")
        public boolean jadeProtocol = false;

        @GlobalConfig(value = "alternative-block-placement", validator = AlternativePlaceValidator.class, comment = "替代方块放置模式：NONE、CARPET、CARPET_FIX、LITEMATICA")
        public AlternativePlaceType alternativeBlockPlacement = AlternativePlaceType.NONE;

        public enum AlternativePlaceType {
            NONE, CARPET, CARPET_FIX, LITEMATICA
        }

        private static class AlternativePlaceValidator extends EnumConfigValidator<AlternativePlaceType> {
            @Override
            public void verify(AlternativePlaceType old, AlternativePlaceType value) throws IllegalArgumentException {
                if (value != AlternativePlaceType.NONE) {
                    LeavesConfig.modify.disableDistanceCheckForUseItem = true;
                }
            }

            @Override
            public void runAfterLoader(AlternativePlaceType value, boolean reload) {
                if (value != AlternativePlaceType.NONE) {
                    LeavesConfig.modify.disableDistanceCheckForUseItem = true;
                }
            }
        }

        @GlobalConfig(value = "xaero-map-protocol", comment = "是否启用 Xaero's Map 协议（小地图/世界地图同步）")
        public boolean xaeroMapProtocol = false;

        @GlobalConfig(value = "xaero-map-server-id", comment = "Xaero's Map 的服务器唯一标识 ID")
        public int xaeroMapServerID = new Random().nextInt();

        @GlobalConfig(value = "leaves-carpet-support", comment = "是否启用 Carpet Mod 规则兼容支持")
        public boolean leavesCarpetSupport = false;

        @GlobalConfig(value = "rei-server-protocol", validator = ReiValidator.class, comment = "是否启用 REI（Roughly Enough Items）服务端协议")
        public boolean reiServerProtocol = false;

        public static class ReiValidator extends BooleanConfigValidator {
            @Override
            public void verify(Boolean old, Boolean value) throws IllegalArgumentException {
                if (old != value && value != null) {
                    REIServerProtocol.onConfigModify(value);
                }
            }
        }

        @GlobalConfig(value = "chat-image-protocol", comment = "是否启用 ChatImage 协议（在聊天中发送图片）")
        public boolean chatImageProtocol = false;

        @GlobalConfig(value = "distant-horizons-protocol", comment = "是否启用 Distant Horizons 服务端 LOD 协议支持")
        public boolean distantHorizonsProtocol = true;

        @GlobalConfig(value = "distant-horizons-debug", comment = "是否启用 Distant Horizons 调试日志")
        public boolean distantHorizonsDebug = false;

        @GlobalConfig(value = "distant-horizons-render-distance", comment = "Distant Horizons LOD 渲染距离")
        public int distantHorizonsRenderDistance = 64;

        @GlobalConfig(value = "distant-horizons-distant-generation", comment = "是否启用 Distant Horizons 远距离生成")
        public boolean distantHorizonsDistantGeneration = true;

        @GlobalConfig(value = "distant-horizons-request-concurrency-limit", comment = "Distant Horizons 请求并发限制")
        public int distantHorizonsRequestConcurrencyLimit = 4;

        @GlobalConfig(value = "distant-horizons-real-time-updates", comment = "是否启用 Distant Horizons 实时更新")
        public boolean distantHorizonsRealTimeUpdates = true;

        @GlobalConfig(value = "distant-horizons-real-time-update-radius", comment = "Distant Horizons 实时更新半径")
        public int distantHorizonsRealTimeUpdateRadius = 2;

        @GlobalConfig(value = "distant-horizons-login-data-sync", comment = "是否在登录时同步 Distant Horizons LOD 数据")
        public boolean distantHorizonsLoginDataSync = true;

        @GlobalConfig(value = "distant-horizons-login-data-sync-radius", comment = "登录时同步 Distant Horizons LOD 数据半径")
        public int distantHorizonsLoginDataSyncRadius = 4;

        @GlobalConfig(value = "distant-horizons-login-data-sync-request-concurrency-limit", comment = "登录同步 Distant Horizons LOD 请求并发限制")
        public int distantHorizonsLoginDataSyncRequestConcurrencyLimit = 2;

        @GlobalConfig(value = "distant-horizons-max-data-transfer-speed", comment = "Distant Horizons 最大数据传输速度")
        public int distantHorizonsMaxDataTransferSpeed = 0;

        @GlobalConfig(value = "distant-horizons-scheduler-threads", comment = "Distant Horizons 调度线程数")
        public int distantHorizonsSchedulerThreads = 2;

        @GlobalConfig(value = "distant-horizons-use-vanilla-world-border", comment = "Distant Horizons 是否使用原版世界边界")
        public boolean distantHorizonsUseVanillaWorldBorder = true;

        @GlobalConfig(value = "distant-horizons-lod-refresh-interval", comment = "Distant Horizons LOD 刷新间隔")
        public int distantHorizonsLodRefreshInterval = 20;

        @GlobalConfig(value = "distant-horizons-generate-new-chunks", comment = "Distant Horizons 是否允许生成新区块")
        public boolean distantHorizonsGenerateNewChunks = false;

        @GlobalConfig(value = "distant-horizons-builder-type", comment = "Distant Horizons LOD 构建器类型")
        public String distantHorizonsBuilderType = "AUTO";

        @GlobalConfig(value = "distant-horizons-trust-height-map", comment = "Distant Horizons 是否信任高度图")
        public boolean distantHorizonsTrustHeightMap = true;

        @GlobalConfig(value = "distant-horizons-builder-resolution", comment = "Distant Horizons LOD 构建分辨率")
        public int distantHorizonsBuilderResolution = 2;

        @GlobalConfig(value = "distant-horizons-scan-to-sea-level", comment = "Distant Horizons 是否扫描到海平面")
        public boolean distantHorizonsScanToSeaLevel = true;

        @GlobalConfig(value = "distant-horizons-fast-underfill", comment = "Distant Horizons 是否启用快速底部填充")
        public boolean distantHorizonsFastUnderfill = true;

        @GlobalConfig(value = "distant-horizons-include-non-colliding-top-layer", comment = "Distant Horizons 是否包含非碰撞顶层方块")
        public boolean distantHorizonsIncludeNonCollidingTopLayer = true;

        @GlobalConfig(value = "distant-horizons-perform-underglow-hack", comment = "Distant Horizons 是否执行底部发光修正")
        public boolean distantHorizonsPerformUnderglowHack = false;

        @GlobalConfig(value = "distant-horizons-sample-biomes-3d", comment = "Distant Horizons 是否采样 3D 生物群系")
        public boolean distantHorizonsSampleBiomes3d = true;
    }

    public static MiscConfig mics = new MiscConfig();

    @GlobalConfigCategory(value = "misc", comment = "杂项配置")
    public static class MiscConfig {

        public AutoUpdateConfig autoUpdate = new AutoUpdateConfig();

        @GlobalConfigCategory(value = "auto-update", comment = "自动更新配置")
        public static class AutoUpdateConfig {
            @GlobalConfig(value = "enable", lock = true, validator = AutoUpdateValidator.class, comment = "是否启用自动更新")
            public boolean enable = false;

            private static class AutoUpdateValidator extends BooleanConfigValidator {
                @Override
                public void runAfterLoader(Boolean value, boolean reload) {
                    if (!reload) {
                        LeavesUpdateHelper.init();
                        if (value) {
                            LeavesLogger.LOGGER.warning("Auto-Update is not completely safe. Enabling it may cause data security problems!");
                        }
                    }
                }
            }

            @GlobalConfig(value = "download-source", lock = true, validator = DownloadSourceValidator.class, comment = "下载源：application（官方）或 cloud（云加速）")
            public String source = "application";

            public static class DownloadSourceValidator extends StringConfigValidator {
                private static final List<String> suggestSourceList = List.of("application", "cloud");

                @Override
                public List<String> valueSuggest() {
                    return suggestSourceList;
                }
            }

            @GlobalConfig(value = "allow-experimental", comment = "是否允许自动更新到实验性版本")
            public Boolean allowExperimental = false;

            @GlobalConfig(value = "time", lock = true, comment = "自动更新的时间列表（24 小时制）")
            public List<String> updateTime = List.of("14:00", "2:00");
        }

        public ExtraYggdrasilConfig yggdrasil = new ExtraYggdrasilConfig();

        @GlobalConfigCategory(value = "extra-yggdrasil-service", comment = "额外 Yggdrasil 验证服务配置（外置登录支持）")
        public static class ExtraYggdrasilConfig {
            @GlobalConfig(value = "enable", validator = ExtraYggdrasilValidator.class, comment = "是否启用额外 Yggdrasil 服务")
            public boolean enable = false;

            public static class ExtraYggdrasilValidator extends BooleanConfigValidator {
                @Override
                public void verify(Boolean old, Boolean value) throws IllegalArgumentException {
                    if (value) {
                        LeavesLogger.LOGGER.warning("extra-yggdrasil-service is an unofficial support. Enabling it may cause data security problems!");
                        GlobalConfiguration.get().unsupportedSettings.performUsernameValidation = true; // always check username
                    }
                }
            }

            @GlobalConfig(value = "login-protect", comment = "是否启用登录保护")
            public boolean loginProtect = false;

            @SuppressWarnings("unused")
            @GlobalConfig(value = "urls", lock = true, validator = ExtraYggdrasilUrlsValidator.class, comment = "Yggdrasil 服务 URL 列表")
            private List<String> serviceList = List.of("https://url.with.authlib-injector-yggdrasil");

            public static class ExtraYggdrasilUrlsValidator extends ListConfigValidator.STRING {
                @Override
                public void verify(List<String> old, List<String> value) throws IllegalArgumentException {
                    LeavesMinecraftSessionService.initExtraYggdrasilList(value);
                }
            }
        }

        @GlobalConfig(value = "disable-method-profiler", comment = "是否禁用方法分析器（减少开销）")
        public boolean disableMethodProfiler = true;

        @TransferConfig("no-chat-sign")
        @GlobalConfig(value = "no-chat-sign", comment = "是否禁用聊天签名（1.19.1+ 的聊天签名验证）")
        public boolean noChatSign = true;

        @GlobalConfig(value = "dont-respond-ping-before-start-fully", comment = "服务器完全启动前是否不响应 Ping 请求")
        public boolean dontRespondPingBeforeStart = true;

        @GlobalConfig(value = "server-lang", lock = true, validator = ServerLangValidator.class, comment = "服务器语言设置")
        public String serverLang = "zh_cn";

        private static class ServerLangValidator extends StringConfigValidator {
            private static final List<String> supportLang = new ArrayList<>(List.of("en_us", "zh_cn"));

            @Override
            public void verify(String old, String value) throws IllegalArgumentException {
                if (!ServerI18nUtil.finishPreload ||
                    !ServerI18nUtil.tryAppendLanguages(supportLang)) {
                    return;
                }
                if (!supportLang.contains(value)) {
                    throw new IllegalArgumentException("lang " + value + " not supported");
                }
            }

            @Override
            public List<String> valueSuggest() {
                return supportLang;
            }
        }

        @GlobalConfig(value = "server-mod-name", comment = "服务器 Mod 名称（显示在客户端的服务器信息中）")
        public String serverModName = "SpringLeaves";

        @GlobalConfig(value = "bstats-privacy-mode", comment = "是否启用 bStats 隐私模式（不发送统计数据）")
        public boolean bstatsPrivacyMode = true;

        @GlobalConfig(value = "force-minecraft-command", lock = true, comment = "是否强制使用 Minecraft 原生命令（而非 Bukkit 命令）")
        public boolean forceMinecraftCommand = false;

        @GlobalConfig(value = "leaves-packet-event", comment = "是否启用 Leaves 数据包事件（允许插件拦截数据包）")
        public boolean leavesPacketEvent = false;

        @GlobalConfig(value = "packet-protector", comment = "是否启用区块包保护（在 Velocity/反作弊/协议插件链路下防止区块包损坏断开连接）")
        public boolean packetProtector = true;

        @GlobalConfig(value = "network-speed", comment = "是否启用 Network speed 网络上传下载统计，默认开启；显示需使用 /leaves networkspeed open")
        public boolean networkSpeed = true;

        @GlobalConfig(value = "chat-command-max-length", comment = "聊天命令的最大长度，原版默认 256，最大 32767")
        public int chatCommandMaxLength = 32767;
    }

    public static RegionConfig region = new RegionConfig();

    @GlobalConfigCategory(value = "region", comment = "区域文件存储格式配置")
    public static class RegionConfig {

        @GlobalConfig(value = "format", lock = true, validator = RegionFormatValidator.class, comment = "区域文件格式：ANVIL（原版）或 LINEAR（Leaves 优化格式）")
        public RegionFileFormat format = RegionFileFormat.ANVIL;

        private static class RegionFormatValidator extends EnumConfigValidator<RegionFileFormat> {
            @Override
            public void verify(RegionFileFormat old, RegionFileFormat value) throws IllegalArgumentException {
                IRegionFileFactory.initFirstRegion(value);
            }
        }

        public LinearConfig linear = new LinearConfig();

        @GlobalConfigCategory(value = "linear", comment = "Linear 区域文件格式配置")
        public static class LinearConfig {

            @GlobalConfig(value = "version", lock = true, comment = "Linear 格式版本")
            public LinearVersion version = LinearVersion.V2;

            @GlobalConfig(value = "flush-max-threads", lock = true, comment = "Linear 刷盘最大线程数，<=0 时使用 CPU 核心数+该值（最小 1）")
            public int flushThreads = 6;

            public int getLinearFlushThreads() {
                if (flushThreads <= 0) {
                    return Math.max(Runtime.getRuntime().availableProcessors() + flushThreads, 1);
                } else {
                    return flushThreads;
                }
            }

            @GlobalConfig(value = "flush-delay-ms", lock = true, comment = "Linear 刷盘延迟（毫秒）")
            public int flushDelayMs = 100;

            @GlobalConfig(value = "use-virtual-thread", lock = true, comment = "Linear 是否使用虚拟线程")
            public boolean useVirtualThread = true;

            @GlobalConfig(value = "compression-level", lock = true, validator = LinearCompressValidator.class, comment = "Linear 压缩级别（1-22，1 最快压缩率最低）")
            public int compressionLevel = 1;

            @GlobalConfig(value = "max-flush-per-run", comment = "每次 Linear 刷盘最多处理的区域文件数")
            public int maxFlushPerRun = 64;

            @GlobalConfig(value = "region-unload-check-interval-ms", comment = "Linear 区域卸载检查间隔（毫秒）")
            public int regionUnloadCheckIntervalMs = 1000;

            @GlobalConfig(value = "region-unload-idle-ms", comment = "Linear 区域文件空闲卸载时间（毫秒）")
            public long regionUnloadIdleMs = 30000L;

            private static class LinearCompressValidator extends IntConfigValidator {
                @Override
                public void verify(Integer old, Integer value) throws IllegalArgumentException {
                    if (value < 1 || value > 22) {
                        throw new IllegalArgumentException("linear.compression-level need between 1 and 22");
                    }
                }
            }
        }
    }

    public static FixConfig fix = new FixConfig();

    @GlobalConfigCategory(value = "fix", comment = "Bug 修复相关配置")
    public static class FixConfig {
        @GlobalConfig(value = "vanilla-hopper", comment = "是否使用原版漏斗行为（而非 Paper 的修改版）")
        public boolean vanillaHopper = false;

        @GlobalConfig(value = "vanilla-display-name", validator = DisplayNameValidator.class, comment = "是否使用原版显示名称处理")
        public boolean vanillaDisplayName = true;

        private static class DisplayNameValidator extends BooleanConfigValidator {
            @Override
            public void verify(Boolean old, Boolean value) throws IllegalArgumentException {
                if (value == old) {
                    return;
                }
                for (ServerPlayer player : MinecraftServer.getServer().getPlayerList().getPlayers()) {
                    player.adventure$displayName = value ? PaperAdventure.asAdventure(player.getDisplayName()) : Component.text(player.getScoreboardName());
                }
            }
        }

        @GlobalConfig(value = "vanilla-portal-handle", comment = "是否使用原版传送门处理逻辑")
        public boolean vanillaPortalHandle = true;

        @GlobalConfig(value = "collision-behavior", validator = CollisionBehaviorValidator.class, comment = "碰撞行为模式：VANILLA 原版、PAPER Paper 版")
        public CollisionBehavior collisionBehavior = CollisionBehavior.PAPER;

        public enum CollisionBehavior {
            VANILLA, PAPER
        }

        private static class CollisionBehaviorValidator extends EnumConfigValidator<CollisionBehavior> {
            @Override
            public CollisionBehavior stringConvert(@NotNull String value) throws IllegalArgumentException {
                if (value.equalsIgnoreCase("BLOCK_SHAPE_VANILLA")) {
                    LeavesLogger.LOGGER.warning("Paper has updated the collision behavior to BLOCK_SHAPE_VANILLA mode, converting this to PAPER...");
                    value = "PAPER";
                }
                return super.stringConvert(value);
            }
        }

        @GlobalConfig(value = "vanilla-end-void-rings", comment = "是否使用原版末地虚空环生成")
        public boolean vanillaEndVoidRings = false;

        @GlobalConfig(value = "stacked-container-destroyed-drop", comment = "堆叠容器被破坏时是否正常掉落物品")
        public boolean stackedContainerDestroyedDrop = true;
    }
}
