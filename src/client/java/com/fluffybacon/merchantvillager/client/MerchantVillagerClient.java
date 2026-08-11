package com.fluffybacon.merchantvillager.client;

import com.fluffybacon.merchantvillager.network.CataloguePayload;
import com.fluffybacon.merchantvillager.network.CatalogueDeltaPayload;
import com.fluffybacon.merchantvillager.registry.ModScreenHandlers;
import com.fluffybacon.merchantvillager.registry.ModBlockEntities;
import com.fluffybacon.merchantvillager.screen.client.MerchantPostScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.render.block.entity.SignBlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.block.entity.state.SignBlockEntityRenderState;
import net.minecraft.block.entity.SignBlockEntity;

public final class MerchantVillagerClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        HandledScreens.register(ModScreenHandlers.MERCHANT_POST, MerchantPostScreen::new);
        BlockEntityRendererFactory<SignBlockEntity, SignBlockEntityRenderState> markerRenderer =
            SignBlockEntityRenderer::new;
        BlockEntityRendererRegistry.register(ModBlockEntities.CHEST_ROLE_MARKER, markerRenderer);
        ClientPlayNetworking.registerGlobalReceiver(CataloguePayload.ID, (payload, context) ->
            ClientCatalogueCache.accept(payload)
        );
        ClientPlayNetworking.registerGlobalReceiver(CatalogueDeltaPayload.ID, (payload, context) ->
            ClientCatalogueCache.accept(payload)
        );
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
            ClientCatalogueCache.clear()
        );
    }
}
