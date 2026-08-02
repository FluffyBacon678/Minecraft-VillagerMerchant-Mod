package com.fluffybacon.merchantvillager.client;

import com.fluffybacon.merchantvillager.network.CataloguePayload;
import net.minecraft.util.math.BlockPos;

public final class ClientCatalogueCache {
    private static CataloguePayload latest;
    private static BlockPos activePost;

    public static void beginSession(BlockPos postPos) {
        activePost = postPos.toImmutable();
        latest = null;
    }

    public static void accept(CataloguePayload payload) {
        if (activePost != null
            && payload.postPos().equals(activePost)
            && (latest == null || payload.revision() >= latest.revision())) {
            latest = payload;
        }
    }

    public static CataloguePayload latest() {
        return latest;
    }

    public static void clear() {
        latest = null;
        activePost = null;
    }

    public static void endSession(BlockPos postPos) {
        if (activePost != null && activePost.equals(postPos)) {
            clear();
        }
    }

    private ClientCatalogueCache() {
    }
}
