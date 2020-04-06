package com.qouteall.immersive_portals.portal;

import com.qouteall.immersive_portals.McHelper;
import com.qouteall.immersive_portals.ModMain;
import com.qouteall.immersive_portals.portal.nether_portal.BlockPortalShape;
import com.qouteall.immersive_portals.portal.nether_portal.NetherPortalEntity;
import com.qouteall.immersive_portals.portal.nether_portal.NetherPortalGeneration;
import net.minecraft.block.Block;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.server.ServerWorld;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

public class ClassicalPortalConverter {
    
    public static interface PortalConverter {
        void onPlayerTeleported(
            ServerWorld oldWorld,
            ServerPlayerEntity player,
            BlockPos portalBlockPos
        );
    }
    
    private static Map<Block, PortalConverter> converterMap = new HashMap<>();
    
    public static void onPlayerChangeDimension(
        ServerPlayerEntity player,
        ServerWorld oldWorld,
        Vec3d oldPos
    ) {
        BlockPos playerPos = new BlockPos(oldPos);
        Iterator<BlockPos> iterator = BlockPos.getAllInBox(
            playerPos.add(-2, -2, -2),
            playerPos.add(2, 2, 2)
        ).iterator();
        while (iterator.hasNext()) {
            BlockPos blockPos = iterator.next();
            Block block = oldWorld.getBlockState(blockPos).getBlock();
            PortalConverter portalConverter = converterMap.get(block);
            if (portalConverter != null) {
                portalConverter.onPlayerTeleported(
                    oldWorld, player, blockPos
                );
                return;
            }
        }
    }
    
    public static void init() {
    
    }
    
    public static void registerClassicalPortalConverter(
        Block portalBlock,
        Function<ServerWorld, Predicate<BlockPos>> portalBody,
        Function<ServerWorld, Predicate<BlockPos>> portalFrame,
        EntityType<NetherPortalEntity> entityType
    ) {
        converterMap.put(
            portalBlock,
            (oldWorld, oldPlayer, portalBlockPos) -> {
                Predicate<BlockPos> portalBodyPredicate = portalBody.apply(oldWorld);
                Predicate<BlockPos> portalFramePredicate = portalFrame.apply(oldWorld);
                UUID playerUuid = oldPlayer.getUniqueID();
                Arrays.stream(Direction.Axis.values()).map(
                    axis -> {
                        return BlockPortalShape.findArea(
                            portalBlockPos,
                            axis,
                            portalBodyPredicate,
                            portalFramePredicate
                        );
                    }
                ).filter(Objects::nonNull).findFirst().ifPresent(blockPortalShape -> {
                    ModMain.serverTaskList.addTask(() -> {
                        ServerPlayerEntity player =
                            McHelper.getServer().getPlayerList().getPlayerByUUID(playerUuid);
                        if (player == null) {
                            return true;
                        }
                        if (player.isInvulnerableDimensionChange()) {
                            return false;
                        }
                        if (player.queuedEndExit) {
                            return false;
                        }
                        
                        onPlayerLandedOnAnotherDimension(
                            player,
                            oldWorld,
                            blockPortalShape,
                            portalBody,
                            portalFrame,
                            entityType
                        );
                        return true;
                    });
                });
            }
        );
    }
    
    private static void onPlayerLandedOnAnotherDimension(
        ServerPlayerEntity player,
        ServerWorld oldWorld,
        BlockPortalShape oldPortalShape,
        Function<ServerWorld, Predicate<BlockPos>> portalBody,
        Function<ServerWorld, Predicate<BlockPos>> portalFrame,
        EntityType<NetherPortalEntity> entityType
    ) {
        BlockPos playerPos = player.getPosition();
        BlockPos.Mutable temp = new BlockPos.Mutable();
        Predicate<BlockPos> portalBodyPredicate = portalBody.apply(((ServerWorld) player.world));
        Predicate<BlockPos> portalFramePredicate = portalFrame.apply(((ServerWorld) player.world));
        BlockPortalShape thisSideShape = BlockPos.getAllInBox(
            playerPos.add(-10, -10, -10),
            playerPos.add(10, 10, 10)
        ).map(
            blockPos -> oldPortalShape.matchShape(
                portalBodyPredicate,
                portalFramePredicate,
                blockPos,
                temp
            )
        ).filter(Objects::nonNull).findFirst().orElse(null);
        
        if (thisSideShape == null) {
            player.sendMessage(new TranslationTextComponent("imm_ptl.auto_portal_generation_failed"));
        }
        else {
            NetherPortalGeneration.generateBreakablePortalEntities(
                new NetherPortalGeneration.Info(
                    oldWorld.dimension.getType(),
                    player.dimension,
                    oldPortalShape,
                    thisSideShape
                ),
                entityType
            );
            player.sendMessage(new TranslationTextComponent("imm_ptl.auto_portal_generation_succeeded"));
        }
    }
    
    
}