package com.fluffybacon.merchantvillager.client;

import com.fluffybacon.merchantvillager.network.CataloguePayload;
import com.fluffybacon.merchantvillager.registry.ModScreenHandlers;
import com.fluffybacon.merchantvillager.screen.client.MerchantPostScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screen.ingame.HandledScreens;

public final class MerchantVillagerClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        HandledScreens.register(ModScreenHandlers.MERCHANT_POST, MerchantPostScreen::new);
        ClientPlayNetworking.registerGlobalReceiver(CataloguePayload.ID, (payload, context) ->
            ClientCatalogueCache.accept(payload)
        );
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
            ClientCatalogueCache.clear()
        );
    }
}
