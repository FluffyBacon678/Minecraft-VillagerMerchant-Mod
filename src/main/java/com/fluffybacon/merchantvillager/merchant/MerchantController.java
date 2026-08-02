package com.fluffybacon.merchantvillager.merchant;

import com.fluffybacon.merchantvillager.blockentity.MerchantPostBlockEntity;
import com.fluffybacon.merchantvillager.config.MerchantVillagerConfig;
import com.fluffybacon.merchantvillager.inventory.OutputChestFinder;
import com.fluffybacon.merchantvillager.registry.ModVillagerProfessions;
import com.fluffybacon.merchantvillager.trade.OfferIdentity;
import com.fluffybacon.merchantvillager.trade.OfferSnapshot;
import com.fluffybacon.merchantvillager.trade.MeetHalfwayEvaluator;
import com.fluffybacon.merchantvillager.trade.TargetMerchantAvailability;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.brain.Activity;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.brain.WalkTarget;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.village.TradeOffer;

public final class MerchantController {
    public static void tick(ServerWorld world, VillagerEntity villager, MerchantWorkerState state) {
        boolean merchantProfession =
            villager.getVillagerData().profession().matchesKey(ModVillagerProfessions.MERCHANT_KEY);
        if (villager.isBaby()) {
            return;
        }
        if (state.postPos() != null && !state.isPostIn(world)) {
            stopMerchantNavigation(villager);
            state.status("Merchant is outside its assigned Merchant's Post dimension");
            return;
        }
        boolean validMerchantJobSite = hasValidMerchantJobSite(world, villager, state);
        if (!state.hasCargo()
            && ((!merchantProfession && state.postPos() != null)
                || (merchantProfession && state.postPos() != null && !validMerchantJobSite))) {
            detachInvalidPost(world, villager, state, merchantProfession
                ? "Merchant job site was lost"
                : "Merchant profession was lost");
            return;
        }
        if (!merchantProfession && !state.hasCargo()) {
            return;
        }
        state.tickAge();
        if (mustYieldToVanillaSafety(villager)) {
            villager.getNavigation().stop();
            state.status(villager.isSleeping()
                ? "Merchant sleeping"
                : "Merchant interrupted by danger");
            return;
        }
        MerchantPostBlockEntity post = resolveAndBindPost(world, villager, state);
        if (post == null) {
            recoverWithoutPost(world, villager, state);
            return;
        }
        // Vanilla navigation only searches 16 blocks by default. Merchant jobs
        // advertise a hard 66-block tether, so use that same bound for the
        // navigator instead of depending on a chain of partial paths.
        villager.getNavigation().setMaxFollowRange(MerchantVillagerConfig.EXTENDED_RADIUS);
        if (!merchantProfession
            && state.state() != MerchantState.RECOVERING
            && state.state() != MerchantState.RETURNING_TO_POST
            && state.state() != MerchantState.RETURNING_UNUSED_INPUTS
            && state.state() != MerchantState.FINDING_OUTPUT_CHEST
            && state.state() != MerchantState.TRAVELLING_TO_OUTPUT_CHEST
            && state.state() != MerchantState.DEPOSITING_REWARDS
            && state.state() != MerchantState.WAITING_FOR_OUTPUT_SPACE) {
            state.enter(MerchantState.RECOVERING, "Recovering cargo after profession loss");
        }
        if (ownsActiveReservation(state)
            && state.reservationExpiry() > 0L
            && world.getTime() >= state.reservationExpiry()) {
            releaseReservation(world, villager, state);
            recover(state, "Trade reservation expired");
        }
        if (villager.squaredDistanceTo(post.getPos().toCenterPos())
            > MerchantVillagerConfig.EXTENDED_RADIUS * MerchantVillagerConfig.EXTENDED_RADIUS
            && state.state() != MerchantState.RETURNING_TO_POST) {
            stopMerchantNavigation(villager);
            state.fail("Merchant exceeded its absolute tether");
            state.enter(MerchantState.RETURNING_TO_POST, "Returning inside Merchant's Post tether");
        }
        if (post.isPaused() && !state.hasCargo()) {
            state.enter(MerchantState.PAUSED, "Merchant paused");
        } else if (!post.isPaused() && state.state() == MerchantState.PAUSED) {
            state.enter(MerchantState.IDLE, "Merchant idle");
        }

        switch (state.state()) {
            case IDLE -> tickIdle(world, villager, state, post);
            case SCANNING -> {
                post.refreshCatalogue(false);
                state.enter(MerchantState.SELECTING_TARGET, "Selecting approved trade");
            }
            case SELECTING_TARGET -> selectTarget(world, villager, state, post);
            case WAITING_FOR_TARGET -> waitForTarget(world, villager, state, post);
            case PLANNING_BATCH -> planBatch(world, villager, state, post);
            case RESERVING_INPUTS -> reserveInputs(world, villager, state, post);
            case COLLECTING_INPUTS -> state.enter(
                MerchantState.TRAVELLING_TO_TARGET, "Travelling to target merchant"
            );
            case TRAVELLING_TO_TARGET -> travelToTarget(world, villager, state, post);
            case EXECUTING_TRADES -> executeTrades(world, villager, state, post);
            case RETURNING_TO_POST -> returnToPost(world, villager, state, post);
            case RETURNING_UNUSED_INPUTS -> returnUnusedInputs(state, post);
            case FINDING_OUTPUT_CHEST -> findOutputChest(world, villager, state, post);
            case TRAVELLING_TO_OUTPUT_CHEST -> travelToOutputChest(world, villager, state, post);
            case DEPOSITING_REWARDS -> depositRewards(world, villager, state, post);
            case WAITING_FOR_OUTPUT_SPACE -> {
                if (state.stateTicks() % MerchantVillagerConfig.OUTPUT_CHEST_RETRY_INTERVAL == 0) {
                    state.enter(MerchantState.FINDING_OUTPUT_CHEST, "Retrying output chest search");
                }
            }
            case RECOVERING -> beginRecovery(world, villager, state);
            case PAUSED -> {
            }
        }
    }

