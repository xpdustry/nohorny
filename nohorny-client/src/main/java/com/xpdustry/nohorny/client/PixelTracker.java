// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.client;

import arc.graphics.Color;
import com.xpdustry.nohorny.common.MindustryAuthor;
import com.xpdustry.nohorny.common.MindustryPixel;
import com.xpdustry.nohorny.common.VirtualBuilding;
import java.util.function.ToIntFunction;
import mindustry.gen.Building;
import mindustry.world.Block;
import mindustry.world.blocks.distribution.Sorter;
import mindustry.world.blocks.power.LightBlock;
import org.jspecify.annotations.Nullable;

// Pixel art made of single tile buildings displaying a configurable color
final class PixelTracker<B extends Building> extends BuildingImageTracker<B, MindustryPixel> {

    private static final int MAX_GROUP_RANGE = 150;
    private static final int MAX_GROUP_STEPS = 200;
    private static final int MIN_PIXEL_GROUP_SIZE = 16;

    private final ToIntFunction<B> color;

    private PixelTracker(
            final NoHornyEventBus events,
            final GroupClassifier classifier,
            final Class<B> buildingType,
            final Class<? extends Block> blockType,
            final ToIntFunction<B> color) {
        super(events, classifier, buildingType, blockType, MAX_GROUP_RANGE, MAX_GROUP_STEPS, MIN_PIXEL_GROUP_SIZE);
        this.color = color;
    }

    public static PixelTracker<Sorter.SorterBuild> sorters(
            final NoHornyEventBus events, final GroupClassifier classifier) {
        // Unconfigured sorters display a dark cross
        return new PixelTracker<>(
                events,
                classifier,
                Sorter.SorterBuild.class,
                Sorter.class,
                building -> building.sortItem == null ? Color.blackRgba : building.sortItem.color.rgba());
    }

    public static PixelTracker<LightBlock.LightBuild> illuminators(
            final NoHornyEventBus events, final GroupClassifier classifier) {
        // Uses the raw color, so the illuminator sprite does not tint the image
        return new PixelTracker<>(
                events, classifier, LightBlock.LightBuild.class, LightBlock.class, building -> building.color | 0xFF);
    }

    @Override
    protected MindustryPixel data(final B building, final @Nullable MindustryAuthor author) {
        return new MindustryPixel(this.color.applyAsInt(building), author);
    }

    @Override
    protected boolean isEligible(final VirtualBuilding.Group<MindustryPixel> group) {
        if (!super.isEligible(group)) {
            return false;
        }
        final var first = group.elements().iterator().next().data().rgba();
        for (final var element : group.elements()) {
            if (element.data().rgba() != first) {
                return true;
            }
        }
        return false;
    }
}
