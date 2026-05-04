package org.leavesmc.leaves.async;

import org.jspecify.annotations.NullMarked;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

@NullMarked
public final class AsyncExecutor {

    private final String name;
    private final LinkedBlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>();
    private volatile Thread thread;

    public AsyncExecutor(String name) {
        this.name = name;
    }

    public void start() {
        if (this.thread != null) {
            return;
        }
        this.thread = Thread.ofPlatform()
            .name("Leaves " + this.name)
            .daemon(true)
            .priority(Thread.NORM_PRIORITY - 1)
            .unstarted(() -> {
                while (true) {
                    try {
                        Runnable task = this.workQueue.take();
                        task.run();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        net.minecraft.util.Util.onThreadException(Thread.currentThread(), e);
                    }
                }
            });
        this.thread.start();
    }

    public void submit(Runnable task) {
        if (this.thread == null) {
            task.run();
            return;
        }
        this.workQueue.add(task);
    }

    public void shutdown() {
        if (this.thread != null) {
            this.thread.interrupt();
        }
    }
}
