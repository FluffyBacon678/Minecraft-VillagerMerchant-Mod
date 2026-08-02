package com.fluffybacon.merchantvillager.blockentity;

import com.fluffybacon.merchantvillager.config.MerchantVillagerConfig;
import com.fluffybacon.merchantvillager.merchant.MerchantWorker;
import com.fluffybacon.merchantvillager.network.ModPayloads;
import com.fluffybacon.merchantvillager.registry.ModBlockEntities;
import com.fluffybacon.merchantvillager.screen.MerchantPostScreenHandler;
import com.fluffybacon.merchantvillager.trade.MerchantScanner;
import com.fluffybacon.merchantvillager.trade.OfferPermission;
import com.fluffybacon.merchantvillager.trade.OfferSnapshot;
import com.fluffybacon.merchantvillager.trade.TradeInputMatcher;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.LockableContainerBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.village.TradedItem;
import org.jspecify.annotations.Nullable;

public final class MerchantPostBlockEntity extends LockableContainerBlockEntity
    implements SidedInventory, ExtendedScreenHandlerFactory<BlockPos> {
    public static final int INVENTORY_SIZE = 27;
    private static final int[] SLOTS = createSlots();
    private static final long PERMISSION_EXPIRY_TICKS = 20L * 60L * 60L * 24L * 7L;

    private DefaultedList<ItemStack> inventory = DefaultedList.ofSize(INVENTORY_SIZE, ItemStack.EMPTY);
    private final Map<String, OfferPermission> permissions = new HashMap<>();
    private final Map<String, Long> unreachableUntil = new HashMap<>();
    private List<OfferSnapshot> offers = List.of();
    @Nullable private UUID assignedMerchant;
    private int scanCooldown;
    private int viewerSyncCooldown;
    private int catalogueRevision;
    private int selectionCursor;
    private boolean paused;
    private String lastFailure = "";

    public MerchantPostBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MERCHANT_POST, pos, state);
    }

    public static void serverTick(ServerWorld world, BlockPos pos, BlockState state, MerchantPostBlockEntity post) {
        if (post.scanCooldown-- <= 0) {
            post.refreshCatalogue(false);
        }
        if (post.viewerSyncCooldown-- <= 0) {
            post.viewerSyncCooldown = 20;
            post.syncViewers();
        }
        post.validateAssignment();
    }

    public void refreshCatalogue(boolean forceSync) {
        if (!(world instanceof ServerWorld serverWorld)) {
            return;
        }
        scanCooldown = MerchantVillagerConfig.CATALOGUE_SCAN_INTERVAL;
        List<OfferSnapshot> scanned = MerchantScanner.scan(serverWorld, pos);
        long now = serverWorld.getTime();
        boolean changed = !sameCatalogue(offers, scanned);
        offers = scanned;
        for (OfferSnapshot offer : scanned) {
            permissions.compute(offer.fingerprint(), (key, old) ->
                new OfferPermission(
                    key,
                    offer.targetUuid(),
                    old != null && old.enabled(),
                    now
                )
            );
        }
        permissions.values().removeIf(permission ->
            now - permission.lastSeen() > PERMISSION_EXPIRY_TICKS
                && scanned.stream().noneMatch(offer -> offer.fingerprint().equals(permission.fingerprint()))
        );
        unreachableUntil.entrySet().removeIf(entry -> entry.getValue() <= now);
        if (changed || forceSync) {
            catalogueRevision++;
            markDirty();
            syncViewers();
        }
    }

    private static boolean sameCatalogue(List<OfferSnapshot> left, List<OfferSnapshot> right) {
        if (left.size() != right.size()) {
            return false;
        }
        for (int i = 0; i < left.size(); i++) {
            OfferSnapshot a = left.get(i);
            OfferSnapshot b = right.get(i);
            if (!a.fingerprint().equals(b.fingerprint())
                || a.uses() != b.uses()
                || a.targetAvailable() != b.targetAvailable()
                || Math.abs(a.distanceSquared() - b.distanceSquared()) > 1.0) {
                return false;
            }
        }
        return true;
    }

    private void syncViewers() {
        if (!(world instanceof ServerWorld serverWorld)) {
            return;
        }
        for (ServerPlayerEntity player : serverWorld.getPlayers()) {
            if (player.currentScreenHandler instanceof MerchantPostScreenHandler handler
                && handler.getPostPos().equals(pos)) {
                sendCatalogue(player);
            }
        }
    }

    public void sendCatalogue(ServerPlayerEntity player) {
        ModPayloads.sendCatalogue(player, this);
    }

    public boolean setOfferEnabled(ServerPlayerEntity player, String fingerprint, boolean enabled) {
        if (!isValidController(player)) {
            return false;
        }
        Optional<OfferSnapshot> current = offers.stream()
            .filter(offer -> offer.fingerprint().equals(fingerprint))
            .findFirst();
        if (current.isEmpty()) {
            return false;
        }
        OfferPermission old = permissions.get(fingerprint);
        permissions.put(fingerprint, new OfferPermission(
            fingerprint,
            current.get().targetUuid(),
            enabled,
            world == null ? 0L : world.getTime()
        ));
        catalogueRevision++;
        markDirty();
        syncViewers();
        return old == null || old.enabled() != enabled;
    }

    public int disableAllOffers(ServerPlayerEntity player) {
        if (!isValidController(player)) {
            return 0;
        }
        int changed = 0;
        for (Map.Entry<String, OfferPermission> entry : permissions.entrySet()) {
            OfferPermission permission = entry.getValue();
            if (permission.enabled()) {
                entry.setValue(new OfferPermission(
                    permission.fingerprint(),
                    permission.targetUuid(),
                    false,
                    permission.lastSeen()
                ));
                changed++;
            }
        }
        if (changed > 0) {
            notifyMaterialOrPermissionChange();
        }
        return changed;
    }

    /**
     * Moves server-owned stacks through a catalogue ghost input. The packet
     * supplies only identity and interaction mode; all stack matching, counts,
     * inventory capacity, and mutations are derived and enforced here.
     */
    public int depositForOffer(
        ServerPlayerEntity player, String fingerprint, int inputIndex, int mode
    ) {
        if (!isValidController(player)
            || inputIndex < 0
            || inputIndex > 1
            || mode < 0
            || mode > 2) {
            return 0;
        }
        Optional<OfferSnapshot> snapshot = offers.stream()
            .filter(offer -> offer.fingerprint().equals(fingerprint))
            .findFirst();
        if (snapshot.isEmpty()) {
            return 0;
        }
        TradedItem required;
        if (inputIndex == 0) {
            required = snapshot.get().firstInput();
        } else if (snapshot.get().secondInput().isPresent()) {
            required = snapshot.get().secondInput().get();
        } else {
            return 0;
        }

        int moved = 0;
        if (mode == 2) {
            DefaultedList<ItemStack> main = player.getInventory().getMainStacks();
            for (ItemStack stack : main) {
                moved += moveMatchingIntoPost(stack, stack.getCount(), required);
            }
            player.getInventory().markDirty();
        } else {
            ItemStack cursor = player.currentScreenHandler.getCursorStack();
            moved = moveMatchingIntoPost(cursor, mode == 1 ? 1 : cursor.getCount(), required);
            if (cursor.isEmpty()) {
                player.currentScreenHandler.setCursorStack(ItemStack.EMPTY);
            }
        }
        if (moved > 0) {
            notifyMaterialOrPermissionChange();
            player.currentScreenHandler.syncState();
        }
        return moved;
    }

    private int moveMatchingIntoPost(ItemStack source, int requested, TradedItem required) {
        if (source.isEmpty() || requested <= 0 || !required.matches(source)) {
            return 0;
        }
        int remaining = Math.min(requested, source.getCount());
        int initial = remaining;
        for (int slot = 0; slot < inventory.size() && remaining > 0; slot++) {
            ItemStack present = inventory.get(slot);
            if (!present.isEmpty() && ItemStack.areItemsAndComponentsEqual(present, source)) {
                int limit = Math.min(getMaxCount(present), present.getMaxCount());
                int amount = Math.min(remaining, Math.max(0, limit - present.getCount()));
                present.increment(amount);
                source.decrement(amount);
                remaining -= amount;
            }
        }
        for (int slot = 0; slot < inventory.size() && remaining > 0; slot++) {
            if (inventory.get(slot).isEmpty()) {
                int amount = Math.min(
                    remaining,
                    Math.min(getMaxCount(source), source.getMaxCount())
                );
                inventory.set(slot, source.copyWithCount(amount));
                source.decrement(amount);
                remaining -= amount;
            }
        }
        return initial - remaining;
    }

    private boolean isValidController(ServerPlayerEntity player) {
        return player.currentScreenHandler instanceof MerchantPostScreenHandler handler
            && handler.getPostPos().equals(pos)
            && canPlayerUse(player);
    }

    public void notifyMaterialOrPermissionChange() {
        catalogueRevision++;
        markDirty();
        syncViewers();
    }

    public boolean isEnabled(String fingerprint) {
        OfferPermission permission = permissions.get(fingerprint);
        return permission != null && permission.enabled();
    }

    public boolean isCoolingDown(String fingerprint) {
        long now = world == null ? 0L : world.getTime();
        Long until = unreachableUntil.get(fingerprint);
        if (until == null) {
            return false;
        }
        if (until <= now) {
            unreachableUntil.remove(fingerprint);
            return false;
        }
        return true;
    }

    public void markUnreachable(String fingerprint, long until) {
        if (!fingerprint.isEmpty()) {
            unreachableUntil.put(fingerprint, until);
            markDirty();
        }
    }

    /**
     * Server-owned approval hook used by deterministic GameTests and recovery
     * tooling. Network handlers never call this without their own validation.
     */
    public void setOfferEnabledInternal(String fingerprint, boolean enabled) {
        offers.stream().filter(offer -> offer.fingerprint().equals(fingerprint)).findFirst()
            .ifPresent(offer -> {
                permissions.put(fingerprint, new OfferPermission(
                    fingerprint,
                    offer.targetUuid(),
                    enabled,
                    world == null ? 0L : world.getTime()
                ));
                catalogueRevision++;
                markDirty();
            });
    }

    public List<OfferSnapshot> getOffers() {
        return offers;
    }

    public int getCatalogueRevision() {
        return catalogueRevision;
    }

    public int getScanCooldown() {
        return Math.max(0, scanCooldown);
    }

    public int getSelectionCursor() {
        return selectionCursor;
    }

    public void advanceSelectionCursor(int selectedIndex, int targetCount) {
        selectionCursor = targetCount <= 0 ? 0 : Math.floorMod(selectedIndex + 1, targetCount);
        markDirty();
    }

    public Optional<UUID> getAssignedMerchant() {
        return Optional.ofNullable(assignedMerchant);
    }

    public void assignMerchant(UUID merchantUuid) {
        if (assignedMerchant == null || assignedMerchant.equals(merchantUuid)) {
            assignedMerchant = merchantUuid;
            markDirty();
        }
    }

    public void clearMerchant(UUID merchantUuid) {
        if (merchantUuid.equals(assignedMerchant)) {
            assignedMerchant = null;
            markDirty();
        }
    }

    private void validateAssignment() {
        if (assignedMerchant == null || !(world instanceof ServerWorld serverWorld)) {
            return;
        }
        Entity entity = serverWorld.getEntity(assignedMerchant);
        if (entity == null) {
            for (ServerWorld candidateWorld : serverWorld.getServer().getWorlds()) {
                if (candidateWorld != serverWorld && candidateWorld.getEntity(assignedMerchant) != null) {
                    assignedMerchant = null;
                    markDirty();
                    return;
                }
            }
        }
        if (entity != null && (!(entity instanceof MerchantWorker worker)
            || worker.merchantVillager$getState().postPos() == null
            || !pos.equals(worker.merchantVillager$getState().postPos()))) {
            assignedMerchant = null;
            markDirty();
        }
    }

    public boolean isPaused() {
        return paused;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
        markDirty();
    }

    public String getLastFailure() {
        return lastFailure;
    }

    public void setLastFailure(String failure) {
        lastFailure = failure == null ? "" : failure;
        markDirty();
    }

    public int countEnabledOffers() {
        return (int)offers.stream().filter(offer -> isEnabled(offer.fingerprint())).count();
    }

    public void onPostDestroyed() {
        paused = true;
        if (world instanceof ServerWorld serverWorld && assignedMerchant != null) {
            Entity entity = serverWorld.getEntity(assignedMerchant);
            if (entity instanceof MerchantWorker worker) {
                worker.merchantVillager$getState().onPostDestroyed(pos);
            }
        }
        assignedMerchant = null;
        markDirty();
    }

    @Override
    protected Text getContainerName() {
        return Text.translatable("container.merchant_villager.merchant_post");
    }

    @Override
    protected DefaultedList<ItemStack> getHeldStacks() {
        return inventory;
    }

    @Override
    protected void setHeldStacks(DefaultedList<ItemStack> inventory) {
        this.inventory = inventory;
    }

    @Override
    protected ScreenHandler createScreenHandler(int syncId, PlayerInventory playerInventory) {
        return new MerchantPostScreenHandler(syncId, playerInventory, this);
    }

    @Override
    public BlockPos getScreenOpeningData(ServerPlayerEntity player) {
        return pos.toImmutable();
    }

    @Override
    public int size() {
        return INVENTORY_SIZE;
    }

    @Override
    public boolean isValid(int slot, ItemStack stack) {
        return TradeInputMatcher.isAccepted(stack, offers);
    }

    @Override
    public int[] getAvailableSlots(Direction side) {
        return SLOTS;
    }

    @Override
    public boolean canInsert(int slot, ItemStack stack, @Nullable Direction dir) {
        return dir != Direction.DOWN && isValid(slot, stack);
    }

    @Override
    public boolean canExtract(int slot, ItemStack stack, Direction dir) {
        return dir != Direction.UP;
    }

    @Override
    protected void readData(ReadView view) {
        super.readData(view);
        inventory = DefaultedList.ofSize(INVENTORY_SIZE, ItemStack.EMPTY);
        Inventories.readData(view, inventory);
        permissions.clear();
        for (OfferPermission permission : view.getTypedListView("OfferPermissions", OfferPermission.CODEC)) {
            permissions.put(permission.fingerprint(), permission);
        }
        unreachableUntil.clear();
        for (OfferCooldown cooldown : view.getTypedListView("UnreachableOffers", OfferCooldown.CODEC)) {
            unreachableUntil.put(cooldown.fingerprint(), cooldown.until());
        }
        assignedMerchant = view.read("AssignedMerchant", net.minecraft.util.Uuids.CODEC).orElse(null);
        paused = view.getBoolean("Paused", false);
        lastFailure = view.getString("LastFailure", "");
        catalogueRevision = view.getInt("CatalogueRevision", 0);
        selectionCursor = Math.max(0, view.getInt("SelectionCursor", 0));
    }

    @Override
    protected void writeData(WriteView view) {
        super.writeData(view);
        Inventories.writeData(view, inventory);
        WriteView.ListAppender<OfferPermission> list = view.getListAppender("OfferPermissions", OfferPermission.CODEC);
        permissions.values().stream()
            .sorted(java.util.Comparator.comparing(OfferPermission::fingerprint))
            .forEach(list::add);
        WriteView.ListAppender<OfferCooldown> cooldowns =
            view.getListAppender("UnreachableOffers", OfferCooldown.CODEC);
        unreachableUntil.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> new OfferCooldown(entry.getKey(), entry.getValue()))
            .forEach(cooldowns::add);
        view.putNullable("AssignedMerchant", net.minecraft.util.Uuids.CODEC, assignedMerchant);
        view.putBoolean("Paused", paused);
        view.putString("LastFailure", lastFailure);
        view.putInt("CatalogueRevision", catalogueRevision);
        view.putInt("SelectionCursor", selectionCursor);
    }

    private static int[] createSlots() {
        int[] slots = new int[INVENTORY_SIZE];
        for (int i = 0; i < slots.length; i++) {
            slots[i] = i;
        }
        return slots;
    }

    public List<ItemStack> copyInventory() {
        List<ItemStack> copy = new ArrayList<>(inventory.size());
        inventory.forEach(stack -> copy.add(stack.copy()));
        return copy;
    }

    /**
     * Internal recovery insertion deliberately bypasses the current catalogue
     * filter. Inputs already removed for a work order must remain recoverable
     * even when the target unloads or its offer changes.
     */
    public ItemStack insertReturnedInput(ItemStack incoming) {
        ItemStack remainder = incoming.copy();
        for (int slot = 0; slot < inventory.size() && !remainder.isEmpty(); slot++) {
            ItemStack present = inventory.get(slot);
            if (!present.isEmpty() && ItemStack.areItemsAndComponentsEqual(present, remainder)) {
                int moved = Math.min(remainder.getCount(), present.getMaxCount() - present.getCount());
                present.increment(moved);
                remainder.decrement(moved);
            }
        }
        for (int slot = 0; slot < inventory.size() && !remainder.isEmpty(); slot++) {
            if (inventory.get(slot).isEmpty()) {
                int moved = Math.min(remainder.getCount(), remainder.getMaxCount());
                inventory.set(slot, remainder.copyWithCount(moved));
                remainder.decrement(moved);
            }
        }
        if (remainder.getCount() != incoming.getCount()) {
            markDirty();
        }
        return remainder;
    }

    private record OfferCooldown(String fingerprint, long until) {
        private static final Codec<OfferCooldown> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("fingerprint").forGetter(OfferCooldown::fingerprint),
            Codec.LONG.fieldOf("until").forGetter(OfferCooldown::until)
        ).apply(instance, OfferCooldown::new));
    }
}
