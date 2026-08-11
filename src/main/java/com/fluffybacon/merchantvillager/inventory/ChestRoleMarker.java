package com.fluffybacon.merchantvillager.inventory;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.SignBlock;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationPropertyHelper;
import com.fluffybacon.merchantvillager.registry.ModBlocks;
import org.jspecify.annotations.Nullable;

/** Places and validates the waxed signs owned by one Merchant's Post. */
public final class ChestRoleMarker {
    private static final String OWNER_PREFIX = "merchant_villager:storage_marker:";

    public enum Role {
        IMPORT("[Import]", ""),
        EXPORT("[Export]", ""),
        DUAL("[Import]", "[Export]");

        private final String firstLine;
        private final String secondLine;

        Role(String firstLine, String secondLine) {
            this.firstLine = firstLine;
            this.secondLine = secondLine;
        }
    }

    public static Optional<BlockPos> reconcile(
        ServerWorld world,
        BlockPos postPos,
        BlockPos chestPos,
        @Nullable BlockPos previousMarker,
        Role role,
        Set<BlockPos> reserved
    ) {
        List<BlockPos> candidates = markerCandidates(postPos, chestPos);
        if (previousMarker != null) {
            // Do not create a duplicate merely because the adjacent marker
            // chunk is temporarily unavailable. It will be validated on load.
            if (!world.isChunkLoaded(previousMarker)) {
                return Optional.of(previousMarker.toImmutable());
            }
            if (!reserved.contains(previousMarker)
                && candidates.contains(previousMarker)
                && matches(world, previousMarker, postPos, chestPos, role)) {
                return Optional.of(previousMarker.toImmutable());
            }
            removeOwned(world, previousMarker, postPos, chestPos, role);
        }
        for (BlockPos candidate : candidates) {
            if (reserved.contains(candidate) || !world.isChunkLoaded(candidate)) {
                continue;
            }
            // Crash recovery: adopt an owned marker that reached the world
            // before the post's marker position reached disk.
            if (matches(world, candidate, postPos, chestPos, role)) {
                return Optional.of(candidate.toImmutable());
            }
            if (!world.getBlockState(candidate).isReplaceable()) {
                continue;
            }
            Direction facing = horizontalDirection(postPos, chestPos).getOpposite();
            BlockState sign = ModBlocks.CHEST_ROLE_MARKER.getDefaultState().with(
                SignBlock.ROTATION,
                RotationPropertyHelper.fromDirection(facing)
            );
            if (!sign.canPlaceAt(world, candidate)
                || !world.setBlockState(candidate, sign, Block.NOTIFY_ALL)) {
                continue;
            }
            if (world.getBlockEntity(candidate) instanceof SignBlockEntity blockEntity) {
                String owner = ownerToken(postPos, chestPos, role);
                SignText text = new SignText()
                    .withMessage(0, Text.literal(role.firstLine)
                        .styled(style -> style.withInsertion(owner)))
                    .withMessage(1, Text.literal(role.secondLine));
                blockEntity.setText(text, true);
                blockEntity.setText(text, false);
                blockEntity.setWaxed(true);
                blockEntity.markDirty();
                world.updateListeners(candidate, sign, sign, Block.NOTIFY_LISTENERS);
                return Optional.of(candidate.toImmutable());
            }
            world.removeBlock(candidate, false);
        }
        return Optional.empty();
    }

    /**
     * Attempts exact owned-marker cleanup without loading chunks.
     *
     * @return {@code false} only when cleanup cannot yet be validated because
     * the marker chunk is unavailable (or a matched marker could not be removed)
     */
    public static boolean removeOwned(
        ServerWorld world,
        @Nullable BlockPos markerPos,
        BlockPos postPos,
        BlockPos chestPos,
        Role expectedRole
    ) {
        if (markerPos == null) {
            return true;
        }
        if (!world.isChunkLoaded(markerPos)) {
            return false;
        }
        if (matches(world, markerPos, postPos, chestPos, expectedRole)) {
            return world.removeBlock(markerPos, false);
        }
        // The chunk is loaded and the block is absent or no longer ours. There
        // is no owned marker left for this post to clean up at this position.
        return true;
    }

    public static boolean matches(
        ServerWorld world,
        BlockPos markerPos,
        BlockPos postPos,
        BlockPos chestPos,
        Role role
    ) {
        if (!world.isChunkLoaded(markerPos)
            || !(world.getBlockEntity(markerPos) instanceof SignBlockEntity sign)) {
            return false;
        }
        return matches(sign, postPos, chestPos, role);
    }

    public static boolean matches(
        SignBlockEntity sign, BlockPos postPos, BlockPos chestPos, Role role
    ) {
        Text[] messages = sign.getText(true).getMessages(false);
        return messages.length >= 2
            && messages[0].getString().equals(role.firstLine)
            && messages[1].getString().equals(role.secondLine)
            && ownerToken(postPos, chestPos, role).equals(
                messages[0].getStyle().getInsertion()
            )
            && sign.isWaxed();
    }

    /** Strong marker tag used by break handling even after the block left the world. */
    public static boolean isGeneratedMarker(SignBlockEntity sign) {
        Text[] messages = sign.getText(true).getMessages(false);
        return messages.length > 0
            && messages[0].getStyle().getInsertion() != null
            && messages[0].getStyle().getInsertion().startsWith(OWNER_PREFIX);
    }

    private static String ownerToken(BlockPos postPos, BlockPos chestPos, Role role) {
        return OWNER_PREFIX + postPos.asLong() + ":" + chestPos.asLong() + ":" + role.name();
    }

    private static List<BlockPos> markerCandidates(BlockPos postPos, BlockPos chestPos) {
        return List.of(chestPos.up().toImmutable());
    }

    private static Direction horizontalDirection(BlockPos postPos, BlockPos chestPos) {
        for (Direction direction : Direction.Type.HORIZONTAL) {
            if (postPos.offset(direction).equals(chestPos)) {
                return direction;
            }
        }
        return Direction.NORTH;
    }

    private ChestRoleMarker() {
    }
}
