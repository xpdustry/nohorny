// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.common;

import java.util.Collection;
import java.util.List;
import org.jspecify.annotations.Nullable;

public interface VirtualBuildingIndex2<T> {

    static <T> VirtualBuildingIndex2<T> create(final int chunkSize) {
        return new VirtualBuildingIndex2Impl<>(chunkSize);
    }

    BaseView<T> withBaseView();

    LiveView<T> withLiveView();

    interface CommonView<T> {

        @Nullable VirtualBuilding<T> select(final int x, final int y);

        Collection<VirtualBuilding<T>> selectAllWithinSquare(final int x, final int y, final int size);

        Collection<VirtualBuilding<T>> selectAll();

        boolean exists(final int x, final int y);

        boolean exists(final int x, final int y, final int size);
    }

    interface BaseView<T> extends CommonView<T> {

        VirtualBuilding.@Nullable Group<T> selectGroupWithinRange(final int x, final int y, final int range);

        List<VirtualBuilding.Group<T>> selectAllDirtyGroupsWithRange(final int range);
    }

    interface LiveView<T> extends CommonView<T> {

        @Nullable VirtualBuilding<T> insert(final int x, final int y, final int size, final T data);

        @Nullable VirtualBuilding<T> remove(final int x, final int y);

        VirtualBuilding<T> upsert(final int x, final int y, final int size, final T data);

        void removeAll();

        Collection<VirtualBuilding<T>> removeAllWithinSquare(final int x, final int y, final int size);

        void markDirty(final int x, final int y);

        boolean hasNoDirtyTiles();

        void writeToBase(final boolean write);
    }
}
