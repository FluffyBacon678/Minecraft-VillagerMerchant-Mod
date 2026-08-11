package com.fluffybacon.merchantvillager.merchant;

import com.fluffybacon.merchantvillager.blockentity.MerchantPostBlockEntity;
import com.fluffybacon.merchantvillager.registry.ModVillagerProfessions;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.GlobalPos;

public final class MerchantInteractions {
    public static void initialize() {
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!(entity instanceof VillagerEntity villager)) {
                return ActionResult.PASS;
            }
            boolean merchantProfession = villager.getVillagerData().profession()
                .matchesKey(ModVillagerProfessions.MERCHANT_KEY);
            if (world.isClient()) {
                return merchantProfession ? ActionResult.SUCCESS : ActionResult.PASS;
            }
            ServerPlayerEntity serverPlayer = (ServerPlayerEntity)player;
            if (!serverPlayer.canInteractWithEntity(villager, 4.0)) {
                return merchantProfession ? ActionResult.FAIL : ActionResult.PASS;
            }
            MerchantWorkerState workerState =
                ((MerchantWorker)villager).merchantVillager$getState();
            AutomatedTradeExperience.releaseStoredExperience(
                (ServerWorld)world,
                villager,
                workerState
            );
            // A former Merchant may have lost its job site/profession before a
            // player could collect. Release any carried XP, then preserve that
            // villager's normal vanilla interaction instead of opening our UI.
            if (!merchantProfession) {
                return ActionResult.PASS;
            }
            MerchantPostBlockEntity post = resolvePost((ServerWorld)world, villager);
            if (post == null) {
                serverPlayer.sendMessage(
                    Text.translatable("message.merchant_villager.no_assigned_post"),
                    true
                );
                return ActionResult.SUCCESS;
            }
            post.assignMerchant(villager.getUuid());
            if (post.getAssignedMerchant().filter(villager.getUuid()::equals).isEmpty()) {
                serverPlayer.sendMessage(
                    Text.translatable("message.merchant_villager.post_already_assigned"),
                    true
                );
                return ActionResult.SUCCESS;
            }
            post.refreshCatalogue(true);
            serverPlayer.openHandledScreen(post);
            post.sendCatalogue(serverPlayer);
            return ActionResult.SUCCESS;
        });
    }

    private static MerchantPostBlockEntity resolvePost(ServerWorld world, VillagerEntity villager) {
        java.util.Optional<GlobalPos> jobSite =
            villager.getBrain().getOptionalRegisteredMemory(MemoryModuleType.JOB_SITE);
        if (jobSite.isPresent()
            && jobSite.get().dimension().equals(world.getRegistryKey())
            && world.getBlockEntity(jobSite.get().pos()) instanceof MerchantPostBlockEntity post) {
            return post;
        }
        MerchantWorkerState state = ((MerchantWorker)villager).merchantVillager$getState();
        return state.isPostIn(world)
            && world.getBlockEntity(state.postPos()) instanceof MerchantPostBlockEntity post
            ? post
            : null;
    }

    private MerchantInteractions() {
    }
}
