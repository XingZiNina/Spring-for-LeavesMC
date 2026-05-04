package org.leavesmc.leaves.async.chunk;

import net.minecraft.util.Util;
import org.leavesmc.leaves.LeavesConfig;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class AsyncChunkSend {

    private static final int THREAD_COUNT;

    static {
        int configured = LeavesConfig.async.asyncChunkSendThreads;
        THREAD_COUNT = configured > 0 ? configured : Math.max(1, Runtime.getRuntime().availableProcessors());
    }

    public static final ExecutorService POOL = new ThreadPoolExecutor(
        THREAD_COUNT, THREAD_COUNT, 0L, TimeUnit.MILLISECONDS,
        new LinkedBlockingQueue<>(),
        Thread.ofPlatform()
            .priority(Thread.NORM_PRIORITY - 1)
            .uncaughtExceptionHandler(Util::onThreadException)
            .name("Leaves Async Chunk Sender Thread")
            .factory(),
        new ThreadPoolExecutor.CallerRunsPolicy()
    );
}
