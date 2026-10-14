package com.isaacmod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.render.entity.FlyingItemEntityRenderer;

public class IsaacModClient implements ClientModInitializer {
    @Override
    public void onInitialize() {
        EntityRendererRegistry.register(IsaacMod.TEAR, FlyingItemEntityRenderer::new);
    }
}
