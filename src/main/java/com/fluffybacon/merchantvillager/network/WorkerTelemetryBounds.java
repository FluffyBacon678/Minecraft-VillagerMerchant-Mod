package com.fluffybacon.merchantvillager.network;

import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.DynamicRegistryManager;

/** Keeps read-only worker telemetry useful without transporting arbitrary item data. */
public final class WorkerTelemetryBounds {
    public static final int MAX_CARGO_SLOTS = 9;
    public static final int MAX_ENCODED_CARGO_BYTES = 48 * 1024;

    public static CargoView summarizeCargo(
        DynamicRegistryManager registries, List<ItemStack> cargo
    ) {
        int size = Math.min(MAX_CARGO_SLOTS, cargo.size());
        List<ItemStack> summaries = new ArrayList<>(size);
        int[] summaryBytes = new int[size];
        int encodedBytes = 0;
        boolean summarized = cargo.size() > MAX_CARGO_SLOTS;

        // Reserve a small identity/count-only representation for every slot
        // before spending the remaining budget on arbitrary components.
        for (int slot = 0; slot < size; slot++) {
            ItemStack source = cargo.get(slot);
            ItemStack summary = source.isEmpty()
                ? ItemStack.EMPTY
                : new ItemStack(source.getItem(), source.getCount());
            int bytes = encodedStackSize(
                registries,
                summary,
                Math.max(1, MAX_ENCODED_CARGO_BYTES - encodedBytes)
            );
            if (bytes < 0 || encodedBytes + bytes > MAX_ENCODED_CARGO_BYTES) {
                summary = ItemStack.EMPTY;
                bytes = encodedStackSize(
                    registries,
                    summary,
                    Math.max(1, MAX_ENCODED_CARGO_BYTES - encodedBytes)
                );
                summarized = true;
            }
            if (bytes < 0 || encodedBytes + bytes > MAX_ENCODED_CARGO_BYTES) {
                // OPTIONAL_PACKET_CODEC's empty representation is normally one
                // byte. This final guard keeps a corrupt registry implementation
                // from escaping the declared budget.
                summaries.add(ItemStack.EMPTY);
                summaryBytes[slot] = 0;
                summarized = true;
                continue;
            }
            summaries.add(summary);
            summaryBytes[slot] = bytes;
            encodedBytes += bytes;
        }

        for (int slot = 0; slot < size; slot++) {
            ItemStack source = cargo.get(slot);
            if (source.isEmpty()) {
                continue;
            }
            int remaining = MAX_ENCODED_CARGO_BYTES - encodedBytes + summaryBytes[slot];
            int fullBytes = encodedStackSize(registries, source, Math.max(1, remaining));
            if (fullBytes >= 0 && encodedBytes - summaryBytes[slot] + fullBytes
                <= MAX_ENCODED_CARGO_BYTES) {
                summaries.set(slot, source.copy());
                encodedBytes = encodedBytes - summaryBytes[slot] + fullBytes;
                summaryBytes[slot] = fullBytes;
            } else {
                summarized = true;
            }
        }
        return new CargoView(List.copyOf(summaries), summarized, encodedBytes);
    }

    public static String clamp(String value, int maximumLength) {
        if (value == null || value.isEmpty() || maximumLength <= 0) {
            return "";
        }
        if (value.length() <= maximumLength) {
            return value;
        }
        int end = maximumLength;
        if (Character.isHighSurrogate(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(0, end);
    }

    private static int encodedStackSize(
        DynamicRegistryManager registries, ItemStack stack, int maximumBytes
    ) {
        if (maximumBytes <= 0) {
            return -1;
        }
        RegistryByteBuf scratch = new RegistryByteBuf(
            Unpooled.buffer(Math.min(256, maximumBytes), maximumBytes), registries
        );
        try {
            ItemStack.OPTIONAL_PACKET_CODEC.encode(scratch, stack);
            return scratch.readableBytes();
        } catch (RuntimeException tooLargeOrInvalid) {
            return -1;
        } finally {
            scratch.release();
        }
    }

    public record CargoView(List<ItemStack> cargo, boolean summarized, int encodedBytes) {
        public CargoView {
            cargo = cargo.stream().map(ItemStack::copy).toList();
            if (encodedBytes < 0 || encodedBytes > MAX_ENCODED_CARGO_BYTES) {
                throw new IllegalArgumentException("Worker cargo telemetry exceeded its byte budget");
            }
        }
    }

    private WorkerTelemetryBounds() {
    }
}
