package com.qouteall.immersive_portals.optifine_compatibility;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.platform.GlStateManager;
import com.qouteall.immersive_portals.CGlobal;
import com.qouteall.immersive_portals.CHelper;
import com.qouteall.immersive_portals.OFInterface;
import com.qouteall.immersive_portals.ducks.IEFrameBuffer;
import com.qouteall.immersive_portals.portal.Portal;
import com.qouteall.immersive_portals.render.*;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import net.optifine.shaders.Shaders;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

public class RendererMixed extends PortalRenderer {
    private SecondaryFrameBuffer[] deferredFbs;
    
    @Override
    public boolean shouldSkipClearing() {
        return false;
    }
    
    @Override
    public void onRenderCenterEnded(MatrixStack matrixStack) {
        int portalLayer = getPortalLayer();
        
        initStencilForLayer(portalLayer);
        
        deferredFbs[portalLayer].fb.bindFramebuffer(true);
        
        GL11.glEnable(GL11.GL_STENCIL_TEST);
        GL11.glStencilFunc(GL11.GL_EQUAL, portalLayer, 0xFF);
        GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
        
        Framebuffer mcFrameBuffer = mc.getFramebuffer();
        MyRenderHelper.myDrawFrameBuffer(mcFrameBuffer, false, true);
        
        GL11.glDisable(GL11.GL_STENCIL_TEST);
        
        renderPortals(matrixStack);
    }
    
    private void initStencilForLayer(int portalLayer) {
        if (portalLayer == 0) {
            deferredFbs[portalLayer].fb.bindFramebuffer(true);
            GlStateManager.clearStencil(0);
            GlStateManager.clear(GL11.GL_STENCIL_BUFFER_BIT, true);
        }
        else {
            GL30.glBindFramebuffer(
                GL30.GL_READ_FRAMEBUFFER,
                deferredFbs[portalLayer - 1].fb.framebufferObject
            );
            GL30.glBindFramebuffer(
                GL30.GL_DRAW_FRAMEBUFFER,
                deferredFbs[portalLayer].fb.framebufferObject
            );
            
            GL30.glBlitFramebuffer(
                0, 0, deferredFbs[0].fb.framebufferWidth, deferredFbs[0].fb.framebufferHeight,
                0, 0, deferredFbs[0].fb.framebufferWidth, deferredFbs[0].fb.framebufferHeight,
                GL11.GL_STENCIL_BUFFER_BIT, GL11.GL_NEAREST
            );
        }
    }
    
    @Override
    public void onBeforeTranslucentRendering(MatrixStack matrixStack) {
    
    }
    
    @Override
    public void onAfterTranslucentRendering(MatrixStack matrixStack) {
        OFHelper.copyFromShaderFbTo(
            deferredFbs[getPortalLayer()].fb,
            GL11.GL_DEPTH_BUFFER_BIT
        );
    }
    
    @Override
    public void prepareRendering() {
        if (CGlobal.shaderManager == null) {
            CGlobal.shaderManager = new ShaderManager();
        }
        
        if (deferredFbs == null) {
            deferredFbs = new SecondaryFrameBuffer[maxPortalLayer.get() + 1];
            for (int i = 0; i < deferredFbs.length; i++) {
                deferredFbs[i] = new SecondaryFrameBuffer();
            }
        }
        
        for (SecondaryFrameBuffer deferredFb : deferredFbs) {
            deferredFb.prepare();
            ((IEFrameBuffer) deferredFb.fb).setIsStencilBufferEnabledAndReload(true);
            
            deferredFb.fb.bindFramebuffer(true);
            GlStateManager.clearColor(1, 0, 1, 0);
            GlStateManager.clearDepth(1);
            GlStateManager.clearStencil(0);
            GlStateManager.clear(
                GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_STENCIL_BUFFER_BIT,
                true
            );
            
        }
        
        OFInterface.bindToShaderFrameBuffer.run();
    }
    
    @Override
    public void finishRendering() {
        GlStateManager.colorMask(true, true, true, true);
        Shaders.useProgram(Shaders.ProgramNone);
        RenderHelper.disableStandardItemLighting();
        
        if (MyRenderHelper.getRenderedPortalNum() == 0) {
            return;
        }
    
        Framebuffer mainFrameBuffer = mc.getFramebuffer();
        mainFrameBuffer.bindFramebuffer(true);
    
        deferredFbs[0].fb.framebufferRender(
            mainFrameBuffer.framebufferWidth,
            mainFrameBuffer.framebufferHeight
        );
    
        CHelper.checkGlError();
    }
    
    @Override
    protected void doRenderPortal(Portal portal, MatrixStack matrixStack) {
        
        //write to deferred buffer
        if (!tryRenderViewAreaInDeferredBufferAndIncreaseStencil(portal)) {
            return;
        }
        
        portalLayers.push(portal);
        
        OFInterface.bindToShaderFrameBuffer.run();
        manageCameraAndRenderPortalContent(portal);
        
        int innerLayer = getPortalLayer();
        
        portalLayers.pop();
        
        int outerLayer = getPortalLayer();
        
        if (innerLayer > maxPortalLayer.get()) {
            return;
        }
        
        deferredFbs[outerLayer].fb.bindFramebuffer(true);
        
        GlStateManager.enableAlphaTest();
        MyRenderHelper.myDrawFrameBuffer(
            deferredFbs[innerLayer].fb,
            true,
            true
        );
    }
    
    //NOTE it will write to shader depth buffer
    //it's drawing into shader fb
    private boolean tryRenderViewAreaInDeferredBufferAndIncreaseStencil(Portal portal) {
        return QueryManager.renderAndGetDoesAnySamplePassed(() -> {
            int portalLayer = getPortalLayer();
            
            initStencilForLayer(portalLayer);
            
            deferredFbs[portalLayer].fb.bindFramebuffer(true);
            
            GL11.glEnable(GL11.GL_STENCIL_TEST);
            GL11.glStencilFunc(GL11.GL_EQUAL, portalLayer, 0xFF);
            GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_INCR);
            
            myDrawPortalViewArea(portal);
            
            GL11.glDisable(GL11.GL_STENCIL_TEST);
            
            OFInterface.bindToShaderFrameBuffer.run();
        });
    }
    
    //maybe it's similar to rendererUsingStencil's ?
    private void myDrawPortalViewArea(Portal portal) {
        GlStateManager.enableDepthTest();
        GlStateManager.disableTexture();
        GlStateManager.colorMask(false, false, false, false);
    
        assert false;
    
        //MyRenderHelper.setupCameraTransformation();
        GL20.glUseProgram(0);
    
        //ViewAreaRenderer.drawPortalViewTriangle(portal);
    
        GlStateManager.enableTexture();
        GlStateManager.colorMask(true, true, true, true);
    }
    
    @Override
    protected void renderPortalContentWithContextSwitched(
        Portal portal, Vec3d oldCameraPos, ClientWorld oldWorld
    ) {
        OFGlobal.shaderContextManager.switchContextAndRun(
            () -> {
                OFInterface.bindToShaderFrameBuffer.run();
                super.renderPortalContentWithContextSwitched(portal, oldCameraPos, oldWorld);
            }
        );
    }
    
    @Override
    public void renderPortalInEntityRenderer(Portal portal) {
        assert false;
//        if (Shaders.isShadowPass) {
//            ViewAreaRenderer.drawPortalViewTriangle(portal);
//        }
    }
}
