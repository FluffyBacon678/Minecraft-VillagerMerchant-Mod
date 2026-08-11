package com.fluffybacon.merchantvillager.merchant;

import com.fluffybacon.merchantvillager.config.MerchantVillagerConfig;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.Uuids;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.village.TradedItem;
import org.jspecify.annotations.Nullable;

public final class MerchantWorkerState {
    private DefaultedList<ItemStack> cargo =
        DefaultedList.ofSize(MerchantVillagerConfig.CARGO_SIZE, ItemStack.EMPTY);
    private final BitSet rewardSlots = new BitSet(MerchantVillagerConfig.CARGO_SIZE);
    private MerchantState state = MerchantState.IDLE;
    @Nullable private BlockPos postPos;
    private String postDimension = "";
    @Nullable private UUID targetUuid;
    private String offerFingerprint = "";
    private int offerIndex = -1;
    @Nullable private BlockPos outputChest;
    private int plannedExecutions;
    private int completedExecutions;
    private int stateTicks;
    private int pathRetries;
    private double pathObservationDistance = -1.0;
    private int pathStallObservations;
    private boolean directApproachActive;
    private long reservationExpiry;
    private String status = "Merchant idle";
    private String lastFailure = "";
    private boolean cargoDropped;
    private boolean postDestroyed;
    private double observedDistance;
    private int noConvergenceTicks;
    private int storedExperience;
    private int interactionDurationTicks;
    private int interactionElapsedTicks;
    private final List<PendingTrade> pendingTrades = new ArrayList<>();

    public MerchantState state() {
        return state;
    }

    public void enter(MerchantState next, String status) {
        this.state = next;
        this.status = status;
        this.stateTicks = 0;
        this.pathRetries = 0;
        this.pathObservationDistance = -1.0;
        this.pathStallObservations = 0;
        this.directApproachActive = false;
        if (next != MerchantState.TRADING_BUSY) {
            this.interactionDurationTicks = 0;
            this.interactionElapsedTicks = 0;
        }
    }

    public void tickAge() {
        stateTicks++;
    }

    public int stateTicks() {
        return stateTicks;
    }

    public String status() {
        return status;
    }

    public void status(String status) {
        this.status = status;
    }

    public String lastFailure() {
        return lastFailure;
    }

    public void fail(String failure) {
        this.lastFailure = failure;
        this.status = failure;
    }

    public void clearFailure() {
        this.lastFailure = "";
    }

    @Nullable
    public BlockPos postPos() {
        return postPos;
    }

    public void bindPost(ServerWorld world, BlockPos pos) {
        postPos = pos.toImmutable();
        postDimension = world.getRegistryKey().getValue().toString();
        postDestroyed = false;
    }

    public boolean isPostIn(ServerWorld world) {
        return postPos != null && postDimension.equals(world.getRegistryKey().getValue().toString());
    }

    /**
     * Clears a stale worker/post relationship after the villager no longer
     * owns a valid Merchant job site. Cargo recovery deliberately happens
     * before this method is called, so no physical items are discarded here.
     */
    public void clearPostAssignment(String status) {
        postPos = null;
        postDimension = "";
        postDestroyed = false;
        outputChest = null;
        reservationExpiry = 0L;
        clearTarget();
        enter(MerchantState.IDLE, status);
    }

    public void onPostDestroyed(BlockPos formerPos) {
        postPos = formerPos.toImmutable();
        postDestroyed = true;
        fail("Merchant's Post destroyed");
        enter(MerchantState.RECOVERING, "Recovering cargo at former Merchant's Post");
    }

    public boolean postDestroyed() {
        return postDestroyed;
    }

    @Nullable
    public UUID targetUuid() {
        return targetUuid;
    }

    public void target(UUID uuid, int index, String fingerprint) {
        targetUuid = uuid;
        offerIndex = index;
        offerFingerprint = fingerprint;
    }