    private static MerchantPostBlockEntity resolveAndBindPost(
        ServerWorld world, VillagerEntity villager, MerchantWorkerState state
    ) {
        Optional<GlobalPos> jobSite = villager.getBrain().getOptionalRegisteredMemory(MemoryModuleType.JOB_SITE);
        if (jobSite.isPresent()
            && jobSite.get().dimension().equals(world.getRegistryKey())
            && (state.postPos() == null
                || (state.isPostIn(world) && state.postPos().equals(jobSite.get().pos())))
            && world.getBlockEntity(jobSite.get().pos()) instanceof MerchantPostBlockEntity post) {
            state.bindPost(world, jobSite.get().pos());
            post.assignMerchant(villager.getUuid());
            return post.getAssignedMerchant().filter(villager.getUuid()::equals).isPresent() ? post : null;
        }
        if (state.isPostIn(world)
            && world.getBlockEntity(state.postPos()) instanceof MerchantPostBlockEntity post
            && post.getAssignedMerchant().filter(villager.getUuid()::equals).isPresent()) {
            return post;
        }
        return null;
    }

    private static boolean hasValidMerchantJobSite(
        ServerWorld world, VillagerEntity villager, MerchantWorkerState state
    ) {
        if (!state.isPostIn(world) || state.postPos() == null) {
            return false;
        }
        return villager.getBrain().getOptionalRegisteredMemory(MemoryModuleType.JOB_SITE)
            .filter(jobSite -> jobSite.dimension().equals(world.getRegistryKey()))
            .map(GlobalPos::pos)
            .filter(state.postPos()::equals)
            .filter(jobSite -> world.getBlockEntity(jobSite) instanceof MerchantPostBlockEntity)
            .isPresent();
    }

    /**
     * Releases both the mod assignment and vanilla POI ticket. This only runs
     * once cargo has been settled, so a profession or job-site change cannot
     * make reserved inputs or completed rewards disappear.
     */
    private static void detachInvalidPost(
        ServerWorld world, VillagerEntity villager, MerchantWorkerState state, String reason
    ) {
        BlockPos formerPost = state.isPostIn(world) ? state.postPos() : null;
        if (formerPost != null
            && world.getBlockEntity(formerPost) instanceof MerchantPostBlockEntity post
            && post.getAssignedMerchant().filter(villager.getUuid()::equals).isPresent()) {
            post.clearMerchant(villager.getUuid());
            if (world.getPointOfInterestStorage().getType(formerPost)
                .filter(type -> type.matchesKey(
                    com.fluffybacon.merchantvillager.registry.ModPointOfInterests.MERCHANT_POST_KEY
                ))
                .isPresent()
                && world.getPointOfInterestStorage().releaseTicket(formerPost)) {
                world.getSubscriptionTracker().onPoiUpdated(formerPost);
            }
        }
        villager.getBrain().forget(MemoryModuleType.JOB_SITE);
        villager.getNavigation().setMaxFollowRange(0.0F);
        ReservationManager.releaseWorker(world.getServer(), villager.getUuid());
        state.clearPostAssignment(reason);
    }

    /**
     * Merchant work is subordinate to vanilla survival and village activities.
     * The state and cargo remain untouched so the same physical job can resume
     * after panic, raids, hiding, or sleep ends.
     */
    private static boolean mustYieldToVanillaSafety(VillagerEntity villager) {
        return villager.isSleeping()
            || villager.getBrain().hasActivity(Activity.PANIC)
            || villager.getBrain().hasActivity(Activity.PRE_RAID)
            || villager.getBrain().hasActivity(Activity.RAID)
            || villager.getBrain().hasActivity(Activity.HIDE);
    }

