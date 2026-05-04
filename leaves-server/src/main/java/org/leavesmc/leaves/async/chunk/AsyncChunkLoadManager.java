package org.leavesmc.leaves.async.chunk;

import ca.spottedleaf.concurrentutil.util.Priority;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.TimeUtil;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.leavesmc.leaves.LeavesConfig;
import org.leavesmc.leaves.LeavesLogger;
import org.leavesmc.leaves.protocol.core.ProtocolHandler;
import org.leavesmc.leaves.util.queue.MpmcQueue;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadMXBean;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

@NullMarked
public final class AsyncChunkLoadManager {

    private static final int COMPLETION_QUEUE_CAPACITY = 8192;
    private static final int REQUEST_POOL_CAPACITY = 4096;
    private static final int DEFAULT_TICK_INTERVAL = 1;
    private static final AtomicLong REQUEST_IDS = new AtomicLong();
    private static final LongAdder REQUESTED = new LongAdder();
    private static final LongAdder DISPATCHED = new LongAdder();
    private static final LongAdder COMPLETED = new LongAdder();
    private static final LongAdder FAILED = new LongAdder();
    private static final LongAdder FALLBACKS = new LongAdder();
    private static final LongAdder REJECTED = new LongAdder();
    private static final LongAdder COMPLETION_DRAINED = new LongAdder();
    private static final LongAdder QUEUE_FULL_DROPS = new LongAdder();
    private static final LongAdder CALLBACK_ERRORS = new LongAdder();
    private static final LongAdder TOTAL_CHUNKS_LOADED = new LongAdder();
    private static final LongAdder TOTAL_NANOS = new LongAdder();
    private static final LongAdder TOTAL_BYTES_DELTA = new LongAdder();
    private static final AtomicInteger IN_FLIGHT = new AtomicInteger();
    private static final AtomicInteger ACTIVE_THREADS = new AtomicInteger();
    private static final MpmcQueue<CompletedRequest> COMPLETIONS = new MpmcQueue<>(CompletedRequest.class, COMPLETION_QUEUE_CAPACITY);
    private static final MpmcQueue<RequestRecord> REQUEST_POOL = new MpmcQueue<>(RequestRecord.class, REQUEST_POOL_CAPACITY);
    private static final Map<UUID, PlayerState> PLAYERS = new ConcurrentHashMap<>();
    private static final ThreadMXBean THREAD_MX_BEAN = ManagementFactory.getThreadMXBean();
    private static final MemoryMXBean MEMORY_MX_BEAN = ManagementFactory.getMemoryMXBean();
    private static final GarbageCollectorMXBean[] GC_BEANS = ManagementFactory.getGarbageCollectorMXBeans().toArray(GarbageCollectorMXBean[]::new);
    private static final long[] LAST_GC_COUNTS = new long[GC_BEANS.length];
    private static final long[] LAST_GC_TIMES = new long[GC_BEANS.length];
    private static volatile Snapshot lastSnapshot = Snapshot.empty();
    private static volatile long degradedUntilTick = Long.MIN_VALUE;

    static {
        Arrays.fill(LAST_GC_COUNTS, -1L);
        Arrays.fill(LAST_GC_TIMES, -1L);
    }

    private AsyncChunkLoadManager() {
    }

    public static void updatePlayer(ServerPlayer player) {
        if (!LeavesConfig.async.asyncChunkLoading) {
            return;
        }
        PlayerState state = PLAYERS.computeIfAbsent(player.getUUID(), ignored -> new PlayerState());
        int chunkX = player.chunkPosition().x;
        int chunkZ = player.chunkPosition().z;
        if (state.levelId != null && !state.levelId.equals(player.level().dimension())) {
            state.reset();
        }
        int viewDistance = resolveClientViewDistance(player);
        if (state.lastChunkX == chunkX && state.lastChunkZ == chunkZ && state.lastViewDistance == viewDistance) {
            return;
        }
        state.levelId = player.level().dimension();
        state.lastChunkX = chunkX;
        state.lastChunkZ = chunkZ;
        state.lastViewDistance = viewDistance;
        if (player.connection == null || !player.connection.isAcceptingMessages()) {
            return;
        }
        queuePlayerPreload(player, state, chunkX, chunkZ);
    }

