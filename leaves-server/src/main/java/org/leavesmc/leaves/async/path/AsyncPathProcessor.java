package org.leavesmc.leaves.async.path;

import net.minecraft.world.level.pathfinder.Path;
import org.leavesmc.leaves.LeavesConfig;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

@NullMarked
public final class AsyncPathProcessor {

    private static final int MAX_THREADS;
    static {
        int configured = LeavesConfig.async.asyncPathfindingThreads;
        MAX_THREADS = configured > 0 ? configured : Math.max(1, Runtime.getRuntime().availableProcessors() / 4);
    }
    private static final AtomicInteger THREAD_COUNT = new AtomicInteger(0);

    public static final ExecutorService PATH_EXECUTOR = new ThreadPoolExecutor(
        1, MAX_THREADS, 60L, TimeUnit.SECONDS,
        new LinkedBlockingQueue<>(),
        (ThreadFactory) r -> {
            Thread t = new Thread(r, "Leaves Async Pathfinding #" + THREAD_COUNT.incrementAndGet());
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        },
        (r, executor) -> {
            if (!executor.isShutdown()) {
                r.run();
            }
        }
    );

    public static CompletableFuture<@Nullable Path> queue(Supplier<@Nullable Path> pathSupplier) {
        CompletableFuture<@Nullable Path> future = new CompletableFuture<>();
        PATH_EXECUTOR.submit(() -> {
            try {
                Path path = pathSupplier.get();
                future.complete(path);
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        return future;
    }
}