    private static boolean ownsActiveReservation(MerchantWorkerState state) {
        return switch (state.state()) {
            case COLLECTING_INPUTS, TRAVELLING_TO_TARGET, WAITING_FOR_TARGET, EXECUTING_TRADES -> true;
            default -> false;
        };
    }

    private static void tickIdle(
        ServerWorld world, VillagerEntity villager, MerchantWorkerState state, MerchantPostBlockEntity post
    ) {
        if (state.hasCargo()) {
            state.enter(MerchantState.RECOVERING, "Recovering unresolved cargo");
            return;
        }
        long dayTime = world.getTimeOfDay() % 24000L;
        boolean working = villager.getBrain().hasActivity(Activity.WORK)
            || (dayTime >= 2000L && dayTime <= 11000L);
        if (working && !villager.isSleeping() && !villager.getBrain().hasActivity(Activity.PANIC)) {
            state.enter(MerchantState.SCANNING, "Scanning nearby target merchants");
        }
    }

    private static void selectTarget(
        ServerWorld world, VillagerEntity villager, MerchantWorkerState state, MerchantPostBlockEntity post
    ) {
        List<OfferSnapshot> eligible = post.getOffers().stream()
            .filter(offer -> post.isEnabled(offer.fingerprint()))
            .filter(offer -> !post.isCoolingDown(offer.fingerprint()))
            .filter(offer -> !offer.isOutOfStock() && offer.targetAvailable())
            .filter(offer -> isTargetSelectable(world, post, offer))
            .filter(offer -> fundable(world, post, offer) > 0)
            .toList();
        if (eligible.isEmpty()) {
            state.enter(MerchantState.IDLE, post.countEnabledOffers() == 0
                ? "No enabled trades"
                : "Waiting for trade materials");
            return;
        }
        List<java.util.UUID> targets = eligible.stream()
            .map(OfferSnapshot::targetUuid)
            .distinct()
            .sorted()
            .toList();
        Map<java.util.UUID, Integer> targetIndexes = new HashMap<>();
        for (int index = 0; index < targets.size(); index++) {
            targetIndexes.put(targets.get(index), index);
        }
        Map<java.util.UUID, Long> executableAtTarget = eligible.stream().collect(
            java.util.stream.Collectors.groupingBy(OfferSnapshot::targetUuid, java.util.stream.Collectors.counting())
        );
        int cursor = Math.floorMod(post.getSelectionCursor(), targets.size());
        List<OfferSnapshot> candidates = eligible.stream().sorted(Comparator
                .comparing(OfferSnapshot::wanderingTrader).reversed()
                .thenComparing(offer -> offer.distanceSquared()
                    > MerchantVillagerConfig.NORMAL_RADIUS * MerchantVillagerConfig.NORMAL_RADIUS)
                .thenComparing(
                    offer -> executableAtTarget.getOrDefault(offer.targetUuid(), 0L),
                    Comparator.reverseOrder()
                )
                .thenComparingInt(offer ->
                    Math.floorMod(targetIndexes.get(offer.targetUuid()) - cursor, targets.size())
                )
                .thenComparingDouble(OfferSnapshot::distanceSquared)
                .thenComparing(OfferSnapshot::fingerprint))
            .toList();
        OfferSnapshot selected = candidates.get(0);
        Optional<BlockPos> chest = OutputChestFinder.find(
            world, post.getPos(), List.of(selected.output()), villager
        );
        if (chest.isEmpty()) {
            state.fail("No output chest found");
            state.enter(MerchantState.IDLE, "No output chest found");
            return;
        }
        state.target(selected.targetUuid(), selected.offerIndex(), selected.fingerprint());
        post.advanceSelectionCursor(targetIndexes.get(selected.targetUuid()), targets.size());
        state.outputChest(chest.get());
        Entity selectedTarget = world.getEntity(selected.targetUuid());
        double selectedDistanceSquared = selectedTarget == null
            ? selected.distanceSquared()
            : selectedTarget.squaredDistanceTo(post.getPos().toCenterPos());
        if (selectedDistanceSquared > MerchantVillagerConfig.NORMAL_RADIUS
            * MerchantVillagerConfig.NORMAL_RADIUS) {
            state.observedDistance(Math.sqrt(selectedDistanceSquared));
            state.enter(MerchantState.WAITING_FOR_TARGET, "Observing meet-halfway target");
        } else {
            state.enter(MerchantState.PLANNING_BATCH, "Planning target visit");
        }
    }

    private static int fundable(ServerWorld world, MerchantPostBlockEntity post, OfferSnapshot offer) {
        TradeOffer liveOffer = resolveSnapshotOffer(world, offer);
        if (liveOffer == null || liveOffer.isDisabled()) {
            return 0;
        }
        int first = com.fluffybacon.merchantvillager.trade.TradeInputMatcher.matchingCount(
            post.copyInventory(), offer.firstInput()
        ) / Math.max(1, liveOffer.getDisplayedFirstBuyItem().getCount());
        if (offer.secondInput().isPresent()) {
            int second = com.fluffybacon.merchantvillager.trade.TradeInputMatcher.matchingCount(
                post.copyInventory(), offer.secondInput().get()
            ) / Math.max(1, liveOffer.getDisplayedSecondBuyItem().getCount());
            first = Math.min(first, second);
        }
        return Math.min(first, liveOffer.getMaxUses() - liveOffer.getUses());
    }

