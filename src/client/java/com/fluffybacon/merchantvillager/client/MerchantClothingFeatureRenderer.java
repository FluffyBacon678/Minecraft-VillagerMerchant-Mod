package com.fluffybacon.merchantvillager.client;

import com.fluffybacon.merchantvillager.MerchantVillagerMod;
import com.fluffybacon.merchantvillager.registry.ModVillagerProfessions;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.VillagerResemblingModel;
import net.minecraft.client.render.entity.state.VillagerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Identifier;

/** Renders only the dyeable cloth UV mask over the permanent profession art. */
public final class MerchantClothingFeatureRenderer extends FeatureRenderer<
    VillagerEntityRenderState,
    VillagerResemblingModel
> {
    private static final Identifier TEXTURE = MerchantVillagerMod.id(
        "textures/entity/villager/profession/merchant_clothing.png"
    );
    // The undyed burgundy is the identity established by the base artwork.
    private static final int DEFAULT_BURGUNDY = 0xFF992F45;

    public MerchantClothingFeatureRenderer(
        FeatureRendererContext<VillagerEntityRenderState, VillagerResemblingModel> context
    ) {
        super(context);
    }

    @Override
    public void render(
        MatrixStack matrices,
        OrderedRenderCommandQueue queue,
        int light,
        VillagerEntityRenderState state,
        float limbAngle,
        float limbDistance
    ) {
        if (state.invisible
            || state.villagerData == null
            || !state.villagerData.profession().matchesKey(ModVillagerProfessions.MERCHANT_KEY)) {
            return;
        }
        DyeColor dye = state.getData(MerchantClothingRenderState.COLOR);
        int argb = dye == null ? DEFAULT_BURGUNDY : 0xFF000000 | dye.getEntityColor();
        renderModel(getContextModel(), TEXTURE, matrices, queue, light, state, argb, 4);
    }
}
