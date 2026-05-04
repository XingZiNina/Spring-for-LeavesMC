package org.leavesmc.leaves.network;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;

import java.util.concurrent.atomic.AtomicLong;

public final class NetworkSpeedMonitor {

    private static final AtomicLong TOTAL_DOWNLOAD_BYTES = new AtomicLong();
    private static final AtomicLong TOTAL_UPLOAD_BYTES = new AtomicLong();
    private static final AtomicLong CURRENT_DOWNLOAD_BYTES = new AtomicLong();
    private static final AtomicLong CURRENT_UPLOAD_BYTES = new AtomicLong();
    private static final int GRADIENT_START = 0xA18CD1;
    private static final int GRADIENT_END = 0xFBC2EB;
    private static volatile boolean visible;
    private static volatile long currentDownloadBytesPerSecond;
    private static volatile long currentUploadBytesPerSecond;
    private static volatile long lastSampleTime = System.nanoTime();

    private NetworkSpeedMonitor() {
    }

    public static void recordDownload(long bytes) {
        if (bytes <= 0) {
            return;
        }
        TOTAL_DOWNLOAD_BYTES.addAndGet(bytes);
        CURRENT_DOWNLOAD_BYTES.addAndGet(bytes);
    }

    public static void recordUpload(long bytes) {
        if (bytes <= 0) {
            return;
        }
        TOTAL_UPLOAD_BYTES.addAndGet(bytes);
        CURRENT_UPLOAD_BYTES.addAndGet(bytes);
    }

    public static void tick() {
        long now = System.nanoTime();
        long elapsed = now - lastSampleTime;
        if (elapsed < 1_000_000_000L) {
            return;
        }
        lastSampleTime = now;
        currentDownloadBytesPerSecond = CURRENT_DOWNLOAD_BYTES.getAndSet(0) * 1_000_000_000L / elapsed;
        currentUploadBytesPerSecond = CURRENT_UPLOAD_BYTES.getAndSet(0) * 1_000_000_000L / elapsed;
    }

    public static long totalDownloadBytes() {
        return TOTAL_DOWNLOAD_BYTES.get();
    }

    public static long totalUploadBytes() {
        return TOTAL_UPLOAD_BYTES.get();
    }

    public static long currentDownloadBytesPerSecond() {
        return currentDownloadBytesPerSecond;
    }

    public static long currentUploadBytesPerSecond() {
        return currentUploadBytesPerSecond;
    }

    public static boolean visible() {
        return visible;
    }

    public static void setVisible(boolean visible) {
        NetworkSpeedMonitor.visible = visible;
    }

    public static String plainDisplayText() {
        return "�?" + formatBytes(currentDownloadBytesPerSecond) + "/s �?" + formatBytes(currentUploadBytesPerSecond) + "/s | Total �?" + formatBytes(totalDownloadBytes()) + " �?" + formatBytes(totalUploadBytes());
    }

    public static Component gradientDisplayText() {
        String text = plainDisplayText();
        Component component = Component.empty();
        int length = Math.max(1, text.length() - 1);
        for (int i = 0; i < text.length(); i++) {
            component = component.append(Component.text(text.charAt(i), TextColor.color(gradientColor(i, length))));
        }
        return component;
    }

    private static int gradientColor(int index, int length) {
        int startR = (GRADIENT_START >> 16) & 0xFF;
        int startG = (GRADIENT_START >> 8) & 0xFF;
        int startB = GRADIENT_START & 0xFF;
        int endR = (GRADIENT_END >> 16) & 0xFF;
        int endG = (GRADIENT_END >> 8) & 0xFF;
        int endB = GRADIENT_END & 0xFF;
        int r = startR + (endR - startR) * index / length;
        int g = startG + (endG - startG) * index / length;
        int b = startB + (endB - startB) * index / length;
        return (r << 16) | (g << 8) | b;
    }

    public static String formatBytes(long bytes) {
        if (bytes < 1024L) {
            return bytes + " B";
        }
        double value = bytes;
        String[] units = {"KB", "MB", "GB", "TB", "PB"};
        int unit = -1;
        do {
            value /= 1024.0D;
            unit++;
        } while (value >= 1024.0D && unit < units.length - 1);
        return String.format(java.util.Locale.ROOT, "%.2f %s", value, units[unit]);
    }
}