    private static boolean isTargetSelectable(
        ServerWorld world, MerchantPostBlockEntity post, OfferSnapshot snapshot
    ) {
        Entity entity = world.getEntity(snapshot.targetUuid());
        if (!(entity instanceof MerchantEntity target)
            || !target.isAlive()
            || target.isRemoved()
            || !TargetMerchantAvailability.canTradeNow(target)
            || target.squaredDistanceTo(post.getPos().toCenterPos())
                > MerchantVillagerConfig.EXTENDED_RADIUS * MerchantVillagerConfig.EXTENDED_RADIUS) {
            return false;
        }
        return resolveSnapshotOffer(world, snapshot) != null;
    }

    private static TradeOffer resolveSnapshotOffer(ServerWorld world, OfferSnapshot snapshot) {
        Entity entity = world.getEntity(snapshot.targetUuid());
        if (!(entity instanceof MerchantEntity target)
            || snapshot.offerIndex() < 0
            || snapshot.offerIndex() >= target.getOffers().size()) {
            return null;
        }
        TradeOffer offer = target.getOffers().get(snapshot.offerIndex());
        String fingerprint = OfferIdentity.create(
            world.getRegistryManager(),
            target.getUuid(),
            snapshot.offerIndex(),
            offer.getFirstBuyItem(),
            offer.getSecondBuyItem(),
            offer.getSellItem(),
            offer.getMaxUses(),
            offer.getMerchantExperience(),
            offer.getPriceMultiplier()
        );
        return fingerprint.equals(snapshot.fingerprint()) ? offer : null;
    }

    private static void observeExtendedTarget(
        ServerWorld world, VillagerEntity villager, MerchantWorkerState state, MerchantPostBlockEntity post
    ) {
        MerchantEntity target = resolveTarget(world, state);
        if (target == null) {
            cancelBeforeReservation(state, "Meet-halfway target unloaded");
            return;
        }
        double current = Math.sqrt(target.squaredDistanceTo(post.getPos().toCenterPos()));
        if (current > MerchantVillagerConfig.EXTENDED_RADIUS) {
            cancelBeforeReservation(state, "Target moved outside extended radius");
            return;
        }
        if (state.stateTicks() < MerchantVillagerConfig.MEET_HALFWAY_OBSERVATION_TICKS) {
            return;
        }
        Vec3d towardPost = post.getPos().toCenterPos().subtract(target.getEntityPos()).normalize();
        double projection = target.getVelocity().dotProduct(towardPost);
        if (MeetHalfwayEvaluator.converging(state.observedDistance(), current, projection)) {
            state.enter(MerchantState.PLANNING_BATCH, "Meet-halfway convergence confirmed");
        } else {
            cancelBeforeReservation(state, "Extended target is not approaching");
        }
    }

    private static void waitForTarget(
        ServerWorld world, VillagerEntity villager, MerchantWorkerState state, MerchantPostBlockEntity post
    ) {
        if (!state.hasCargo()) {
            observeExtendedTarget(world, villager, state, post);
            return;
        }
        MerchantEntity target = resolveTarget(world, state);
        if (target == null || !target.isAlive()) {
            recover(state, "Target merchant disappeared");
            return;
        }
        if (target.squaredDistanceTo(post.getPos().toCenterPos())
            > MerchantVillagerConfig.EXTENDED_RADIUS * MerchantVillagerConfig.EXTENDED_RADIUS) {
            recover(state, "Target moved outside range");
            return;
        }
        if (TargetMerchantAvailability.canTradeNow(target)) {
            state.enter(MerchantState.TRAVELLING_TO_TARGET, "Target merchant is available");
        } else if (state.stateTicks() >= MerchantVillagerConfig.TARGET_BUSY_TIMEOUT) {
            recover(state, "Target merchant remained occupied");
        } else {
            stopMerchantNavigation(villager);
            state.status(target.hasCustomer()
                ? "Merchant is currently trading with a player"
                : "Waiting for target merchant to become available");
        }
    }

