package org.leavesmc.leaves.protocol.distanthorizons;

import no.jckf.dhsupport.core.DhSupport;
import no.jckf.dhsupport.core.configuration.DhsConfig;
import no.jckf.dhsupport.core.scheduling.Scheduler;
import org.bukkit.Bukkit;
import org.leavesmc.leaves.plugin.MinecraftInternalPlugin;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class LeavesDistantHorizonsScheduler implements Scheduler {

    private final ExecutorService executor;
    private final ScheduledExecutorService scheduledExecutor;

    public LeavesDistantHorizonsScheduler(DhSupport dhSupport) {
        int threadCount = Math.max(1, dhSupport.getConfig().getInt(DhsConfig.SCHEDULER_THREADS, 2));
        this.executor = Executors.newFixedThreadPool(threadCount, runnable -> {
            Thread thread = new Thread(runnable, "Distant-Horizons-Worker");
            thread.setDaemon(true);
            return thread;
        });
        this.scheduledExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "Distant-Horizons-Scheduler");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    public boolean canReadWorldAsync() {
        return false;
    }

    @Override
    public <U> CompletableFuture<U> runOnMainThread(Supplier<U> supplier) {
        CompletableFuture<U> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTask(MinecraftInternalPlugin.INSTANCE, () -> complete(future, supplier));
        return future;
    }

    @Override
    public <U> CompletableFuture<U> runOnMainThreadDelayed(Supplier<U> supplier, long delayTicks) {
        CompletableFuture<U> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTaskLater(MinecraftInternalPlugin.INSTANCE, () -> complete(future, supplier), delayTicks);
        return future;
    }

    @Override
    public <U> CompletableFuture<U> runOnMainThreadDelayed(Supplier<U> supplier, long delayAmount, TimeUnit delayUnit) {
        CompletableFuture<U> future = new CompletableFuture<>();
        this.scheduledExecutor.schedule(() -> runOnMainThread(supplier).whenComplete((value, throwable) -> completeFrom(future, value, throwable)), delayAmount, delayUnit);
        return future;
    }

    @Override
    public <U> CompletableFuture<U> runOnRegionThread(UUID worldId, int x, int z, Supplier<U> supplier) {
        return runOnMainThread(supplier);
    }

    @Override
    public <U> CompletableFuture<U> runOnRegionThreadDelayed(UUID worldId, int x, int z, Supplier<U> supplier, long delayTicks) {
        return runOnMainThreadDelayed(supplier, delayTicks);
    }

    @Override
    public <U> CompletableFuture<U> runOnRegionThreadDelayed(UUID worldId, int x, int z, Supplier<U> supplier, long delayAmount, TimeUnit delayUnit) {
        return runOnMainThreadDelayed(supplier, delayAmount, delayUnit);
    }

    @Override
    public <U> CompletableFuture<U> runOnSeparateThread(Supplier<U> supplier) {
        return CompletableFuture.supplyAsync(supplier, this.executor);
    }

    @Override
    public <U> CompletableFuture<U> runOnSeparateThreadDelayed(Supplier<U> supplier, long delayTicks) {
        CompletableFuture<U> future = new CompletableFuture<>();
        this.scheduledExecutor.schedule(() -> runOnSeparateThread(supplier).whenComplete((value, throwable) -> completeFrom(future, value, throwable)), delayTicks * 50L, TimeUnit.MILLISECONDS);
        return future;
    }

    @Override
    public <U> CompletableFuture<U> runOnSeparateThreadDelayed(Supplier<U> supplier, long delayAmount, TimeUnit delayUnit) {
        CompletableFuture<U> future = new CompletableFuture<>();
        this.scheduledExecutor.schedule(() -> runOnSeparateThread(supplier).whenComplete((value, throwable) -> completeFrom(future, value, throwable)), delayAmount, delayUnit);
        return future;
    }

    @Override
    public void cancelTasks() {
        this.scheduledExecutor.shutdownNow();
        this.executor.shutdownNow();
    }

    @Override
    public Executor getExecutor() {
        return this.executor;
    }

    private static <U> void complete(CompletableFuture<U> future, Supplier<U> supplier) {
        try {
            future.complete(supplier.get());
        } catch (Throwable throwable) {
            future.completeExceptionally(throwable);
        }
    }

    private static <U> void completeFrom(CompletableFuture<U> future, U value, Throwable throwable) {
        if (throwable == null) {
            future.complete(value);
        } else {
            future.completeExceptionally(throwable);
        }
    }
}
