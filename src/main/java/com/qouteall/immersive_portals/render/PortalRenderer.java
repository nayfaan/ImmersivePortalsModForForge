package com.qouteall.immersive_portals.render;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.platform.GlStateManager;
import com.qouteall.immersive_portals.CGlobal;
import com.qouteall.immersive_portals.CHelper;
import com.qouteall.immersive_portals.Global;
import com.qouteall.immersive_portals.Helper;
import com.qouteall.immersive_portals.McHelper;
import com.qouteall.immersive_portals.OFInterface;
import com.qouteall.immersive_portals.portal.Mirror;
import com.qouteall.immersive_portals.portal.Portal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.renderer.Matrix3f;
import net.minecraft.client.renderer.Matrix4f;
import net.minecraft.client.renderer.Quaternion;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.dimension.DimensionType;

import java.util.Comparator;
import java.util.List;
import java.util.Stack;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public abstract class PortalRenderer {
    
    public static final Minecraft mc = Minecraft.getInstance();
    protected Supplier<Integer> maxPortalLayer = () -> Global.maxPortalLayer;
    protected Stack<Portal> portalLayers = new Stack<>();
    
    //this WILL be called when rendering portal
    public abstract void onBeforeTranslucentRendering(MatrixStack matrixStack);
    
    //this WILL be called when rendering portal
    public abstract void onAfterTranslucentRendering(MatrixStack matrixStack);
    
    //this WILL be called when rendering portal
    public abstract void onRenderCenterEnded(MatrixStack matrixStack);
    
    //this will NOT be called when rendering portal
    public abstract void prepareRendering();
    
    //this will NOT be called when rendering portal
    public abstract void finishRendering();
    
    //this will be called when rendering portal entities
    public abstract void renderPortalInEntityRenderer(Portal portal);
    
    //0 for rendering outer world
    //1 for rendering world inside portal
    //2 for rendering world inside PortalEntity inside portal
    public int getPortalLayer() {
        return portalLayers.size();
    }
    
    public boolean isRendering() {
        return getPortalLayer() != 0;
    }
    
    public abstract boolean shouldSkipClearing();
    
    public Portal getRenderingPortal() {
        return portalLayers.peek();
    }
    
    public boolean shouldRenderPlayerItself() {
        if (!Global.renderYourselfInPortal) {
            return false;
        }
        if (!isRendering()) {
            return false;
        }
        if (mc.renderViewEntity.dimension == MyRenderHelper.originalPlayerDimension) {
            Portal renderingPortal = getRenderingPortal();
            return renderingPortal.canRenderEntityInsideMe(
                MyRenderHelper.originalPlayerPos.add(
                    0, mc.renderViewEntity.getPosYEye(), 0
                ),
                0.1
            );
        }
        return false;
    }
    
    public boolean shouldRenderEntityNow(Entity entity) {
        if (OFInterface.isShadowPass.getAsBoolean()) {
            return true;
        }
        if (isRendering()) {
            if (entity instanceof ClientPlayerEntity) {
                return shouldRenderPlayerItself();
            }
            return getRenderingPortal().canRenderEntityInsideMe(
                entity.getEyePosition(1), -0.01
            );
        }
        return true;
    }
    
    protected void renderPortals(MatrixStack matrixStack) {
        assert mc.renderViewEntity.world == mc.world;
        assert mc.renderViewEntity.dimension == mc.world.dimension.getType();
    
        for (Portal portal : getPortalsNearbySorted()) {
            renderPortalIfRoughCheckPassed(portal, matrixStack);
        }
    }
    
    private void renderPortalIfRoughCheckPassed(
        Portal portal,
        MatrixStack matrixStack
    ) {
        if (!portal.isPortalValid()) {
            Helper.err("rendering invalid portal " + portal);
            return;
        }
    
        Vec3d thisTickEyePos = getRoughTestCameraPos();
    
        if (!portal.isInFrontOfPortal(thisTickEyePos)) {
            return;
        }
    
        if (isRendering()) {
            Portal outerPortal = portalLayers.peek();
            if (isReversePortal(portal, outerPortal)) {
                return;
            }
        }
    
    
        doRenderPortal(portal, matrixStack);
    }
    
    private static boolean isReversePortal(Portal currPortal, Portal outerPortal) {
        if (currPortal.dimension != outerPortal.dimensionTo) {
            return false;
        }
        if (currPortal.dimensionTo != outerPortal.dimension) {
            return false;
        }
        if (currPortal.getNormal().dotProduct(outerPortal.getContentDirection()) > -0.9) {
            return false;
        }
        return !outerPortal.canRenderEntityInsideMe(currPortal.getPositionVec(), 0.1);
    }
    
    private Vec3d getRoughTestCameraPos() {
        if (mc.gameRenderer.getActiveRenderInfo().isThirdPerson()) {
            return mc.gameRenderer.getActiveRenderInfo().getProjectedView();
        }
        return mc.renderViewEntity.getEyePosition(MyRenderHelper.partialTicks);
    }
    
    private List<Portal> getPortalsNearbySorted() {
        Vec3d cameraPos = mc.renderViewEntity.getPositionVec();
        double range = 128.0;
        return CHelper.getClientNearbyPortals(range)
            .sorted(
                Comparator.comparing(portalEntity ->
                    portalEntity.getDistanceToNearestPointInPortal(cameraPos)
                )
            ).collect(Collectors.toList());
    }
    
    protected abstract void doRenderPortal(
        Portal portal,
        MatrixStack matrixStack
    );
    
    protected final void manageCameraAndRenderPortalContent(
        Portal portal
    ) {
        if (getPortalLayer() > maxPortalLayer.get()) {
            return;
        }
        
        Entity cameraEntity = mc.renderViewEntity;
        ActiveRenderInfo camera = mc.gameRenderer.getActiveRenderInfo();
    
        if (getPortalLayer() >= 2 &&
            portal.getDistanceToNearestPointInPortal(cameraEntity.getPositionVec()) >
                (16 * maxPortalLayer.get())
        ) {
            return;
        }
    
        MyRenderHelper.onBeginPortalWorldRendering(portalLayers);
    
        assert cameraEntity.world == mc.world;
    
        Vec3d oldEyePos = McHelper.getEyePos(cameraEntity);
        Vec3d oldLastTickEyePos = McHelper.getLastTickEyePos(cameraEntity);
        DimensionType oldDimension = cameraEntity.dimension;
        ClientWorld oldWorld = ((ClientWorld) cameraEntity.world);
    
        Vec3d oldCameraPos = camera.getProjectedView();
    
        Vec3d newEyePos = portal.transformPoint(oldEyePos);
        Vec3d newLastTickEyePos = portal.transformPoint(oldLastTickEyePos);
        DimensionType newDimension = portal.dimensionTo;
        ClientWorld newWorld =
            CGlobal.clientWorldLoader.getOrCreateFakedWorld(newDimension);
        //Vec3d newCameraPos = portal.applyTransformationToPoint(oldCameraPos);
    
        McHelper.setEyePos(cameraEntity, newEyePos, newLastTickEyePos);
        cameraEntity.dimension = newDimension;
        cameraEntity.world = newWorld;
        mc.world = newWorld;
    
        renderPortalContentWithContextSwitched(
            portal, oldCameraPos, oldWorld
        );
        
        //restore the position
        cameraEntity.dimension = oldDimension;
        cameraEntity.world = oldWorld;
        mc.world = oldWorld;
        McHelper.setEyePos(cameraEntity, oldEyePos, oldLastTickEyePos);
        
        GlStateManager.enableDepthTest();
        GlStateManager.disableBlend();
        MyRenderHelper.restoreViewPort();
    
        CGlobal.myGameRenderer.resetFog();
    }
    
    protected void renderPortalContentWithContextSwitched(
        Portal portal, Vec3d oldCameraPos, ClientWorld oldWorld
    ) {
        GlStateManager.enableAlphaTest();
        GlStateManager.enableCull();
    
        WorldRenderer worldRenderer = CGlobal.clientWorldLoader.getWorldRenderer(portal.dimensionTo);
        ClientWorld destClientWorld = CGlobal.clientWorldLoader.getOrCreateFakedWorld(portal.dimensionTo);
    
        CHelper.checkGlError();
    
        CGlobal.myGameRenderer.renderWorld(
            MyRenderHelper.partialTicks, worldRenderer, destClientWorld, oldCameraPos, oldWorld
        );
    
        CHelper.checkGlError();
    
    }
    
    public void applyAdditionalTransformations(MatrixStack matrixStack) {
        portalLayers.forEach(portal -> {
            if (portal instanceof Mirror) {
                Matrix4f matrix = TransformationManager.getMirrorTransformation(portal.getNormal());
                matrixStack.getLast().getMatrix().mul(matrix);
                matrixStack.getLast().getNormal().mul(new Matrix3f(matrix));
            }
            else if (portal.rotation != null) {
                Quaternion rot = portal.rotation.copy();
                rot.conjugate();
                matrixStack.rotate(rot);
            }
        });
    }
    
}