    private static void planBatch(
        ServerWorld world, VillagerEntity villager, MerchantWorkerState state, MerchantPostBlockEntity post
    ) {
        MerchantEntity target = resolveTarget(world, state);
        TradeOffer offer = resolveCurrentOffer(world, target, state);
        if (target == null || offer == null || !TargetMerchantAvailability.canTradeNow(target)) {
            cancelBeforeReservation(state, "Target merchant unavailable");
            return;
        }
        Inventory outputChest = state.outputChest() == null
            ? null
            : OutputChestFinder.touchingChestInventory(world, post.getPos(), state.outputChest());
        Optional<MerchantWorkOrder> plan = MerchantBatchPlanner.plan(
            post,
            state,
            target.getUuid(),
            batchCandidates(world, post, state, target),
            outputChest
        );
        if (plan.isEmpty()) {
            cancelBeforeReservation(state, "Missing exact trade materials");
            return;
        }
        state.status("Planning " + plan.get().trades().size() + " approved offers");
        state.enter(MerchantState.RESERVING_INPUTS, "Reserving trade materials");
    }

    private static void reserveInputs(
        ServerWorld world, VillagerEntity villager, MerchantWorkerState state, MerchantPostBlockEntity post
    ) {
        MerchantEntity target = resolveTarget(world, state);
        TradeOffer offer = resolveCurrentOffer(world, target, state);
        if (target == null || offer == null) {
            cancelBeforeReservation(state, "Offer changed before reservation");
            return;
        }
        Inventory outputChest = state.outputChest() == null
            ? null
            : OutputChestFinder.touchingChestInventory(world, post.getPos(), state.outputChest());
        Optional<MerchantWorkOrder> plan = MerchantBatchPlanner.plan(
            post,
            state,
            target.getUuid(),
            batchCandidates(world, post, state, target),
            outputChest
        );
        if (plan.isEmpty()
            || !ReservationManager.reserveAll(
                world.getServer(),
                villager.getUuid(),
                plan.get().trades().stream()
                    .map(trade -> new ReservationManager.Request(
                        target.getUuid(), trade.fingerprint(), trade.executions()
                    ))
                    .toList(),
                world.getTime()
            )
            || !MerchantBatchPlanner.reserve(post, state, plan.get())) {
            ReservationManager.releaseWorker(world.getServer(), villager.getUuid());
            cancelBeforeReservation(state, "Unable to reserve trade materials");
            return;
        }
        state.setWorkOrder(plan.get());
        state.reservationExpiry(world.getTime() + MerchantVillagerConfig.RESERVATION_TIMEOUT);
        state.enter(
            MerchantState.COLLECTING_INPUTS,
            "Collected materials for " + plan.get().totalExecutions() + " trades"
        );
    }

    private static void travelToTarget(
        ServerWorld world, VillagerEntity villager, MerchantWorkerState state, MerchantPostBlockEntity post
    ) {
        MerchantEntity target = resolveTarget(world, state);
        if (target == null || !target.isAlive()) {
            stopMerchantNavigation(villager);
            recover(state, "Target merchant disappeared");
            return;
        }
        if (!TargetMerchantAvailability.canTradeNow(target)) {
            stopMerchantNavigation(villager);
            state.enter(MerchantState.WAITING_FOR_TARGET, target.hasCustomer()
                ? "Merchant is currently trading with a player"
                : "Waiting for target merchant to become available");
            return;
        }
        double targetFromPost = target.squaredDistanceTo(post.getPos().toCenterPos());
        if (targetFromPost > MerchantVillagerConfig.EXTENDED_RADIUS
            * MerchantVillagerConfig.EXTENDED_RADIUS) {
            stopMerchantNavigation(villager);
            recover(state, "Target moved outside range");
            return;
        }
        if (villager.squaredDistanceTo(target) <= MerchantVillagerConfig.INTERACTION_DISTANCE_SQUARED
            && MerchantTradeExecutor.hasInteractionLine(villager, target)) {
            stopMerchantNavigation(villager);
            state.enter(MerchantState.EXECUTING_TRADES, "Trading with target merchant");
            return;
        }
        if (targetFromPost > MerchantVillagerConfig.NORMAL_RADIUS
            * MerchantVillagerConfig.NORMAL_RADIUS
            && state.stateTicks() % MerchantVillagerConfig.MEET_HALFWAY_OBSERVATION_TICKS == 0) {
            double distance = Math.sqrt(targetFromPost);
            if (state.observedDistance() - distance < MerchantVillagerConfig.MEET_HALFWAY_MIN_PROGRESS) {
                state.missedConvergence();
            } else {
                state.resetConvergence();
            }
            state.observedDistance(distance);
            if (state.noConvergenceTicks() * MerchantVillagerConfig.MEET_HALFWAY_OBSERVATION_TICKS
                >= MerchantVillagerConfig.MEET_HALFWAY_FAILURE_TICKS) {
                recover(state, "Meet-halfway target stopped approaching");
                return;
            }
        }
        navigateToTarget(world, villager, target, state, post);
    }