    public static void removePlayer(ServerPlayer player) {
        PlayerState state = PLAYERS.remove(player.getUUID());
        if (state != null) {
            state.reset();
        }
    }

    @ProtocolHandler.PlayerJoin
    public static void onPlayerJoin(ServerPlayer player) {
        removePlayer(player);
        updatePlayer(player);
    }

    @ProtocolHandler.PlayerLeave
    public static void onPlayerLeave(ServerPlayer player) {
        removePlayer(player);
    }

    @ProtocolHandler.ReloadDataPack
    public static void onDataPackReload() {
        PLAYERS.values().forEach(PlayerState::reset);
    }

    @ProtocolHandler.Ticker
    public static void tick() {
        if (!LeavesConfig.async.asyncChunkLoading) {
            return;
        }
        MinecraftServer server = MinecraftServer.getServer();
        int budget = computeDrainBudget(server);
        for (int i = 0; i < budget; ++i) {
            CompletedRequest completed = COMPLETIONS.recv();
            if (completed == null) {
                break;
            }
            handleCompletion(server, completed);
            COMPLETION_DRAINED.increment();
        }
        long tick = server.getTickCount();
        if (tick % Math.max(DEFAULT_TICK_INTERVAL, LeavesConfig.async.asyncChunkLoadingMonitorInterval) == 0) {
            captureSnapshot(server);
        }
    }

    @Override
    public String toString() {
        return super.toString();
    }

    public static Snapshot getLastSnapshot() {
        return lastSnapshot;
    }

    public static boolean isDegraded(MinecraftServer server) {
        return server.getTickCount() < degradedUntilTick;
    }

    public static void queueArea(ServerLevel level, BlockPos center, int radiusBlocks, @Nullable ServerPlayer player, Priority priority) {
        if (!LeavesConfig.async.asyncChunkLoading) {
            return;
        }
        MinecraftServer server = level.getServer();
        if (server == null || isDegraded(server)) {
            FALLBACKS.increment();
            return;
        }
        Snapshot snapshot = lastSnapshot;
        if (!snapshot.allowAsyncDispatch()) {
            REJECTED.increment();
            return;
        }
        int radiusChunks = Math.max(0, radiusBlocks >> 4);
        int minChunkX = (center.getX() >> 4) - radiusChunks;
        int maxChunkX = (center.getX() >> 4) + radiusChunks;
        int minChunkZ = (center.getZ() >> 4) - radiusChunks;
        int maxChunkZ = (center.getZ() >> 4) + radiusChunks;
        int totalChunks = Math.max(1, (maxChunkX - minChunkX + 1) * (maxChunkZ - minChunkZ + 1));
        if (IN_FLIGHT.get() + totalChunks > LeavesConfig.async.asyncChunkLoadingMaxInflight) {
            REJECTED.increment();
            return;
        }
        RequestRecord request = borrowRequest();
        request.id = REQUEST_IDS.incrementAndGet();
        request.level = level;
        request.priority = priority;
        request.owner = player;
        request.minChunkX = minChunkX;
        request.maxChunkX = maxChunkX;
        request.minChunkZ = minChunkZ;
        request.maxChunkZ = maxChunkZ;
        request.totalChunks = totalChunks;
        request.startTick = server.getTickCount();
        request.startNanos = System.nanoTime();
        request.heapBefore = currentHeapBytes();
        request.chunkStatus = "FULL";
        request.centerChunkX = center.getX() >> 4;
        request.centerChunkZ = center.getZ() >> 4;
        REQUESTED.increment();
        IN_FLIGHT.addAndGet(totalChunks);
        DISPATCHED.increment();
        ACTIVE_THREADS.incrementAndGet();
        try {
            level.loadChunks(minChunkX, minChunkZ, maxChunkX, maxChunkZ, priority, chunks -> complete(request, chunks == null ? 0 : chunks.size(), null));
        } catch (Throwable throwable) {
            complete(request, 0, throwable);
        }
    }

