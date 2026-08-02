package com.fluffybacon.merchantvillager.command;

import com.fluffybacon.merchantvillager.blockentity.MerchantPostBlockEntity;
import com.fluffybacon.merchantvillager.merchant.MerchantWorker;
import com.fluffybacon.merchantvillager.merchant.MerchantWorkerState;
import com.fluffybacon.merchantvillager.merchant.ReservationManager;
import com.fluffybacon.merchantvillager.trade.OfferIdentity;
import com.fluffybacon.merchantvillager.trade.TradeInputMatcher;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.village.TradeOffer;

public final class MerchantVillagerCommands {
    public static void initialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(
                CommandManager.literal("merchant_villager")
                    .requires(CommandManager.requirePermissionLevel(CommandManager.GAMEMASTERS_CHECK))
                    .then(withOptionalPosition("inspect", MerchantVillagerCommands::inspect))
                    .then(withOptionalPosition("refresh", MerchantVillagerCommands::refresh))
                    .then(withOptionalPosition("pause", context -> pause(context, true)))
                    .then(withOptionalPosition("resume", context -> pause(context, false)))
                    .then(withOptionalPosition("release", MerchantVillagerCommands::release))
            )
        );
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> withOptionalPosition(
        String literal,
        com.mojang.brigadier.Command<ServerCommandSource> command
    ) {
        return CommandManager.literal(literal)
            .executes(command)
            .then(CommandManager.argument("pos", BlockPosArgumentType.blockPos()).executes(command));
    }

    private static int inspect(CommandContext<ServerCommandSource> context) {
        MerchantPostBlockEntity post = getPost(context);
        if (post == null) {
            return 0;
        }
        MerchantWorkerState worker = getWorker(context.getSource(), post);
        ServerCommandSource source = context.getSource();
        Entity target = worker == null || worker.targetUuid() == null
            ? null
            : source.getWorld().getEntity(worker.targetUuid());
        String targetDistance = target == null
            ? "unloaded"
            : String.format(
                java.util.Locale.ROOT,
                "%.2f",
                Math.sqrt(target.squaredDistanceTo(post.getPos().toCenterPos()))
            );
        String cargo = worker == null
            ? "[]"
            : worker.cargo().stream().filter(stack -> !stack.isEmpty()).toList().toString();
        int reservations = worker == null
            ? 0
            : ReservationManager.countWorker(
                source.getServer(),
                post.getAssignedMerchant().orElseThrow(),
                source.getWorld().getTime()
            );
        String chestCapacity = outputChestStatus(source, worker);
        context.getSource().sendFeedback(() -> Text.literal(
            "Merchant's Post " + post.getPos()
                + " | worker=" + post.getAssignedMerchant().map(Object::toString).orElse("none")
                + " | state=" + (worker == null ? "unloaded" : worker.state())
                + " | inventory=" + post.copyInventory().stream().filter(stack -> !stack.isEmpty()).toList()
                + " | cargo=" + cargo
                + " | targets=" + post.getOffers().stream().map(offer -> offer.targetUuid()).distinct().count()
                + " | offers=" + post.getOffers().size()
                + " | enabled=" + post.countEnabledOffers()
                + " | executable=" + countExecutable(source, post)
                + " | target=" + (worker == null ? "none" : worker.targetUuid())
                + " | targetDistance=" + targetDistance
                + " | progress=" + (worker == null ? "0/0"
                    : worker.completedExecutions() + "/" + worker.plannedExecutions())
                + " | pendingOffers=" + (worker == null ? 0 : worker.pendingTradeCount())
                + " | reservations=" + reservations
                + " | chest=" + (worker == null ? "none" : worker.outputChest())
                + " | chestCapacity=" + chestCapacity
                + " | retries=" + (worker == null ? 0 : worker.pathRetries())
                + " | timeout=" + (worker == null ? 0L
                    : Math.max(0L, worker.reservationExpiry() - source.getWorld().getTime()))
                + " | scanCooldown=" + post.getScanCooldown()
                + " | meetHalfway=" + (worker == null ? "n/a"
                    : worker.observedDistance() + "/" + worker.noConvergenceTicks())
                + " | failure=" + (worker == null ? post.getLastFailure() : worker.lastFailure())
        ), false);
        return 1;
    }

    private static int countExecutable(
        ServerCommandSource source, MerchantPostBlockEntity post
    ) {
        int executable = 0;
        for (var snapshot : post.getOffers()) {
            if (!post.isEnabled(snapshot.fingerprint())
                || snapshot.isOutOfStock()
                || !snapshot.targetAvailable()) {
                continue;
            }
            Entity entity = source.getWorld().getEntity(snapshot.targetUuid());
            if (!(entity instanceof MerchantEntity merchant)
                || snapshot.offerIndex() < 0
                || snapshot.offerIndex() >= merchant.getOffers().size()) {
                continue;
            }
            TradeOffer offer = merchant.getOffers().get(snapshot.offerIndex());
            String liveFingerprint = OfferIdentity.create(
                source.getWorld().getRegistryManager(),
                merchant.getUuid(),
                snapshot.offerIndex(),
                offer.getFirstBuyItem(),
                offer.getSecondBuyItem(),
                offer.getSellItem(),
                offer.getMaxUses(),
                offer.getMerchantExperience(),
                offer.getPriceMultiplier()
            );
            if (!snapshot.fingerprint().equals(liveFingerprint) || offer.isDisabled()) {
                continue;
            }
            int possible = TradeInputMatcher.matchingCount(
                post.copyInventory(),
                offer.getFirstBuyItem()
            ) / Math.max(1, offer.getDisplayedFirstBuyItem().getCount());
            if (offer.getSecondBuyItem().isPresent()) {
                possible = Math.min(
                    possible,
                    TradeInputMatcher.matchingCount(
                        post.copyInventory(),
                        offer.getSecondBuyItem().get()
                    ) / Math.max(1, offer.getDisplayedSecondBuyItem().getCount())
                );
            }
            if (possible > 0) {
                executable++;
            }
        }
        return executable;
    }

    private static String outputChestStatus(
        ServerCommandSource source, MerchantWorkerState worker
    ) {
        if (worker == null || worker.outputChest() == null) {
            return "not selected";
        }
        Inventory chest = com.fluffybacon.merchantvillager.inventory.OutputChestFinder.chestInventory(
            source.getWorld(),
            worker.outputChest()
        );
        if (chest == null) {
            return "unavailable";
        }
        int freeSlots = 0;
        for (int slot = 0; slot < chest.size(); slot++) {
            if (chest.getStack(slot).isEmpty()) {
                freeSlots++;
            }
        }
        return freeSlots + " empty slots";
    }

    private static int refresh(CommandContext<ServerCommandSource> context) {
        MerchantPostBlockEntity post = getPost(context);
        if (post == null) {
            return 0;
        }
        post.refreshCatalogue(true);
        context.getSource().sendFeedback(() -> Text.literal("Merchant catalogue refreshed safely"), true);
        return 1;
    }

    private static int pause(CommandContext<ServerCommandSource> context, boolean paused) {
        MerchantPostBlockEntity post = getPost(context);
        if (post == null) {
            return 0;
        }
        post.setPaused(paused);
        context.getSource().sendFeedback(
            () -> Text.literal(paused ? "Merchant work paused" : "Merchant work resumed"),
            true
        );
        return 1;
    }

    private static int release(CommandContext<ServerCommandSource> context) {
        MerchantPostBlockEntity post = getPost(context);
        if (post == null) {
            return 0;
        }
        MerchantWorkerState state = getWorker(context.getSource(), post);
        if (state != null && state.hasCargo()) {
            context.getSource().sendError(Text.literal("Cannot release: the active Merchant still owns cargo"));
            return 0;
        }
        int released = post.getAssignedMerchant()
            .map(uuid -> ReservationManager.releaseWorker(context.getSource().getServer(), uuid))
            .orElse(0);
        context.getSource().sendFeedback(() -> Text.literal("Released " + released + " stale reservations"), true);
        return released + 1;
    }

    private static MerchantPostBlockEntity getPost(CommandContext<ServerCommandSource> context) {
        BlockPos pos;
        try {
            pos = BlockPosArgumentType.getLoadedBlockPos(context, "pos");
        } catch (IllegalArgumentException | com.mojang.brigadier.exceptions.CommandSyntaxException absent) {
            ServerPlayerEntity player = context.getSource().getPlayer();
            if (player == null) {
                context.getSource().sendError(Text.literal("Supply a Merchant's Post position"));
                return null;
            }
            HitResult hit = player.raycast(8.0, 1.0F, false);
            pos = hit instanceof BlockHitResult blockHit ? blockHit.getBlockPos() : player.getBlockPos();
        }
        if (context.getSource().getWorld().getBlockEntity(pos) instanceof MerchantPostBlockEntity post) {
            return post;
        }
        context.getSource().sendError(Text.literal("No Merchant's Post at " + pos.toShortString()));
        return null;
    }

    private static MerchantWorkerState getWorker(ServerCommandSource source, MerchantPostBlockEntity post) {
        if (post.getAssignedMerchant().isEmpty()) {
            return null;
        }
        Entity entity = source.getWorld().getEntity(post.getAssignedMerchant().get());
        return entity instanceof MerchantWorker worker ? worker.merchantVillager$getState() : null;
    }

    private MerchantVillagerCommands() {
    }
}
