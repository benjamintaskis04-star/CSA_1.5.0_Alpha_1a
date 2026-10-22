package com.benjamintaskis.createsignalextensions.client;

import com.benjamintaskis.createsignalextensions.content.HeightVariable;
import com.benjamintaskis.createsignalextensions.content.signal.SignalPlateBlock;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Alpha 7a4: moves height-variable baked geometry at quad generation time.
 * This works in the normal chunk renderer and Sodium because both consume the
 * model's quads, instead of relying on a PoseStack hook around renderBatched.
 */
public final class HeightVariableBakedModel extends BakedModelWrapper<BakedModel> {
    public HeightVariableBakedModel(BakedModel originalModel) {
        super(originalModel);
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand) {
        return shifted(originalModel.getQuads(state, side, rand), state);
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand,
                                    ModelData extraData, @Nullable RenderType renderType) {
        return shifted(originalModel.getQuads(state, side, rand, extraData, renderType), state);
    }

    private static List<BakedQuad> shifted(List<BakedQuad> source, @Nullable BlockState state) {
        if (state == null || !HeightVariable.supports(state) || state.getBlock() instanceof SignalPlateBlock) return source;
        float dy = (float) HeightVariable.blocks(state);
        if (dy == 0.0f || source.isEmpty()) return source;

        List<BakedQuad> result = new ArrayList<>(source.size());
        for (BakedQuad quad : source) result.add(shift(quad, dy));
        return result;
    }

    private static BakedQuad shift(BakedQuad quad, float dy) {
        int[] vertices = quad.getVertices().clone();
        int stride = vertices.length / 4;
        for (int vertex = 0; vertex < 4; vertex++) {
            int yIndex = vertex * stride + 1;
            float y = Float.intBitsToFloat(vertices[yIndex]);
            vertices[yIndex] = Float.floatToRawIntBits(y + dy);
        }
        return new BakedQuad(vertices, quad.getTintIndex(), quad.getDirection(), quad.getSprite(), quad.isShade());
    }
}