    private static void queuePlayerPreload(ServerPlayer player, PlayerState state, int chunkX, int chunkZ) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        int viewDistance = Math.max(2, Math.min(resolveClientViewDistance(player), LeavesConfig.async.asyncChunkLoadingMaxViewDistance));
        int preloadDistance = Math.max(1, Math.min(viewDistance + LeavesConfig.async.asyncChunkLoadingExtraRadius, LeavesConfig.async.asyncChunkLoadingMaxViewDistance));
        LongOpenHashSet submitted = state.submitted;
        ArrayDeque<ChunkCandidate> candidates = new ArrayDeque<>();
        for (int dx = -preloadDistance; dx <= preloadDistance; ++dx) {
            for (int dz = -preloadDistance; dz <= preloadDistance; ++dz) {
                int targetX = chunkX + dx;
                int targetZ = chunkZ + dz;
                long key = ChunkPos.asLong(targetX, targetZ);
                if (submitted.contains(key)) {
                    continue;
                }
                int distance = Math.max(Math.abs(dx), Math.abs(dz));
                candidates.add(new ChunkCandidate(targetX, targetZ, distance));
            }
        }
        candidates.stream()
            .sorted(Comparator.comparingInt(ChunkCandidate::distance))
            .limit(Math.max(1, LeavesConfig.async.asyncChunkLoadingMaxRequestsPerTick))
            .forEach(candidate -> {
                submitted.add(ChunkPos.asLong(candidate.chunkX(), candidate.chunkZ()));
                queueArea(level, new BlockPos(candidate.chunkX() << 4, player.blockPosition().getY(), candidate.chunkZ() << 4), 0, player, priorityFor(candidate.distance()));
            });
        if (submitted.size() > LeavesConfig.async.asyncChunkLoadingTrackedPerPlayer) {
            trimSubmitted(submitted, chunkX, chunkZ);
        }
    }

    private static void trimSubmitted(LongOpenHashSet submitted, int chunkX, int chunkZ) {
        LongOpenHashSet retained = new LongOpenHashSet();
        int maxDistance = LeavesConfig.async.asyncChunkLoadingMaxViewDistance + LeavesConfig.async.asyncChunkLoadingExtraRadius + 2;
        for (long key : submitted) {
            int x = ChunkPos.getX(key);
            int z = ChunkPos.getZ(key);
            if (Math.max(Math.abs(x - chunkX), Math.abs(z - chunkZ)) <= maxDistance) {
                retained.add(key);
            }
        }
        submitted.clear();
        submitted.addAll(retained);
    }

    private static Priority priorityFor(int distance) {
        if (distance <= 1) {
            return Priority.BLOCKING;
        }
        if (distance <= 3) {
            return Priority.HIGH;
        }
        if (distance <= 6) {
            return Priority.NORMAL;
        }
        return Priority.LOW;
    }

    private static void complete(RequestRecord request, int loadedChunks, @Nullable Throwable throwable) {
        CompletedRequest completed = new CompletedRequest();
        completed.request = request;
        completed.loadedChunks = loadedChunks;
        completed.throwable = throwable;
        completed.finishNanos = System.nanoTime();
        completed.heapAfter = currentHeapBytes();
        ACTIVE_THREADS.decrementAndGet();
        if (!COMPLETIONS.send(completed)) {
            QUEUE_FULL_DROPS.increment();
            handleCompletion(request.level.getServer(), completed);
        }
    }

    private static void handleCompletion(MinecraftServer server, CompletedRequest completed) {
        RequestRecord request = completed.request;
        long nanos = Math.max(0L, completed.finishNanos - request.startNanos);
        long heapDelta = Math.max(0L, completed.heapAfter - request.heapBefore);
        IN_FLIGHT.addAndGet(-request.totalChunks);
        TOTAL_NANOS.add(nanos);
        TOTAL_BYTES_DELTA.add(heapDelta);
        TOTAL_CHUNKS_LOADED.add(completed.loadedChunks);
        if (completed.throwable != null) {
            FAILED.increment();
            FALLBACKS.increment();
            degradedUntilTick = Math.max(degradedUntilTick, server.getTickCount() + LeavesConfig.async.asyncChunkLoadingDegradeTicks);
            LeavesLogger.LOGGER.warning("Async chunk loading failed for " + request.describe() + ", fallback to native path", completed.throwable instanceof Exception exception ? exception : new RuntimeException(completed.throwable));
        } else {
            COMPLETED.increment();
        }
        recycle(request);
    }

    private static int computeDrainBudget(MinecraftServer server) {
        Snapshot snapshot = lastSnapshot;
        int configured = LeavesConfig.async.asyncChunkLoadingMainThreadBudget;
        if (isDegraded(server)) {
            return Math.max(1, configured / 2);
        }
        if (snapshot.averageMspt >= LeavesConfig.async.asyncChunkLoadingHighMsptThreshold) {
            return Math.max(1, configured / 2);
        }
        if (snapshot.averageMspt <= LeavesConfig.async.asyncChunkLoadingLowMsptThreshold && snapshot.inFlight < LeavesConfig.async.asyncChunkLoadingMaxInflight / 2) {
            return configured + Math.max(1, configured / 2);
        }
        return configured;
    }

    private static void captureSnapshot(MinecraftServer server) {
        long[] tickTimes = server.getTickTimesNanos();
        double averageMspt = Arrays.stream(tickTimes).average().orElse(0.0D) / TimeUtil.NANOSECONDS_PER_MILLISECOND;
        double tps = Math.min(20.0D, 1000.0D / Math.max(50.0D, averageMspt));
        MemoryUsage heap = MEMORY_MX_BEAN.getHeapMemoryUsage();
        long usedHeap = heap.getUsed();
        long committedHeap = heap.getCommitted();
        int liveThreads = THREAD_MX_BEAN.getThreadCount();
        long gcCollections = 0L;
        long gcCollectionTime = 0L;
        for (int i = 0; i < GC_BEANS.length; ++i) {
            long count = GC_BEANS[i].getCollectionCount();
            long time = GC_BEANS[i].getCollectionTime();
            if (count >= 0L) {
                gcCollections += LAST_GC_COUNTS[i] < 0L ? 0L : Math.max(0L, count - LAST_GC_COUNTS[i]);
                LAST_GC_COUNTS[i] = count;
            }
            if (time >= 0L) {
                gcCollectionTime += LAST_GC_TIMES[i] < 0L ? 0L : Math.max(0L, time - LAST_GC_TIMES[i]);
                LAST_GC_TIMES[i] = time;
            }
        }
        lastSnapshot = new Snapshot(
            averageMspt,
            tps,
            usedHeap,
            committedHeap,
            liveThreads,
            ACTIVE_THREADS.get(),
            IN_FLIGHT.get(),
            Math.max(0, REQUESTED.intValue() - COMPLETION_DRAINED.intValue() - FAILED.intValue()),
            gcCollections,
            gcCollectionTime,
            REQUESTED.sum(),
            DISPATCHED.sum(),
            COMPLETED.sum(),
            FAILED.sum(),
            FALLBACKS.sum(),
            REJECTED.sum(),
            TOTAL_CHUNKS_LOADED.sum(),
            TOTAL_NANOS.sum(),
            TOTAL_BYTES_DELTA.sum(),
            server.getTickCount() < degradedUntilTick
        );
    }

    private static RequestRecord borrowRequest() {
        RequestRecord request = REQUEST_POOL.recv();
        return request == null ? new RequestRecord() : request;
    }

    private static void recycle(RequestRecord request) {
        request.reset();
        REQUEST_POOL.send(request);
    }

    private static long currentHeapBytes() {
        return MEMORY_MX_BEAN.getHeapMemoryUsage().getUsed();
    }

    private static int resolveClientViewDistance(ServerPlayer player) {
        Integer requested = player.requestedViewDistance();
        if (requested == null) {
            return LeavesConfig.async.asyncChunkLoadingMaxViewDistance;
        }
        return Math.max(2, requested.intValue());
    }

    public static final class Snapshot {
        public final double averageMspt;
        public final double tps;
        public final long usedHeap;
        public final long committedHeap;
        public final int liveThreads;
        public final int activeAsyncThreads;
        public final int inFlight;
        public final int completionQueueSize;
        public final long gcCollections;
        public final long gcCollectionTime;
        public final long requested;
        public final long dispatched;
        public final long completed;
        public final long failed;
        public final long fallbacks;
        public final long rejected;
        public final long totalChunksLoaded;
        public final long totalNanos;
        public final long totalBytesDelta;
        public final boolean degraded;

        private Snapshot(
            double averageMspt,
            double tps,
            long usedHeap,
            long committedHeap,
            int liveThreads,
            int activeAsyncThreads,
            int inFlight,
            int completionQueueSize,
            long gcCollections,
            long gcCollectionTime,
            long requested,
            long dispatched,
            long completed,
            long failed,
            long fallbacks,
            long rejected,
            long totalChunksLoaded,
            long totalNanos,
            long totalBytesDelta,
            boolean degraded
        ) {
            this.averageMspt = averageMspt;
            this.tps = tps;
            this.usedHeap = usedHeap;
            this.committedHeap = committedHeap;
            this.liveThreads = liveThreads;
            this.activeAsyncThreads = activeAsyncThreads;
            this.inFlight = inFlight;
            this.completionQueueSize = completionQueueSize;
            this.gcCollections = gcCollections;
            this.gcCollectionTime = gcCollectionTime;
            this.requested = requested;
            this.dispatched = dispatched;
            this.completed = completed;
            this.failed = failed;
            this.fallbacks = fallbacks;
            this.rejected = rejected;
            this.totalChunksLoaded = totalChunksLoaded;
            this.totalNanos = totalNanos;
            this.totalBytesDelta = totalBytesDelta;
            this.degraded = degraded;
        }

        public static Snapshot empty() {
            return new Snapshot(0.0D, 20.0D, 0L, 0L, 0, 0, 0, 0, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, false);
        }

        public boolean allowAsyncDispatch() {
            if (this.degraded) {
                return false;
            }
            if (this.inFlight >= LeavesConfig.async.asyncChunkLoadingMaxInflight) {
                return false;
            }
            return this.averageMspt <= LeavesConfig.async.asyncChunkLoadingRejectMsptThreshold;
        }

        public double averageChunkLoadMs() {
            return this.completed == 0L ? 0.0D : (double) this.totalNanos / (double) this.completed / 1.0E6D;
        }

        public long averageHeapDeltaBytes() {
            return this.completed == 0L ? 0L : this.totalBytesDelta / Math.max(1L, this.completed);
        }
    }

    private static final class PlayerState {
        private final LongOpenHashSet submitted = new LongOpenHashSet();
        private @Nullable ResourceKey<Level> levelId;
        private int lastChunkX = Integer.MIN_VALUE;
        private int lastChunkZ = Integer.MIN_VALUE;
        private int lastViewDistance = Integer.MIN_VALUE;

        private void reset() {
            this.submitted.clear();
            this.levelId = null;
            this.lastChunkX = Integer.MIN_VALUE;
            this.lastChunkZ = Integer.MIN_VALUE;
            this.lastViewDistance = Integer.MIN_VALUE;
        }
    }

    private static final class RequestRecord {
        private long id;
        private ServerLevel level = null;
        private @Nullable ServerPlayer owner;
        private Priority priority = Priority.NORMAL;
        private int minChunkX;
        private int maxChunkX;
        private int minChunkZ;
        private int maxChunkZ;
        private int totalChunks;
        private int centerChunkX;
        private int centerChunkZ;
        private long startTick;
        private long startNanos;
        private long heapBefore;
        private String chunkStatus = "FULL";

        private String describe() {
            return this.level.dimension().identifier() + " @ [" + this.minChunkX + ',' + this.minChunkZ + "] -> [" + this.maxChunkX + ',' + this.maxChunkZ + ']';
        }

        private void reset() {
            this.id = 0L;
            this.level = null;
            this.owner = null;
            this.priority = Priority.NORMAL;
            this.minChunkX = 0;
            this.maxChunkX = 0;
            this.minChunkZ = 0;
            this.maxChunkZ = 0;
            this.totalChunks = 0;
            this.centerChunkX = 0;
            this.centerChunkZ = 0;
            this.startTick = 0L;
            this.startNanos = 0L;
            this.heapBefore = 0L;
            this.chunkStatus = "FULL";
        }
    }

    private static final class CompletedRequest {
        private RequestRecord request = null;
        private int loadedChunks;
        private @Nullable Throwable throwable;
        private long finishNanos;
        private long heapAfter;
    }

    private record ChunkCandidate(int chunkX, int chunkZ, int distance) {
    }
}