    private static void executeTrades(
        ServerWorld world, VillagerEntity villager, MerchantWorkerState state, MerchantPostBlockEntity post
    ) {
        MerchantEntity target = resolveTarget(world, state);
        if (target == null
            || villager.squaredDistanceTo(target) > MerchantVillagerConfig.INTERACTION_DISTANCE_SQUARED
            || !post.isEnabled(state.offerFingerprint())) {
            recover(state, "Trade revalidation failed");
            return;
        }
        if (!TargetMerchantAvailability.canTradeNow(target)) {
            state.enter(MerchantState.WAITING_FOR_TARGET, target.hasCustomer()
                ? "Merchant is currently trading with a player"
                : "Waiting for target merchant to become available");
            return;
        }
        int attempts = Math.min(
            MerchantVillagerConfig.MAX_EXECUTIONS_PER_TICK,
            state.plannedExecutions() - state.completedExecutions()
        );
        int completedBefore = state.completedExecutions();
        for (int i = 0; i < attempts; i++) {
            if (!MerchantTradeExecutor.executeOne(world, villager, target, state, post)) {
                break;
            }
        }
        TradeOffer current = resolveCurrentOffer(world, target, state);
        if (state.completedExecutions() >= state.plannedExecutions()
            || current == null
            || current.isDisabled()) {
            if (state.advanceTradePlan()) {
                state.status("Continuing same-target approved batch");
            } else {
                releaseReservation(world, villager, state);
                state.enter(MerchantState.RETURNING_TO_POST, "Returning to Merchant's Post");
            }
        } else if (state.completedExecutions() == completedBefore) {
            // A live-looking offer can still fail because its effective price
            // changed or the output no longer fits. Retrying every tick would
            // strand cargo forever, so return the exact unused inputs instead.
            recover(state, "Trade price or cargo capacity changed");
        }
    }

    private static void returnToPost(
        ServerWorld world, VillagerEntity villager, MerchantWorkerState state, MerchantPostBlockEntity post
    ) {
        if (villager.squaredDistanceTo(post.getPos().toCenterPos()) <= 9.0) {
            stopMerchantNavigation(villager);
            state.enter(
                state.hasInputs() ? MerchantState.RETURNING_UNUSED_INPUTS : MerchantState.FINDING_OUTPUT_CHEST,
                state.hasInputs() ? "Returning unused trade inputs" : "Searching for output chest"
            );
            return;
        }
        navigate(villager, post.getPos(), state, "Unable to return to Merchant's Post");
    }

    private static void returnUnusedInputs(MerchantWorkerState state, MerchantPostBlockEntity post) {
        for (int slot = 0; slot < state.cargo().size(); slot++) {
            if (!state.isRewardSlot(slot) && !state.cargo().get(slot).isEmpty()) {
                ItemStack remainder = post.insertReturnedInput(state.cargo().get(slot));
                state.cargo().set(slot, remainder);
                if (remainder.isEmpty()) {
                    state.clearSlot(slot);
                }
            }
        }
        if (state.hasInputs()) {
            state.status("Waiting to return unused inputs");
        } else {
            state.enter(MerchantState.FINDING_OUTPUT_CHEST, "Searching for output chest");
        }
    }

    private static void findOutputChest(
        ServerWorld world, VillagerEntity villager, MerchantWorkerState state, MerchantPostBlockEntity post
    ) {
        if (!state.hasRewards()) {
            finishJob(world, villager, state);
            return;
        }
        List<ItemStack> rewards = rewardStacks(state);
        Optional<BlockPos> chest = OutputChestFinder.find(world, post.getPos(), rewards, villager);
        if (chest.isEmpty()) {
            state.enter(MerchantState.WAITING_FOR_OUTPUT_SPACE, "No output chest or output space");
            return;
        }
        state.outputChest(chest.get());
        state.enter(MerchantState.TRAVELLING_TO_OUTPUT_CHEST, "Travelling to output chest");
    }

    private static void travelToOutputChest(
        ServerWorld world, VillagerEntity villager, MerchantWorkerState state, MerchantPostBlockEntity post
    ) {
        BlockPos chest = state.outputChest();
        if (chest == null
            || OutputChestFinder.touchingChestInventory(world, post.getPos(), chest) == null) {
            stopMerchantNavigation(villager);
            state.enter(MerchantState.FINDING_OUTPUT_CHEST, "Output chest changed");
            return;
        }
        if (villager.squaredDistanceTo(chest.toCenterPos()) <= 9.0) {
            stopMerchantNavigation(villager);
            state.enter(MerchantState.DEPOSITING_REWARDS, "Depositing trade rewards");
            return;
        }
        navigate(villager, chest, state, "Output chest unreachable");
    }

