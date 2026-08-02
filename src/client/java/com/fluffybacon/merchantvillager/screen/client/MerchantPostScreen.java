package com.fluffybacon.merchantvillager.screen.client;

import com.fluffybacon.merchantvillager.client.ClientCatalogueCache;
import com.fluffybacon.merchantvillager.network.CataloguePayload;
import com.fluffybacon.merchantvillager.network.DepositTradeMaterialPayload;
import com.fluffybacon.merchantvillager.network.DisableAllOffersPayload;
import com.fluffybacon.merchantvillager.network.RefreshCataloguePayload;
import com.fluffybacon.merchantvillager.network.ToggleOfferPayload;
import com.fluffybacon.merchantvillager.screen.MerchantPostScreenHandler;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public final class MerchantPostScreen extends HandledScreen<MerchantPostScreenHandler> {
    private static final int SCREEN_WIDTH = 414;
    private static final int SCREEN_HEIGHT = 240;
    private static final int TRADE_LEFT = 6;
    private static final int TRADE_TOP = 35;
    private static final int TRADE_WIDTH = 228;
    private static final int TRADE_ROW_HEIGHT = 19;
    private static final int ROWS_PER_PAGE = 7;
    private static final int INPUT_X = 78;
    private static final int SECOND_INPUT_X = 103;
    private static final int OUTPUT_X = 137;
    private static final int TOGGLE_X = 207;
    private static final int CARGO_X = 244;
    private static final int CARGO_Y = 91;
    private int page;
    private int filterIndex;
    private SortMode sortMode = SortMode.READY;
    private TextFieldWidget search;
    private ButtonWidget filterButton;
    private ButtonWidget sortButton;

    public MerchantPostScreen(
        MerchantPostScreenHandler handler, PlayerInventory inventory, Text title
    ) {
        super(handler, inventory, title);
        backgroundWidth = SCREEN_WIDTH;
        backgroundHeight = SCREEN_HEIGHT;
        playerInventoryTitleX = MerchantPostScreenHandler.STORAGE_X;
        playerInventoryTitleY = 147;
        titleX = 8;
        titleY = 6;
    }

    @Override
    protected void init() {
        super.init();
        search = new TextFieldWidget(
            textRenderer,
            x + 96,
            y + 3,
            70,
            18,
            Text.translatable("merchant_villager.search")
        );
        search.setMaxLength(64);
        search.setPlaceholder(Text.translatable("merchant_villager.search"));
        search.setChangedListener(ignored -> page = 0);
        addDrawableChild(search);

        filterButton = addDrawableChild(ButtonWidget.builder(Text.literal("All"), button -> {
            CataloguePayload payload = catalogue();
            int count = payload == null ? 1 : filterChoices(payload).size();
            filterIndex = Math.floorMod(filterIndex + 1, count);
            page = 0;
            updateControlLabels(payload);
        }).dimensions(x + 168, y + 3, 42, 18)
            .tooltip(Tooltip.of(Text.translatable("merchant_villager.tooltip.filter")))
            .build());
        sortButton = addDrawableChild(ButtonWidget.builder(Text.literal(sortMode.label), button -> {
            sortMode = sortMode.next();
            page = 0;
            updateControlLabels(catalogue());
        }).dimensions(x + 212, y + 3, 42, 18)
            .tooltip(Tooltip.of(Text.translatable("merchant_villager.tooltip.sort")))
            .build());
        addDrawableChild(ButtonWidget.builder(Text.literal("X All"), button -> {
            CataloguePayload payload = catalogue();
            if (payload != null) {
                ClientPlayNetworking.send(new DisableAllOffersPayload(payload.postPos()));
            }
        }).dimensions(x + 256, y + 3, 62, 18)
            .tooltip(Tooltip.of(Text.translatable("merchant_villager.tooltip.disable_all")))
            .build());
        addDrawableChild(ButtonWidget.builder(Text.literal("\u21bb"), button -> {
            CataloguePayload payload = catalogue();
            if (payload != null) {
                ClientPlayNetworking.send(new RefreshCataloguePayload(payload.postPos()));
            }
        }).dimensions(x + 320, y + 3, 36, 18)
            .tooltip(Tooltip.of(Text.translatable("merchant_villager.tooltip.refresh")))
            .build());
        addDrawableChild(ButtonWidget.builder(Text.literal("<"), button -> page = Math.max(0, page - 1))
            .dimensions(x + 184, y + 171, 20, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(">"), button -> {
            CataloguePayload payload = catalogue();
            int pages = payload == null
                ? 1
                : Math.max(1, (visibleEntries(payload).size() + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
            page = Math.min(pages - 1, page + 1);
        }).dimensions(x + 208, y + 171, 20, 18).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        super.render(context, mouseX, mouseY, deltaTicks);
        renderOfferTooltips(context, mouseX, mouseY);
        renderCargoTooltip(context, mouseX, mouseY);
        renderStatusTooltip(context, mouseX, mouseY);
        drawMouseoverTooltip(context, mouseX, mouseY);
    }

    @Override
    protected void drawBackground(DrawContext context, float deltaTicks, int mouseX, int mouseY) {
        drawWoodBackground(context);
        drawCompactPanels(context);
        CataloguePayload payload = catalogue();
        if (payload == null) {
            context.drawText(
                textRenderer,
                Text.translatable("merchant_villager.loading_catalogue"),
                x + 8,
                y + TRADE_TOP,
                0x403020,
                false
            );
            return;
        }
        updateControlLabels(payload);
        context.drawText(
            textRenderer,
            Text.translatable("merchant_villager.trades"),
            x + 8,
            y + 24,
            0x403020,
            false
        );
        List<CataloguePayload.Entry> entries = visibleEntries(payload);
        int pages = Math.max(1, (entries.size() + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
        page = Math.min(page, pages - 1);
        int start = page * ROWS_PER_PAGE;
        for (int row = 0; row < ROWS_PER_PAGE && start + row < entries.size(); row++) {
            drawOfferRow(context, payload, entries.get(start + row), row, mouseX, mouseY);
        }
        context.drawText(
            textRenderer,
            Text.literal((page + 1) + "/" + pages + "  " + entries.size() + "/" + payload.entries().size()),
            x + 8,
            y + 177,
            0x403020,
            false
        );
        context.drawText(
            textRenderer,
            shortText(payload.workerState() + " \u2014 " + payload.status(), 31),
            x + 8,
            y + 194,
            0x403020,
            false
        );
        context.drawText(
            textRenderer,
            Text.literal(
                payload.targetCount() + " targets  "
                    + payload.enabledCount() + " enabled  "
                    + payload.executableCount() + " ready"
            ),
            x + 8,
            y + 206,
            0x403020,
            false
        );
        drawTelemetryPanel(context, payload);
    }

    private void drawCompactPanels(DrawContext context) {
        drawPanel(context, x + 5, y + 22, x + 235, y + 171, 0xE8D9B67A);
        drawPanel(context, x + 239, y + 22, x + 411, y + 89, 0xE06A4729);
        drawPanel(context, x + 239, y + 90, x + 301, y + 144, 0xE06A4729);
        drawPanel(context, x + 302, y + 90, x + 411, y + 144, 0xE8D9B67A);
        drawPanel(context, x + 239, y + 145, x + 411, y + 236, 0xE06A4729);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                drawSlotFrame(
                    context,
                    x + MerchantPostScreenHandler.STORAGE_X + column * 18,
                    y + MerchantPostScreenHandler.STORAGE_Y + row * 18
                );
                drawSlotFrame(
                    context,
                    x + MerchantPostScreenHandler.STORAGE_X + column * 18,
                    y + MerchantPostScreenHandler.PLAYER_INVENTORY_Y + row * 18
                );
            }
        }
        for (int column = 0; column < 9; column++) {
            drawSlotFrame(
                context,
                x + MerchantPostScreenHandler.STORAGE_X + column * 18,
                y + MerchantPostScreenHandler.HOTBAR_Y
            );
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                drawSlotFrame(context, x + CARGO_X + column * 18, y + CARGO_Y + row * 18);
            }
        }
        context.drawText(
            textRenderer,
            Text.translatable("merchant_villager.backpack"),
            x + MerchantPostScreenHandler.STORAGE_X,
            y + 24,
            0xFFF2D0,
            true
        );
        context.drawText(
            textRenderer,
            Text.translatable("merchant_villager.in_transit"),
            x + 305,
            y + 94,
            0xFFF2D0,
            true
        );
    }

    private void drawWoodBackground(DrawContext context) {
        context.fill(x, y, x + backgroundWidth, y + backgroundHeight, 0xFF24170F);
        context.fill(x + 2, y + 2, x + backgroundWidth - 2, y + backgroundHeight - 2, 0xFF6A4729);
        context.fill(x + 4, y + 4, x + backgroundWidth - 4, y + 6, 0xFF9A7044);
        context.fill(x + 4, y + backgroundHeight - 6, x + backgroundWidth - 4, y + backgroundHeight - 4, 0xFF392417);
        for (int plankY = 14; plankY < backgroundHeight - 6; plankY += 16) {
            context.fill(x + 3, y + plankY, x + backgroundWidth - 3, y + plankY + 1, 0x55301D12);
        }
    }

    private static void drawPanel(
        DrawContext context, int left, int top, int right, int bottom, int fill
    ) {
        context.fill(left, top, right, bottom, 0xFF3A281A);
        context.fill(left + 2, top + 2, right - 2, bottom - 2, fill);
        context.fill(left + 2, top + 2, right - 2, top + 3, 0x66FFF1CA);
    }

    private void drawTelemetryPanel(DrawContext context, CataloguePayload payload) {
        int left = x + 305;
        int color = 0x403020;
        if (payload.workerStats().isEmpty()) {
            context.drawText(
                textRenderer,
                Text.translatable("merchant_villager.no_merchant"),
                left,
                y + 105,
                color,
                false
            );
            context.drawText(
                textRenderer,
                Text.literal(payload.targetCount() + " targets"),
                left,
                y + 117,
                color,
                false
            );
            return;
        }
        CataloguePayload.WorkerStats stats = payload.workerStats().get();
        context.drawText(textRenderer, shortText(stats.name(), 17), left, y + 105, color, false);
        context.drawText(
            textRenderer,
            Text.literal("HP " + formatOne(stats.health()) + "/" + formatOne(stats.maxHealth())),
            left,
            y + 116,
            color,
            false
        );
        context.drawText(
            textRenderer,
            Text.literal("Range " + formatDistance(stats.distanceSquared()) + "m"),
            left,
            y + 127,
            color,
            false
        );
        context.drawText(
            textRenderer,
            Text.literal("Trip " + stats.completedExecutions() + "/" + stats.plannedExecutions()),
            left,
            y + 138,
            color,
            false
        );
        drawCargo(context, stats.cargo());
        if (!payload.lastFailure().isBlank()) {
            context.drawText(
                textRenderer,
                shortText("! " + payload.lastFailure(), 35),
                x + 8,
                y + 219,
                0x8A2020,
                false
            );
        }
    }

    private void drawCargo(DrawContext context, List<ItemStack> cargo) {
        for (int index = 0; index < 9; index++) {
            ItemStack stack = index < cargo.size() ? cargo.get(index) : ItemStack.EMPTY;
            if (stack.isEmpty()) {
                continue;
            }
            int itemX = x + CARGO_X + index % 3 * 18;
            int itemY = y + CARGO_Y + index / 3 * 18;
            context.drawItem(stack, itemX, itemY);
            context.drawStackOverlay(textRenderer, stack, itemX, itemY);
        }
    }

    private static void drawSlotFrame(DrawContext context, int slotX, int slotY) {
        context.fill(slotX - 1, slotY - 1, slotX + 17, slotY + 17, 0xFF3A281A);
        context.fill(slotX, slotY, slotX + 16, slotY + 16, 0xFFC2A675);
        context.fill(slotX, slotY, slotX + 16, slotY + 1, 0xFFE2C998);
        context.fill(slotX, slotY, slotX + 1, slotY + 16, 0xFFE2C998);
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        context.drawText(textRenderer, title, titleX, titleY, 0x403020, false);
        context.drawText(
            textRenderer,
            playerInventoryTitle,
            playerInventoryTitleX,
            playerInventoryTitleY,
            0xFFF2D0,
            true
        );
    }

    private void drawOfferRow(
        DrawContext context,
        CataloguePayload payload,
        CataloguePayload.Entry entry,
        int row,
        int mouseX,
        int mouseY
    ) {
        int rowY = y + TRADE_TOP + row * TRADE_ROW_HEIGHT;
        boolean hovered = inside(
            mouseX,
            mouseY,
            x + TRADE_LEFT,
            rowY - 2,
            TRADE_WIDTH,
            TRADE_ROW_HEIGHT
        );
        int background = entry.selected()
            ? 0xCCE0C06A
            : hovered ? 0xB09E7952 : (row % 2 == 0 ? 0x98916B45 : 0x987A583A);
        context.fill(
            x + TRADE_LEFT,
            rowY - 2,
            x + TRADE_LEFT + TRADE_WIDTH,
            rowY + 17,
            background
        );
        String target = shortText(targetDisplayLabel(payload, entry), 10);
        context.drawText(textRenderer, target, x + 9, rowY + 3, 0xFFF2D0, true);
        int itemX = x + INPUT_X;
        ItemStack first = entry.offer().firstInput().itemStack()
            .copyWithCount(entry.effectiveFirstCount());
        drawGhostInput(context, first, entry.offer().firstInput().matches(handler.getCursorStack()), itemX, rowY);
        if (entry.offer().secondInput().isPresent()) {
            context.drawText(textRenderer, "+", itemX + 17, rowY + 4, 0xFFF2D0, true);
            ItemStack second = entry.offer().secondInput().get().itemStack()
                .copyWithCount(entry.effectiveSecondCount());
            drawGhostInput(
                context,
                second,
                entry.offer().secondInput().get().matches(handler.getCursorStack()),
                x + SECOND_INPUT_X,
                rowY
            );
        }
        context.drawText(textRenderer, "\u2192", x + 124, rowY + 4, 0xFFF2D0, true);
        context.drawItem(entry.offer().output(), x + OUTPUT_X, rowY - 1);
        context.drawStackOverlay(textRenderer, entry.offer().output(), x + OUTPUT_X, rowY - 1);
        String availability = availability(entry);
        context.drawText(textRenderer, shortText(availability, 8), x + 157, rowY + 4, 0xFFF2D0, true);
        if (!entry.enabled()) {
            context.fill(
                x + TRADE_LEFT,
                rowY - 2,
                x + TRADE_LEFT + TRADE_WIDTH,
                rowY + 17,
                0x880F1112
            );
        }
        context.fill(
            x + TOGGLE_X,
            rowY - 1,
            x + TOGGLE_X + 24,
            rowY + 16,
            entry.enabled() ? 0xFF356A3A : 0xFF555555
        );
        context.drawCenteredTextWithShadow(
            textRenderer,
            entry.enabled() ? "ON" : "X",
            x + TOGGLE_X + 12,
            rowY + 4,
            0xFFFFFF
        );
    }

    private void drawGhostInput(
        DrawContext context, ItemStack stack, boolean cursorMatches, int itemX, int rowY
    ) {
        if (!handler.getCursorStack().isEmpty()) {
            int color = cursorMatches ? 0xFF5ABF63 : 0xFFB44242;
            context.fill(itemX - 1, rowY - 2, itemX + 17, rowY - 1, color);
            context.fill(itemX - 1, rowY + 15, itemX + 17, rowY + 16, color);
            context.fill(itemX - 1, rowY - 1, itemX, rowY + 15, color);
            context.fill(itemX + 16, rowY - 1, itemX + 17, rowY + 15, color);
        }
        context.drawItem(stack, itemX, rowY - 1);
        context.drawStackOverlay(textRenderer, stack, itemX, rowY - 1);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
        CataloguePayload payload = catalogue();
        if (payload != null) {
            List<CataloguePayload.Entry> entries = visibleEntries(payload);
            int start = page * ROWS_PER_PAGE;
            for (int row = 0; row < ROWS_PER_PAGE && start + row < entries.size(); row++) {
                int rowY = y + TRADE_TOP + row * TRADE_ROW_HEIGHT;
                CataloguePayload.Entry entry = entries.get(start + row);
                int itemX = x + INPUT_X;
                if (inside(mouseX, mouseY, itemX, rowY - 1, 16, 16)) {
                    sendDeposit(payload, entry, 0, click);
                    return true;
                }
                if (entry.offer().secondInput().isPresent()
                    && inside(mouseX, mouseY, x + SECOND_INPUT_X, rowY - 1, 16, 16)) {
                    sendDeposit(payload, entry, 1, click);
                    return true;
                }
                if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT
                    && inside(
                        mouseX,
                        mouseY,
                        x + TRADE_LEFT,
                        rowY - 2,
                        TRADE_WIDTH,
                        TRADE_ROW_HEIGHT
                    )) {
                    ClientPlayNetworking.send(new ToggleOfferPayload(
                        payload.postPos(),
                        entry.offer().fingerprint(),
                        !entry.enabled()
                    ));
                    return true;
                }
            }
        }
        return super.mouseClicked(click, doubled);
    }

    private static void sendDeposit(
        CataloguePayload payload, CataloguePayload.Entry entry, int inputIndex, Click click
    ) {
        if (click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT
            && click.button() != GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            return;
        }
        int mode;
        if ((click.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0) {
            mode = DepositTradeMaterialPayload.PLAYER_ALL;
        } else if (click.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            mode = DepositTradeMaterialPayload.CURSOR_ONE;
        } else {
            mode = DepositTradeMaterialPayload.CURSOR_ALL;
        }
        ClientPlayNetworking.send(new DepositTradeMaterialPayload(
            payload.postPos(),
            entry.offer().fingerprint(),
            inputIndex,
            mode
        ));
    }

    @Override
    public boolean mouseScrolled(
        double mouseX, double mouseY, double horizontalAmount, double verticalAmount
    ) {
        CataloguePayload payload = catalogue();
        if (payload != null
            && inside(mouseX, mouseY, x + TRADE_LEFT, y + TRADE_TOP - 2, TRADE_WIDTH, 135)) {
            int pages = Math.max(1, (visibleEntries(payload).size() + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
            page = Math.max(0, Math.min(pages - 1, page + (verticalAmount < 0 ? 1 : -1)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private void renderOfferTooltips(DrawContext context, int mouseX, int mouseY) {
        CataloguePayload payload = catalogue();
        if (payload == null) {
            return;
        }
        List<CataloguePayload.Entry> entries = visibleEntries(payload);
        int start = page * ROWS_PER_PAGE;
        for (int row = 0; row < ROWS_PER_PAGE && start + row < entries.size(); row++) {
            CataloguePayload.Entry entry = entries.get(start + row);
            int rowY = y + TRADE_TOP + row * TRADE_ROW_HEIGHT;
            int itemX = x + INPUT_X;
            if (inside(mouseX, mouseY, itemX, rowY - 1, 16, 16)) {
                context.drawItemTooltip(textRenderer, entry.offer().firstInput().itemStack(), mouseX, mouseY);
            } else if (entry.offer().secondInput().isPresent()
                && inside(mouseX, mouseY, x + SECOND_INPUT_X, rowY - 1, 16, 16)) {
                context.drawItemTooltip(textRenderer, entry.offer().secondInput().get().itemStack(), mouseX, mouseY);
            } else if (inside(mouseX, mouseY, x + OUTPUT_X, rowY - 1, 16, 16)) {
                context.drawItemTooltip(textRenderer, entry.offer().output(), mouseX, mouseY);
            } else if (inside(mouseX, mouseY, x + TOGGLE_X, rowY - 1, 24, 17)) {
                context.drawTooltip(
                    textRenderer,
                    Text.translatable(entry.enabled()
                        ? "merchant_villager.tooltip.enabled"
                        : "merchant_villager.tooltip.disabled"),
                    mouseX,
                    mouseY
                );
            } else if (inside(
                mouseX,
                mouseY,
                x + TRADE_LEFT,
                rowY - 2,
                TRADE_WIDTH,
                TRADE_ROW_HEIGHT
            )) {
                List<Text> details = new ArrayList<>();
                details.add(Text.literal(entry.offer().targetName()));
                details.add(Text.literal("Distance: " + formatDistance(entry.offer().distanceSquared()) + " blocks"));
                details.add(Text.literal("Uses: " + entry.offer().uses() + "/" + entry.offer().maxUses()));
                details.add(Text.literal(
                    "Stored: " + entry.storedFirstCount()
                        + (entry.offer().secondInput().isPresent()
                            ? " + " + entry.storedSecondCount()
                            : "")
                ));
                details.add(Text.literal("Fundable: " + entry.fundableExecutions()));
                if (entry.selected()) {
                    details.add(Text.literal("Reserved in the current work order"));
                }
                if (entry.coolingDown()) {
                    details.add(Text.literal("Target is on an unreachable-path cooldown"));
                }
                if (entry.offer().wanderingTrader() && entry.offer().despawnDelay() >= 0) {
                    details.add(Text.literal(
                        "Despawns in: " + (entry.offer().despawnDelay() / 20) + "s"
                    ));
                }
                details.add(Text.translatable("merchant_villager.tooltip.toggle_row"));
                details.add(Text.translatable("merchant_villager.tooltip.ghost_deposit"));
                context.drawTooltip(
                    textRenderer,
                    details,
                    mouseX,
                    mouseY
                );
            }
        }
    }

    private void renderCargoTooltip(DrawContext context, int mouseX, int mouseY) {
        CataloguePayload payload = catalogue();
        if (payload == null || payload.workerStats().isEmpty()) {
            return;
        }
        List<ItemStack> cargo = payload.workerStats().get().cargo();
        for (int index = 0; index < Math.min(9, cargo.size()); index++) {
            ItemStack stack = cargo.get(index);
            int itemX = x + CARGO_X + index % 3 * 18;
            int itemY = y + CARGO_Y + index / 3 * 18;
            if (!stack.isEmpty() && inside(mouseX, mouseY, itemX, itemY, 16, 16)) {
                context.drawItemTooltip(textRenderer, stack, mouseX, mouseY);
                return;
            }
        }
    }

    private void renderStatusTooltip(DrawContext context, int mouseX, int mouseY) {
        CataloguePayload payload = catalogue();
        if (payload == null || !inside(mouseX, mouseY, x + 6, y + 191, 226, 40)) {
            return;
        }
        List<Text> lines = new ArrayList<>();
        lines.add(Text.literal(payload.status()));
        payload.workerStats().ifPresent(stats -> {
            lines.add(Text.literal(
                stats.name() + "  " + formatOne(stats.health()) + "/" + formatOne(stats.maxHealth()) + " health"
            ));
            lines.add(Text.literal("Distance from post: " + formatDistance(stats.distanceSquared()) + " blocks"));
            lines.add(Text.literal(
                "Work order: " + stats.completedExecutions() + "/" + stats.plannedExecutions()
            ));
            lines.add(Text.literal(
                "Target: " + stats.targetUuid()
                    .map(uuid -> targetName(payload, uuid))
                    .orElse("none")
            ));
            lines.add(Text.literal(
                "Output chest: " + stats.outputChest()
                    .map(pos -> pos.toShortString() + " \u2014 " + stats.outputChestStatus())
                    .orElse("not selected")
            ));
            String cargo = stats.cargo().stream()
                .filter(stack -> !stack.isEmpty())
                .map(stack -> stack.getCount() + " " + stack.getName().getString())
                .reduce((left, right) -> left + ", " + right)
                .orElse("empty");
            lines.add(Text.literal("Cargo: " + cargo));
        });
        if (!payload.lastFailure().isBlank()) {
            lines.add(Text.literal("Last failure: " + payload.lastFailure()));
        }
        context.drawTooltip(textRenderer, lines, mouseX, mouseY);
    }

    private CataloguePayload catalogue() {
        CataloguePayload payload = ClientCatalogueCache.latest();
        return payload != null && payload.postPos().equals(handler.getPostPos()) ? payload : null;
    }

    private List<CataloguePayload.Entry> visibleEntries(CataloguePayload payload) {
        List<FilterChoice> filters = filterChoices(payload);
        filterIndex = Math.floorMod(filterIndex, filters.size());
        Predicate<CataloguePayload.Entry> filter = filters.get(filterIndex).predicate;
        String query = search == null ? "" : search.getText().strip().toLowerCase(Locale.ROOT);
        Comparator<CataloguePayload.Entry> comparator = sortMode.comparator();
        return payload.entries().stream()
            .filter(filter)
            .filter(entry -> matchesSearch(entry, query))
            .sorted(comparator.thenComparing(defaultComparator()))
            .toList();
    }

    private List<FilterChoice> filterChoices(CataloguePayload payload) {
        List<FilterChoice> choices = new ArrayList<>();
        choices.add(new FilterChoice("All", ignored -> true));
        choices.add(new FilterChoice("Enabled", CataloguePayload.Entry::enabled));
        choices.add(new FilterChoice("Disabled", entry -> !entry.enabled()));
        choices.add(new FilterChoice("Ready", MerchantPostScreen::isReady));
        choices.add(new FilterChoice("Missing", entry ->
            entry.enabled() && !entry.offer().isOutOfStock() && entry.fundableExecutions() <= 0));
        choices.add(new FilterChoice("Out", entry -> entry.offer().isOutOfStock()));
        choices.add(new FilterChoice("Wander", entry -> entry.offer().wanderingTrader()));
        payload.entries().stream()
            .map(entry -> entry.offer().profession())
            .distinct()
            .sorted()
            .forEach(profession -> choices.add(new FilterChoice(
                shortText(displayProfession(profession), 7),
                entry -> entry.offer().profession().equals(profession)
            )));
        return choices;
    }

    private void updateControlLabels(CataloguePayload payload) {
        if (filterButton != null && payload != null) {
            List<FilterChoice> filters = filterChoices(payload);
            filterIndex = Math.floorMod(filterIndex, filters.size());
            filterButton.setMessage(Text.literal(filters.get(filterIndex).label));
        }
        if (sortButton != null) {
            sortButton.setMessage(Text.literal(sortMode.label));
        }
    }

    private static Comparator<CataloguePayload.Entry> defaultComparator() {
        return Comparator
            .comparingInt(MerchantPostScreen::visualRank)
            .thenComparing(entry -> entry.offer().profession())
            .thenComparing(entry -> entry.offer().targetUuid())
            .thenComparingInt(entry -> entry.offer().offerIndex());
    }

    private static int visualRank(CataloguePayload.Entry entry) {
        if (isReady(entry) && entry.offer().wanderingTrader()) {
            return 0;
        }
        if (isReady(entry)) {
            return 1;
        }
        if (entry.enabled() && entry.fundableExecutions() <= 0 && !entry.offer().isOutOfStock()) {
            return 2;
        }
        if (!entry.enabled()) {
            return 3;
        }
        if (!entry.offer().targetAvailable()) {
            return 4;
        }
        return 5;
    }

    private static boolean isReady(CataloguePayload.Entry entry) {
        return entry.enabled()
            && entry.fundableExecutions() > 0
            && !entry.coolingDown()
            && !entry.offer().isOutOfStock()
            && entry.offer().targetAvailable();
    }

    private static boolean matchesSearch(CataloguePayload.Entry entry, String query) {
        if (query.isEmpty()) {
            return true;
        }
        return entry.offer().profession().toLowerCase(Locale.ROOT).contains(query)
            || entry.offer().targetName().toLowerCase(Locale.ROOT).contains(query)
            || entry.offer().firstInput().itemStack().getName().getString()
                .toLowerCase(Locale.ROOT).contains(query)
            || entry.offer().secondInput()
                .map(input -> input.itemStack().getName().getString().toLowerCase(Locale.ROOT).contains(query))
                .orElse(false)
            || entry.offer().output().getName().getString().toLowerCase(Locale.ROOT).contains(query);
    }

    private static String availability(CataloguePayload.Entry entry) {
        if (entry.offer().isOutOfStock()) {
            return "Out";
        }
        if (!entry.offer().targetAvailable()) {
            return "Busy";
        }
        if (entry.coolingDown()) {
            return "Unreachable";
        }
        if (!entry.enabled()) {
            return "Disabled";
        }
        return entry.fundableExecutions() > 0 ? "Ready " + entry.fundableExecutions() : "Missing";
    }

    private static String targetName(CataloguePayload payload, java.util.UUID targetUuid) {
        return payload.entries().stream()
            .filter(entry -> entry.offer().targetUuid().equals(targetUuid))
            .map(entry -> entry.offer().targetName())
            .findFirst()
            .orElse(targetUuid.toString().substring(0, 8));
    }

    private static String targetDisplayLabel(
        CataloguePayload payload, CataloguePayload.Entry selected
    ) {
        List<java.util.UUID> professionTargets = payload.entries().stream()
            .filter(entry -> entry.offer().profession().equals(selected.offer().profession()))
            .map(entry -> entry.offer().targetUuid())
            .distinct()
            .sorted()
            .toList();
        int ordinal = professionTargets.indexOf(selected.offer().targetUuid()) + 1;
        return displayProfession(selected.offer().profession()) + " #" + Math.max(1, ordinal);
    }

    private static String displayProfession(String profession) {
        int separator = profession.indexOf(':');
        String path = separator >= 0 ? profession.substring(separator + 1) : profession;
        String[] words = path.split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!result.isEmpty()) {
                result.append(' ');
            }
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
        }
        return result.toString();
    }

    private static boolean inside(double x, double y, int left, int top, int width, int height) {
        return x >= left && x < left + width && y >= top && y < top + height;
    }

    private static String shortText(String text, int max) {
        return text.length() <= max ? text : text.substring(0, Math.max(0, max - 1)) + "\u2026";
    }

    private static String formatDistance(double squared) {
        return String.format(Locale.ROOT, "%.1f", Math.sqrt(Math.max(0.0, squared)));
    }

    private static String formatOne(float value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private record FilterChoice(String label, Predicate<CataloguePayload.Entry> predicate) {
    }

    private enum SortMode {
        READY("Ready"),
        DISTANCE("Near"),
        PROFESSION("Job"),
        INPUT("Input"),
        OUTPUT("Output"),
        REMAINING("Uses");

        private final String label;

        SortMode(String label) {
            this.label = label;
        }

        private SortMode next() {
            SortMode[] values = values();
            return values[(ordinal() + 1) % values.length];
        }

        private Comparator<CataloguePayload.Entry> comparator() {
            return switch (this) {
                case READY -> Comparator.comparingInt(MerchantPostScreen::visualRank);
                case DISTANCE -> Comparator.comparingDouble(entry -> entry.offer().distanceSquared());
                case PROFESSION -> Comparator.comparing(entry -> entry.offer().profession());
                case INPUT -> Comparator.comparing(entry ->
                    entry.offer().firstInput().itemStack().getName().getString());
                case OUTPUT -> Comparator.comparing(entry -> entry.offer().output().getName().getString());
                case REMAINING -> Comparator.comparingInt(
                    (CataloguePayload.Entry entry) -> entry.offer().remainingUses()
                ).reversed();
            };
        }
    }
}
