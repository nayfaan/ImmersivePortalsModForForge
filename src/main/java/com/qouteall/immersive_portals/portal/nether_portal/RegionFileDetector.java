package com.qouteall.immersive_portals.portal.nether_portal;

import com.qouteall.immersive_portals.McHelper;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.server.ServerWorld;
import java.io.File;

public class RegionFileDetector {
    
    /**
     * {@link RegionBasedStorage#getRegionFile(ChunkPos)}
     */
    public static boolean doesRegionFileExist(
        ServerWorld world,
        ChunkPos pos
    ) {
        File saveDir = McHelper.getIEStorage(world.getDimension().getType()).portal_getSaveDir();
    
        File regionDir = new File(saveDir, "region");
        
        File file = new File(regionDir, "r." + pos.getRegionCoordX() + "." + pos.getRegionCoordZ() + ".mca");
    
        return file.exists();
    }
}