    private static void depositRewards(
        ServerWorld world, VillagerEntity villager, MerchantWorkerState state, MerchantPostBlockEntity post
    ) {
        Inventory chest = state.outputChest() == null
            ? null
            : OutputChestFinder.touchingChestInventory(world, post.getPos(), state.outputChest());
        if (chest == null) {
            state.enter(MerchantState.FINDING_OUTPUT_CHEST, "Output chest removed");
            return;
        }
        for (int slot = 0; slot < state.cargo().size(); slot++) {
            if (state.isRewardSlot(slot) && !state.cargo().get(slot).isEmpty()) {
                ItemStack remainder = HopperBlockEntity.transfer(null, chest, state.cargo().get(slot).copy(), null);
                state.cargo().set(slot, remainder);
                if (remainder.isEmpty()) {
                    state.clearSlot(slot);
                }
            }
        }
        chest.markDirty();
        if (state.hasRewards()) {
            state.enter(MerchantState.WAITING_FOR_OUTPUT_SPACE, "Output chest full");
        } else {
            finishJob(world, villager, state);
        }
    }

    private static void beginRecovery(
        ServerWorld world, VillagerEntity villager, MerchantWorkerState state
    ) {
        releaseReservation(world, villager, state);
        if (state.hasInputs()) {
            state.enter(MerchantState.RETURNING_TO_POST, "Returning unused trade inputs");
        } else if (state.hasRewards()) {
            state.enter(MerchantState.RETURNING_TO_POST, "Returning with trade rewards");
        } else {
            state.clearTarget();
            state.enter(MerchantState.IDLE, "Merchant idle");
        }
    }

    private static void finishJob(ServerWorld world, VillagerEntity villager, MerchantWorkerState state) {
        releaseReservation(world, villager, state);
        state.clearTarget();
        state.outputChest(null);
        state.enter(MerchantState.IDLE, "Merchant idle");
    }

    private static void recover(MerchantWorkerState state, String reason) {
        state.fail(reason);
        state.enter(MerchantState.RECOVERING, reason);
    }

    private static void cancelBeforeReservation(MerchantWorkerState state, String reason) {
        state.fail(reason);
        state.clearTarget();
        state.enter(MerchantState.IDLE, reason);
    }

    private static void recoverWithoutPost(
        ServerWorld world, VillagerEntity villager, MerchantWorkerState state
    ) {
        if (!state.hasCargo()) {
            state.enter(MerchantState.IDLE, "This Merchant has no assigned Merchant's Post");
            return;
        }
        BlockPos former = state.postPos();
        if (former == null) {
            state.dropCargoOnce(villager);
            return;
        }
        if (villager.squaredDistanceTo(former.toCenterPos()) > 9.0) {
            if (state.stateTicks() > 200) {
                villager.getNavigation().stop();
                state.dropCargoOnce(villager);
                ReservationManager.releaseWorker(world.getServer(), villager.getUuid());
                return;
            }
            villager.getNavigation().startMovingTo(
                former.getX() + 0.5, former.getY(), former.getZ() + 0.5, 0.55
            );
        } else if (state.stateTicks() > 200 || state.postDestroyed()) {
            state.dropCargoOnce(villager);
            ReservationManager.releaseWorker(world.getServer(), villager.getUuid());
        }
    }

    private static MerchantEntity resolveTarget(ServerWorld world, MerchantWorkerState state) {
        if (state.targetUuid() == null) {
            return null;
        }
        Entity entity = world.getEntity(state.targetUuid());
        return entity instanceof MerchantEntity merchant ? merchant : null;
    }

    private static TradeOffer resolveCurrentOffer(
        ServerWorld world, MerchantEntity target, MerchantWorkerState state
    ) {
        if (target == null || state.offerIndex() < 0 || state.offerIndex() >= target.getOffers().size()) {
            return null;
        }
        TradeOffer offer = target.getOffers().get(state.offerIndex());
        String fingerprint = OfferIdentity.create(
            world.getRegistryManager(),
            target.getUuid(),
            state.offerIndex(),
            offer.getFirstBuyItem(),
            offer.getSecondBuyItem(),
            offer.getSellItem(),
            offer.getMaxUses(),
            offer.getMerchantExperience(),
            offer.getPriceMultiplier()
        );
        return fingerprint.equals(state.offerFingerprint()) ? offer : null;
    }

    private static List<MerchantBatchPlanner.Candidate> batchCandidates(
        ServerWorld world,
        MerchantPostBlockEntity post,
        MerchantWorkerState state,
        MerchantEntity target
    ) {
        return post.getOffers().stream()
            .filter(snapshot -> snapshot.targetUuid().equals(target.getUuid()))
            .filter(snapshot -> post.isEnabled(snapshot.fingerprint()))
            .filter(snapshot -> !post.isCoolingDown(snapshot.fingerprint()))
            .map(snapshot -> new java.util.AbstractMap.SimpleImmutableEntry<>(
                snapshot,
                resolveSnapshotOffer(world, snapshot)
            ))
            .filter(entry -> entry.getValue() != null && !entry.getValue().isDisabled())
            .filter(entry -> fundable(world, post, entry.getKey()) > 0)
            .sorted(Comparator
                .comparing((java.util.Map.Entry<OfferSnapshot, TradeOffer> entry) ->
                    !entry.getKey().fingerprint().equals(state.offerFingerprint()))
                .thenComparingInt(entry -> entry.getKey().offerIndex()))
            .map(entry -> new MerchantBatchPlanner.Candidate(
                entry.getKey().fingerprint(),
                entry.getKey().offerIndex(),
                entry.getValue()
            ))
            .toList();
    }

