package com.fluffybacon.merchantvillager.client;

import com.fluffybacon.merchantvillager.network.CataloguePayload;

public final class ClientCatalogueCache {
    private static CataloguePayload latest;

    public static void accept(CataloguePayload payload) {
        if (latest == null || payload.revision() >= latest.revision()
            || !payload.postPos().equals(latest.postPos())) {
            latest = payload;
        }
    }

    public static CataloguePayload latest() {
        return latest;
    }

    public static void clear() {
        latest = null;
    }

    private ClientCatalogueCache() {
    }
}