    public void clearTarget() {
        targetUuid = null;
        offerIndex = -1;
        offerFingerprint = "";
        plannedExecutions = 0;
        completedExecutions = 0;
        observedDistance = 0.0;
        noConvergenceTicks = 0;
        pendingTrades.clear();
    }

    public int offerIndex() {
        return offerIndex;
    }

    public String offerFingerprint() {
        return offerFingerprint;
    }

    public int plannedExecutions() {
        return plannedExecutions;
    }

    public void plannedExecutions(int planned) {
        plannedExecutions = planned;
        completedExecutions = 0;
    }

    public int completedExecutions() {
        return completedExecutions;
    }

    public void completeExecution() {
        completedExecutions++;
    }

    public void setWorkOrder(MerchantWorkOrder order) {
        if (order.trades().isEmpty()) {
            throw new IllegalArgumentException("A Merchant work order must contain at least one trade");
        }
        targetUuid = order.targetUuid();
        pendingTrades.clear();
        MerchantTradePlan first = order.trades().getFirst();
        activate(first.fingerprint(), first.offerIndex(), first.executions());
        for (int index = 1; index < order.trades().size(); index++) {
            MerchantTradePlan trade = order.trades().get(index);
            pendingTrades.add(new PendingTrade(
                trade.fingerprint(), trade.offerIndex(), trade.executions()
            ));
        }
    }

    public boolean advanceTradePlan() {
        if (pendingTrades.isEmpty()) {
            return false;
        }
        PendingTrade next = pendingTrades.removeFirst();
        activate(next.fingerprint(), next.offerIndex(), next.executions());
        return true;
    }

    public int pendingTradeCount() {
        return pendingTrades.size();
    }

    private void activate(String fingerprint, int index, int executions) {
        offerFingerprint = fingerprint;
        offerIndex = index;
        plannedExecutions = executions;
        completedExecutions = 0;
    }

    public int pathRetries() {
        return pathRetries;
    }

    public void pathRetried() {
        pathRetries++;
    }

    public void pathSucceeded() {
        pathRetries = 0;
    }

    public void observePathDistance(double distance) {
        if (pathObservationDistance < 0.0
            || pathObservationDistance - distance >= MerchantVillagerConfig.PATH_MIN_PROGRESS) {
            pathObservationDistance = distance;
            pathStallObservations = 0;
        } else {
            // Compare against the best distance, not merely the previous
            // sample. A villager oscillating between two nodes must not count
            // each return toward the target as fresh route progress.
            pathStallObservations++;
        }
    }

    public boolean pathStalled() {
        return pathStallObservations >= MerchantVillagerConfig.PATH_STALL_OBSERVATIONS;
    }

    public boolean directApproachActive() {
        return directApproachActive;
    }

    public void beginDirectApproach() {
        directApproachActive = true;
    }

    public long reservationExpiry() {
        return reservationExpiry;
    }

    public void reservationExpiry(long expiry) {
        reservationExpiry = expiry;
    }

    @Nullable
    public BlockPos outputChest() {
        return outputChest;
    }

    public void outputChest(@Nullable BlockPos outputChest) {
        this.outputChest = outputChest == null ? null : outputChest.toImmutable();
    }

    public double observedDistance() {
        return observedDistance;
    }

    public void observedDistance(double observedDistance) {
        this.observedDistance = observedDistance;
    }

    public int noConvergenceTicks() {
        return noConvergenceTicks;
    }

    public int storedExperience() {
        return storedExperience;
    }

    public void addStoredExperience(int amount) {
        if (amount <= 0) {
            return;
        }
        storedExperience = (int)Math.min(
            Integer.MAX_VALUE,
            (long)storedExperience + amount
        );
    }

    public int drainStoredExperience() {
        int drained = storedExperience;
        storedExperience = 0;
        return drained;
    }

