package com.fluffybacon.merchantvillager.merchant;

import com.fluffybacon.merchantvillager.config.MerchantVillagerConfig;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.MinecraftServer;

public final class ReservationManager {
    private static final Map<MinecraftServer, Map<Key, Reservation>> RESERVATIONS = new WeakHashMap<>();

    public static synchronized boolean reserve(
        MinecraftServer server,
        UUID worker,
        UUID target,
        String offerFingerprint,
        int planned,
        long now
    ) {
        return reserveAll(
            server,
            worker,
            List.of(new Request(target, offerFingerprint, planned)),
            now
        );
    }

    public static synchronized boolean reserveAll(
        MinecraftServer server,
        UUID worker,
        List<Request> requests,
        long now
    ) {
        Map<Key, Reservation> reservations = RESERVATIONS.computeIfAbsent(server, ignored -> new HashMap<>());
        reservations.values().removeIf(reservation -> reservation.expiry() <= now);
        for (Request request : requests) {
            Reservation existing = reservations.get(new Key(request.target(), request.fingerprint()));
            if (existing != null && !existing.worker().equals(worker)) {
                return false;
            }
        }
        for (Request request : requests) {
            reservations.put(
                new Key(request.target(), request.fingerprint()),
                new Reservation(
                    worker,
                    request.planned(),
                    now + MerchantVillagerConfig.RESERVATION_TIMEOUT
                )
            );
        }
        return true;
    }

    public static synchronized void release(
        MinecraftServer server, UUID worker, UUID target, String fingerprint
    ) {
        Map<Key, Reservation> reservations = RESERVATIONS.get(server);
        if (reservations == null) {
            return;
        }
        Key key = new Key(target, fingerprint);
        Reservation reservation = reservations.get(key);
        if (reservation != null && reservation.worker().equals(worker)) {
            reservations.remove(key);
        }
    }

    public static synchronized int releaseWorker(MinecraftServer server, UUID worker) {
        Map<Key, Reservation> reservations = RESERVATIONS.get(server);
        if (reservations == null) {
            return 0;
        }
        int before = reservations.size();
        reservations.values().removeIf(reservation -> reservation.worker().equals(worker));
        return before - reservations.size();
    }

    public static synchronized int countWorker(
        MinecraftServer server, UUID worker, long now
    ) {
        Map<Key, Reservation> reservations = RESERVATIONS.get(server);
        if (reservations == null) {
            return 0;
        }
        reservations.values().removeIf(reservation -> reservation.expiry() <= now);
        return (int)reservations.values().stream()
            .filter(reservation -> reservation.worker().equals(worker))
            .count();
    }

    private record Key(UUID target, String fingerprint) {
    }

    private record Reservation(UUID worker, int planned, long expiry) {
    }

    public record Request(UUID target, String fingerprint, int planned) {
    }

    private ReservationManager() {
    }
}
