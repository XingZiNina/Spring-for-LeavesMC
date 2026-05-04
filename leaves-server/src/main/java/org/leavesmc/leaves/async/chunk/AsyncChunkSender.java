package org.leavesmc.leaves.async.chunk;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.leavesmc.leaves.util.queue.MpmcQueue;

import java.util.function.Supplier;

@NullMarked
public final class AsyncChunkSender {

    private static final int CAPACITY = 255;

    private final MpmcQueue<ClientboundLevelChunkWithLightPacket> channel;
    private final LongOpenHashSet pending;
    private volatile int size = 0;

    public AsyncChunkSender() {
        this.channel = new MpmcQueue<>(ClientboundLevelChunkWithLightPacket.class, CAPACITY);
        this.pending = new LongOpenHashSet();
    }

    public boolean add(long k) {
        if (size >= CAPACITY || pending.size() >= CAPACITY) {
            return false;
        }
        return pending.add(k);
    }

    public boolean remove(long k) {
        return pending.remove(k);
    }

    public boolean contains(long k) {
        return pending.contains(k);
    }

    public void clear() {
        pending.clear();
        while (recv() != null) ;
    }

    public void submit(Supplier<ClientboundLevelChunkWithLightPacket> task) {
        synchronized (this) {
            if (size >= CAPACITY) return;
            size++;
        }
        AsyncChunkSend.POOL.submit(() -> {
            ClientboundLevelChunkWithLightPacket chunk = task.get();
            if (chunk != null) {
                while (!channel.send(chunk)) ;
            } else {
                synchronized (AsyncChunkSender.this) {
                    size--;
                }
            }
        });
    }

    public boolean send(ClientboundLevelChunkWithLightPacket packet) {
        synchronized (this) {
            if (size >= CAPACITY) return false;
            size++;
        }
        return this.channel.send(packet);
    }

    public @Nullable ClientboundLevelChunkWithLightPacket recv() {
        ClientboundLevelChunkWithLightPacket pkt = this.channel.recv();
        if (pkt != null) {
            synchronized (this) {
                size--;
            }
        }
        return pkt;
    }

    public int size() {
        return this.size;
    }
}
