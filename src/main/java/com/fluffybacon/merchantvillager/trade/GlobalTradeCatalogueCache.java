package com.fluffybacon.merchantvillager.trade;

import com.fluffybacon.merchantvillager.MerchantVillagerMod;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;

/** One immutable runtime-table snapshot per integrated server. */
public final class GlobalTradeCatalogueCache {
    private static final Map<MinecraftServer, GlobalTradeCatalogue> CATALOGUES =
        new WeakHashMap<>();

    public static synchronized GlobalTradeCatalogue get(ServerWorld world) {
        return CATALOGUES.computeIfAbsent(
            world.getServer(),
            ignored -> GlobalTradeCatalogue.fromRuntimeTables(world)
        );
    }

    public static synchronized void invalidate(MinecraftServer server) {
        CATALOGUES.remove(server);
    }

    public static void initialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> rebuild(server));
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, manager, success) -> {
            if (success) {
                rebuild(server);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(GlobalTradeCatalogueCache::invalidate);
    }

    private static void rebuild(MinecraftServer server) {
        invalidate(server);
        try {
            GlobalTradeCatalogue catalogue = get(server.getOverworld());
            MerchantVillagerMod.LOGGER.info(
                "Indexed {} global villager trade rows from {} runtime sources",
                catalogue.entries().size(),
                catalogue.sourceCount()
            );
            if (!catalogue.samplingIssues().isEmpty()) {
                MerchantVillagerMod.LOGGER.warn(
                    "Skipped {} unsafe or failed global trade sources/samples that could not be previewed",
                    catalogue.samplingIssues().size()
                );
            }
        } catch (RuntimeException failure) {
            MerchantVillagerMod.LOGGER.error(
                "Could not build the global villager trade catalogue; it will retry on demand",
                failure
            );
        }
    }

    private GlobalTradeCatalogueCache() {
    }
}