    public void beginTradingInteraction(int durationTicks) {
        interactionDurationTicks = Math.max(1, durationTicks);
        interactionElapsedTicks = 0;
    }

    public int interactionDurationTicks() {
        return interactionDurationTicks;
    }

    public int interactionElapsedTicks() {
        return interactionElapsedTicks;
    }

    public boolean advanceTradingInteraction() {
        if (interactionDurationTicks <= 0) {
            return true;
        }
        interactionElapsedTicks++;
        return interactionElapsedTicks >= interactionDurationTicks;
    }

    public void resetConvergence() {
        noConvergenceTicks = 0;
    }

    public void missedConvergence() {
        noConvergenceTicks++;
    }

    public DefaultedList<ItemStack> cargo() {
        return cargo;
    }

    public boolean isRewardSlot(int slot) {
        return rewardSlots.get(slot);
    }

    public boolean hasCargo() {
        return cargo.stream().anyMatch(stack -> !stack.isEmpty());
    }

    public boolean hasRewards() {
        for (int slot = rewardSlots.nextSetBit(0); slot >= 0; slot = rewardSlots.nextSetBit(slot + 1)) {
            if (!cargo.get(slot).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public boolean hasInputs() {
        for (int slot = 0; slot < cargo.size(); slot++) {
            if (!rewardSlots.get(slot) && !cargo.get(slot).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public boolean canAdd(ItemStack stack, boolean reward) {
        DefaultedList<ItemStack> copy = copyCargo();
        BitSet flags = (BitSet)rewardSlots.clone();
        return insert(copy, flags, stack.copy(), reward);
    }

    public boolean add(ItemStack stack, boolean reward) {
        return insert(cargo, rewardSlots, stack, reward);
    }

    public boolean canLoadInputs(Iterable<ItemStack> inputs) {
        if (hasCargo()) {
            return false;
        }
        DefaultedList<ItemStack> working =
            DefaultedList.ofSize(MerchantVillagerConfig.CARGO_SIZE, ItemStack.EMPTY);
        BitSet flags = new BitSet(MerchantVillagerConfig.CARGO_SIZE);
        for (ItemStack input : inputs) {
            if (!insert(working, flags, input.copy(), false)) {
                return false;
            }
        }
        return true;
    }

    public boolean loadInputs(Iterable<ItemStack> inputs) {
        if (hasCargo()) {
            return false;
        }
        DefaultedList<ItemStack> working =
            DefaultedList.ofSize(MerchantVillagerConfig.CARGO_SIZE, ItemStack.EMPTY);
        BitSet flags = new BitSet(MerchantVillagerConfig.CARGO_SIZE);
        for (ItemStack input : inputs) {
            if (!insert(working, flags, input.copy(), false)) {
                return false;
            }
        }
        replaceCargo(working, flags);
        return true;
    }

    public DefaultedList<ItemStack> copyCargo() {
        DefaultedList<ItemStack> copy = DefaultedList.ofSize(cargo.size(), ItemStack.EMPTY);
        for (int i = 0; i < cargo.size(); i++) {
            copy.set(i, cargo.get(i).copy());
        }
        return copy;
    }

    public void replaceCargo(DefaultedList<ItemStack> replacement, BitSet rewardFlags) {
        cargo = replacement;
        rewardSlots.clear();
        rewardSlots.or(rewardFlags);
        if (hasCargo()) {
            // A previous job may have ended by dropping cargo after death or
            // post destruction. New physical cargo must be eligible for its
            // own one-time recovery drop.
            cargoDropped = false;
        }
    }

    public BitSet rewardFlagsCopy() {
        return (BitSet)rewardSlots.clone();
    }

    /**
     * Atomically transforms exact input cargo into one reward stack. The live
     * offer is revalidated separately immediately before this call.
     */
    public boolean executeCargoTrade(
        TradedItem first,
        int firstCount,
        Optional<TradedItem> second,
        int secondCount,
        ItemStack output
    ) {
        DefaultedList<ItemStack> working = copyCargo();
        BitSet flags = rewardFlagsCopy();
        if (!consume(working, flags, first, firstCount)
            || (second.isPresent() && !consume(working, flags, second.get(), secondCount))
            || !insert(working, flags, output.copy(), true)) {
            return false;
        }
        replaceCargo(working, flags);
        return true;
    }

    private static boolean consume(
        DefaultedList<ItemStack> inventory, BitSet flags, TradedItem required, int amount
    ) {
        int remaining = amount;
        for (int slot = 0; slot < inventory.size() && remaining > 0; slot++) {
            ItemStack stack = inventory.get(slot);
            if (!flags.get(slot) && required.matches(stack)) {
                int moved = Math.min(remaining, stack.getCount());
                stack.decrement(moved);
                remaining -= moved;
                if (stack.isEmpty()) {
                    inventory.set(slot, ItemStack.EMPTY);
                    flags.clear(slot);
                }
            }
        }
        return remaining == 0;
    }

    private static boolean insert(
        DefaultedList<ItemStack> inventory, BitSet flags, ItemStack incoming, boolean reward
    ) {
        for (int i = 0; i < inventory.size() && !incoming.isEmpty(); i++) {
            ItemStack present = inventory.get(i);
            if (!present.isEmpty()
                && flags.get(i) == reward
                && ItemStack.areItemsAndComponentsEqual(present, incoming)) {
                int moved = Math.min(incoming.getCount(), present.getMaxCount() - present.getCount());
                present.increment(moved);
                incoming.decrement(moved);
            }
        }
        for (int i = 0; i < inventory.size() && !incoming.isEmpty(); i++) {
            if (inventory.get(i).isEmpty()) {
                int moved = Math.min(incoming.getCount(), incoming.getMaxCount());
                inventory.set(i, incoming.copyWithCount(moved));
                flags.set(i, reward);
                incoming.decrement(moved);
            }
        }
        return incoming.isEmpty();
    }

    public void clearSlot(int slot) {
        cargo.set(slot, ItemStack.EMPTY);
        rewardSlots.clear(slot);
    }

    public ItemStack takeCargo(int slot, int amount) {
        if (slot < 0 || slot >= cargo.size() || amount <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack removed = cargo.get(slot).split(amount);
        if (cargo.get(slot).isEmpty()) {
            clearSlot(slot);
        }
        return removed;
    }

    public void write(WriteView root) {
        WriteView view = root.get("MerchantVillager");
        Inventories.writeData(view, cargo);
        view.putIntArray("RewardSlots", rewardSlots.stream().toArray());
        view.putString("State", state.name());
        view.putNullable("PostPos", BlockPos.CODEC, postPos);
        view.putString("PostDimension", postDimension);
        view.putNullable("Target", Uuids.CODEC, targetUuid);
        view.putString("OfferFingerprint", offerFingerprint);
        view.putInt("OfferIndex", offerIndex);
        view.putNullable("OutputChest", BlockPos.CODEC, outputChest);
        view.putInt("Planned", plannedExecutions);
        view.putInt("Completed", completedExecutions);
        view.putInt("StateTicks", stateTicks);
        view.putInt("PathRetries", pathRetries);
        view.putLong("ReservationExpiry", reservationExpiry);
        view.putString("Status", status);
        view.putString("LastFailure", lastFailure);
        view.putBoolean("CargoDropped", cargoDropped);
        view.putBoolean("PostDestroyed", postDestroyed);
        view.putDouble("ObservedDistance", observedDistance);
        view.putInt("NoConvergenceTicks", noConvergenceTicks);
        view.putInt("StoredExperience", storedExperience);
        WriteView.ListAppender<PendingTrade> pending =
            view.getListAppender("PendingTrades", PendingTrade.CODEC);
        pendingTrades.forEach(pending::add);
    }

    public void read(ReadView root) {
        ReadView view = root.getReadView("MerchantVillager");
        cargo = DefaultedList.ofSize(MerchantVillagerConfig.CARGO_SIZE, ItemStack.EMPTY);
        Inventories.readData(view, cargo);
        rewardSlots.clear();
        view.getOptionalIntArray("RewardSlots").ifPresent(values -> {
            for (int slot : values) {
                if (slot >= 0 && slot < cargo.size()) {
                    rewardSlots.set(slot);
                }
            }
        });
        try {
            state = MerchantState.valueOf(view.getString("State", MerchantState.IDLE.name()));
        } catch (IllegalArgumentException invalid) {
            state = MerchantState.RECOVERING;
        }
        postPos = view.read("PostPos", BlockPos.CODEC).orElse(null);
        postDimension = view.getString("PostDimension", "");
        targetUuid = view.read("Target", Uuids.CODEC).orElse(null);
        offerFingerprint = view.getString("OfferFingerprint", "");
        offerIndex = view.getInt("OfferIndex", -1);
        outputChest = view.read("OutputChest", BlockPos.CODEC).orElse(null);
        plannedExecutions = view.getInt("Planned", 0);
        completedExecutions = view.getInt("Completed", 0);
        stateTicks = view.getInt("StateTicks", 0);
        pathRetries = view.getInt("PathRetries", 0);
        reservationExpiry = view.getLong("ReservationExpiry", 0L);
        status = view.getString("Status", "Recovering saved work order");
        lastFailure = view.getString("LastFailure", "");
        cargoDropped = view.getBoolean("CargoDropped", false);
        postDestroyed = view.getBoolean("PostDestroyed", false);
        observedDistance = view.getDouble("ObservedDistance", 0.0);
        noConvergenceTicks = view.getInt("NoConvergenceTicks", 0);
        storedExperience = Math.max(0, view.getInt("StoredExperience", 0));
        interactionDurationTicks = 0;
        interactionElapsedTicks = 0;
        pendingTrades.clear();
        for (PendingTrade pending : view.getTypedListView("PendingTrades", PendingTrade.CODEC)) {
            if (pending.offerIndex() >= 0
                && pending.executions() > 0
                && pending.fingerprint().length() == 64) {
                pendingTrades.add(pending);
            }
        }

        /*
         * An in-flight TradeOffer call is atomic within one server tick, but
         * entity state and the target merchant are serialized independently.
         * After any unload/reload boundary, conservatively recover the stacks
         * already present in cargo instead of blindly issuing another offer
         * use. This may cancel a trip, but cannot duplicate or erase its items.
         */
        if (hasCargo()) {
            cargoDropped = false;
            state = MerchantState.RECOVERING;
            status = "Recovering saved work order";
            reservationExpiry = 0L;
        } else if (state != MerchantState.IDLE && state != MerchantState.PAUSED) {
            clearTarget();
            outputChest = null;
            reservationExpiry = 0L;
            state = MerchantState.IDLE;
            status = "Merchant idle";
        }
    }

    public void dropCargoOnce(VillagerEntity villager) {
        if (cargoDropped || !hasCargo()) {
            return;
        }
        cargoDropped = true;
        for (int i = 0; i < cargo.size(); i++) {
            ItemStack stack = cargo.get(i);
            if (!stack.isEmpty()) {
                villager.dropStack((ServerWorld)villager.getEntityWorld(), stack.copy());
                clearSlot(i);
            }
        }
    }

    private record PendingTrade(String fingerprint, int offerIndex, int executions) {
        private static final Codec<PendingTrade> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("fingerprint").forGetter(PendingTrade::fingerprint),
            Codec.INT.fieldOf("offer_index").forGetter(PendingTrade::offerIndex),
            Codec.INT.fieldOf("executions").forGetter(PendingTrade::executions)
        ).apply(instance, PendingTrade::new));
    }
}