    private static void navigateToTarget(
        ServerWorld world,
        VillagerEntity villager,
        Entity target,
        MerchantWorkerState state,
        MerchantPostBlockEntity post
    ) {
        // Villager core tasks own WALK_TARGET and PATH. Publishing the courier
        // destination here keeps the vanilla WORK activity and our direct
        // navigation request aligned instead of letting the job-site task
        // replace the merchant route on the next brain tick.
        villager.getBrain().remember(
            MemoryModuleType.WALK_TARGET,
            new WalkTarget(target, 0.55F, 3)
        );
        boolean initialAttempt = state.stateTicks() <= 1;
        boolean scheduledRetry =
            state.stateTicks() % MerchantVillagerConfig.PATH_RETRY_INTERVAL == 0;
        if (!initialAttempt && !scheduledRetry) {
            return;
        }
        if (!villager.getNavigation().isIdle()) {
            // EntityNavigation may keep an unfinished route to an entity's old
            // position after that entity moves. It can also reuse a route that
            // reports reaching a distant target but ends at a stale endpoint.
            // Refresh the initial route and scheduled retries from both
            // entities' current positions.
            villager.getNavigation().stop();
        }
        if (!villager.getNavigation().startMovingTo(
            target.getX(), target.getY(), target.getZ(), 0.55
        )) {
            state.pathRetried();
            if (state.pathRetries() >= MerchantVillagerConfig.MAX_PATH_RETRIES) {
                post.markUnreachable(
                    state.offerFingerprint(),
                    world.getTime() + MerchantVillagerConfig.UNREACHABLE_COOLDOWN
                );
                stopMerchantNavigation(villager);
                recover(state, "Target unreachable");
            }
        } else {
            state.pathSucceeded();
            if (pathExceedsTether(villager.getNavigation().getCurrentPath(), post.getPos())) {
                stopMerchantNavigation(villager);
                post.markUnreachable(
                    state.offerFingerprint(),
                    world.getTime() + MerchantVillagerConfig.UNREACHABLE_COOLDOWN
                );
                recover(state, "Target path would exceed Merchant's Post tether");
            }
        }
    }

    private static boolean pathExceedsTether(Path path, BlockPos postPos) {
        if (path == null) {
            return false;
        }
        double maximumSquared =
            MerchantVillagerConfig.EXTENDED_RADIUS * MerchantVillagerConfig.EXTENDED_RADIUS;
        for (int node = path.getCurrentNodeIndex(); node < path.getLength(); node++) {
            if (path.getNodePos(node).getSquaredDistance(postPos) > maximumSquared) {
                return true;
            }
        }
        return false;
    }

    private static void navigate(
        VillagerEntity villager, BlockPos target, MerchantWorkerState state, String failure
    ) {
        villager.getBrain().remember(
            MemoryModuleType.WALK_TARGET,
            new WalkTarget(target, 0.55F, 2)
        );
        boolean initialAttempt = state.stateTicks() <= 1;
        boolean scheduledRetry =
            state.stateTicks() % MerchantVillagerConfig.PATH_RETRY_INTERVAL == 0;
        if (!initialAttempt && !scheduledRetry) {
            return;
        }
        if (!villager.getNavigation().isIdle()) {
            // Force a fresh route instead of allowing EntityNavigation to
            // return its cached unfinished path for the same block target.
            villager.getNavigation().stop();
        }
        if (!villager.getNavigation().startMovingTo(
            target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 0.55
        )) {
            state.pathRetried();
            if (state.pathRetries() >= MerchantVillagerConfig.MAX_PATH_RETRIES) {
                stopMerchantNavigation(villager);
                recover(state, failure);
            }
        } else {
            state.pathSucceeded();
        }
    }

    private static void stopMerchantNavigation(VillagerEntity villager) {
        villager.getBrain().forget(MemoryModuleType.WALK_TARGET);
        villager.getBrain().forget(MemoryModuleType.PATH);
        villager.getNavigation().stop();
    }

    private static List<ItemStack> rewardStacks(MerchantWorkerState state) {
        java.util.ArrayList<ItemStack> rewards = new java.util.ArrayList<>();
        for (int slot = 0; slot < state.cargo().size(); slot++) {
            if (state.isRewardSlot(slot) && !state.cargo().get(slot).isEmpty()) {
                rewards.add(state.cargo().get(slot));
            }
        }
        return rewards;
    }

    private static void releaseReservation(
        ServerWorld world, VillagerEntity villager, MerchantWorkerState state
    ) {
        ReservationManager.releaseWorker(world.getServer(), villager.getUuid());
        state.reservationExpiry(0L);
    }

    private MerchantController() {
    }
}
