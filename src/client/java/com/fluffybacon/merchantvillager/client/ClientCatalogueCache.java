package com.fluffybacon.merchantvillager.client;

import com.fluffybacon.merchantvillager.network.CataloguePayload;
import com.fluffybacon.merchantvillager.network.CatalogueDeltaPayload;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.util.math.BlockPos;

public final class ClientCatalogueCache {
    private static CataloguePayload latest;
    private static BlockPos activePost;
    private static int latestRowRevision = -1;
    private static int pendingRevision = -1;
    private static int pendingChunkCount;
    private static List<List<CataloguePayload.Entry>> pendingChunks = List.of();
    private static int pendingDeltaBaseRevision = -1;
    private static int pendingDeltaRevision = -1;
    private static int pendingDeltaChunkCount;
    private static List<List<CatalogueDeltaPayload.RowDelta>> pendingDeltaChunks = List.of();

    public static void beginSession(BlockPos postPos) {
        activePost = postPos.toImmutable();
        latest = null;
        latestRowRevision = -1;
        clearPendingCatalogue();
        clearPendingDelta();
    }

    public static void accept(CataloguePayload payload) {
        if (activePost == null || !payload.postPos().equals(activePost)) {
            return;
        }
        if (payload.fullCatalogue()) {
            acceptChunk(payload);
        } else if (latest != null && payload.revision() >= latest.revision()) {
            latest = withEntries(payload, latest.entries());
        }
    }

    public static void accept(CatalogueDeltaPayload payload) {
        if (activePost == null
            || !payload.postPos().equals(activePost)
            || latest == null
            || payload.revision() <= latestRowRevision
            || payload.baseRevision() != latestRowRevision) {
            return;
        }
        if (pendingDeltaRevision != payload.revision()
            || pendingDeltaBaseRevision != payload.baseRevision()
            || pendingDeltaChunkCount != payload.chunkCount()) {
            pendingDeltaBaseRevision = payload.baseRevision();
            pendingDeltaRevision = payload.revision();
            pendingDeltaChunkCount = payload.chunkCount();
            pendingDeltaChunks = new ArrayList<>(
                Collections.nCopies(pendingDeltaChunkCount, null)
            );
        }
        pendingDeltaChunks.set(payload.chunkIndex(), payload.deltas());
        if (pendingDeltaChunks.stream().anyMatch(java.util.Objects::isNull)) {
            return;
        }

        Map<String, CataloguePayload.Entry> rows = new LinkedHashMap<>();
        for (CataloguePayload.Entry entry : latest.entries()) {
            rows.put(entry.offer().fingerprint(), entry);
        }
        for (CatalogueDeltaPayload.RowDelta delta : pendingDeltaChunks.stream()
            .flatMap(List::stream)
            .toList()) {
            CataloguePayload.Entry previous = rows.get(delta.tradeKey());
            if (previous == null) {
                clearPendingDelta();
                return;
            }
            rows.put(delta.tradeKey(), delta.apply(previous));
        }
        int appliedRevision = payload.revision();
        latest = withRevisionAndEntries(
            latest,
            Math.max(latest.revision(), appliedRevision),
            List.copyOf(rows.values())
        );
        latestRowRevision = appliedRevision;
        clearPendingDelta();
    }

    private static void acceptChunk(CataloguePayload payload) {
        if (payload.revision() < latestRowRevision
            || (pendingRevision >= 0 && payload.revision() < pendingRevision)) {
            return;
        }
        if (pendingRevision != payload.revision()
            || pendingChunkCount != payload.chunkCount()) {
            pendingRevision = payload.revision();
            pendingChunkCount = payload.chunkCount();
            pendingChunks = new ArrayList<>(Collections.nCopies(pendingChunkCount, null));
        }
        pendingChunks.set(payload.chunkIndex(), payload.entries());
        if (pendingChunks.stream().anyMatch(java.util.Objects::isNull)) {
            return;
        }
        List<CataloguePayload.Entry> combined = pendingChunks.stream()
            .flatMap(List::stream)
            .toList();
        CataloguePayload metadata = latest != null && latest.revision() > payload.revision()
            ? latest
            : payload;
        latest = withEntries(metadata, combined);
        latestRowRevision = payload.revision();
        clearPendingCatalogue();
        clearPendingDelta();
    }

    private static CataloguePayload withEntries(
        CataloguePayload payload, List<CataloguePayload.Entry> entries
    ) {
        return withRevisionAndEntries(payload, payload.revision(), entries);
    }

    private static CataloguePayload withRevisionAndEntries(
        CataloguePayload payload,
        int revision,
        List<CataloguePayload.Entry> entries
    ) {
        return new CataloguePayload(
            payload.postPos(),
            revision,
            payload.workerUuid(),
            payload.workerState(),
            payload.status(),
            payload.lastFailure(),
            payload.targetCount(),
            payload.enabledCount(),
            payload.executableCount(),
            payload.workerStats(),
            true,
            0,
            1,
            List.copyOf(entries)
        );
    }

    public static CataloguePayload latest() {
        return latest;
    }

    public static void clear() {
        latest = null;
        activePost = null;
        latestRowRevision = -1;
        clearPendingCatalogue();
        clearPendingDelta();
    }

    private static void clearPendingCatalogue() {
        pendingRevision = -1;
        pendingChunkCount = 0;
        pendingChunks = List.of();
    }

    private static void clearPendingDelta() {
        pendingDeltaBaseRevision = -1;
        pendingDeltaRevision = -1;
        pendingDeltaChunkCount = 0;
        pendingDeltaChunks = List.of();
    }

    public static void endSession(BlockPos postPos) {
        if (activePost != null && activePost.equals(postPos)) {
            clear();
        }
    }

    private ClientCatalogueCache() {
    }
}
