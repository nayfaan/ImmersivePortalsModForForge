package com.qouteall.immersive_portals.render.context_management;

import com.qouteall.immersive_portals.Helper;
import com.qouteall.immersive_portals.ModMain;
import net.minecraft.client.renderer.vertex.VertexBuffer;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.ArrayList;

/**
 * {@link net.minecraft.client.render.WorldRenderer#renderClouds(MatrixStack, float, double, double, double)}
 */
public class CloudContext {
    
    //keys
    public int lastCloudsBlockX = 0;
    public int lastCloudsBlockY = 0;
    public int lastCloudsBlockZ = 0;
    public RegistryKey<World> dimension = null;
    public Vector3d cloudColor;
    
    public VertexBuffer cloudsBuffer = null;
    
    public static final ArrayList<CloudContext> contexts = new ArrayList<>();
    
    public static void init() {
        ModMain.clientCleanupSignal.connect(CloudContext::cleanup);
    }
    
    public CloudContext() {
    
    }
    
    private static void cleanup() {
        for (CloudContext context : contexts) {
            context.dispose();
        }
        contexts.clear();
    }
    
    public void dispose() {
        if (cloudsBuffer != null) {
            cloudsBuffer.close();
            cloudsBuffer = null;
        }
    }
    
    @Nullable
    public static CloudContext findAndTakeContext(
        int lastCloudsBlockX, int lastCloudsBlockY, int lastCloudsBlockZ,
        RegistryKey<World> dimension, Vector3d cloudColor
    ) {
        int i = Helper.indexOf(contexts, c ->
            c.lastCloudsBlockX == lastCloudsBlockX &&
                c.lastCloudsBlockY == lastCloudsBlockY &&
                c.lastCloudsBlockZ == lastCloudsBlockZ &&
                c.dimension == dimension &&
                c.cloudColor.squareDistanceTo(cloudColor) < 2.0E-4D
        );
        
        if (i == -1) {
            return null;
        }
        
        CloudContext result = contexts.get(i);
        contexts.remove(i);
        
        return result;
    }
    
    public static void appendContext(CloudContext context) {
        contexts.add(context);
        
        if (contexts.size() > 15) {
            contexts.remove(0).dispose();
        }
    }
}
