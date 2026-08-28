// Copyright (c) 2001, Eric D. Friedman All Rights Reserved.
// Copyright (c) 2009, Rob Eden All Rights Reserved.
// Copyright (c) 2009, Jeff Randall All Rights Reserved.
//
// This library is free software; you can redistribute it and/or modify it under
// the terms of the GNU Lesser General Public License as published by the Free
// Software Foundation; either version 2.1 of the License, or any later version.
// Modified for NoHorny in 2026: reduced to the int-to-object index operations
// used by VirtualBuildingIndex and given an array-cloning snapshot operation.

package com.xpdustry.nohorny.common;

import java.util.Arrays;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * The part of GNU Trove 3.1.0's {@code TIntObjectHashMap} needed by
 * {@link VirtualBuildingIndex}. Collection views, iterators, procedures, serialization, and
 * auto-compaction were removed. Snapshotting was added through {@link #clone()}.
 */
final class TroveIntObjectMap<V> implements Cloneable {

    private static final byte FREE = 0;
    private static final byte FULL = 1;
    private static final byte REMOVED = 2;
    private static final float LOAD_FACTOR = 0.5F;

    private int[] keys;
    private @Nullable Object[] values;
    private byte[] states;
    private int size;
    private int free;
    private int maxSize;
    private boolean consumedFreeSlot;

    public TroveIntObjectMap() {
        this(10);
    }

    public TroveIntObjectMap(final int initialCapacity) {
        final int capacity = TrovePrimeFinder.nextPrime((int) Math.ceil(initialCapacity / (double) LOAD_FACTOR));
        this.keys = new int[capacity];
        this.values = new Object[capacity];
        this.states = new byte[capacity];
        this.computeMaxSize();
    }

    public int size() {
        return this.size;
    }

    public boolean isEmpty() {
        return this.size == 0;
    }

    public boolean containsKey(final int key) {
        return this.index(key) >= 0;
    }

    @SuppressWarnings("unchecked")
    public @Nullable V get(final int key) {
        final int index = this.index(key);
        return index < 0 ? null : (V) this.values[index];
    }

    @SuppressWarnings({"unchecked", "UnusedReturnValue"})
    public @Nullable V put(final int key, final V value) {
        int index = this.insertKey(key);
        if (index < 0) {
            index = -index - 1;
            final var previous = (V) this.values[index];
            this.values[index] = value;
            return previous;
        }

        this.values[index] = value;
        this.postInsertHook();
        return null;
    }

    @SuppressWarnings({"unchecked", "UnusedReturnValue"})
    public @Nullable V remove(final int key) {
        final int index = this.index(key);
        if (index < 0) {
            return null;
        }

        final var previous = (V) this.values[index];
        this.keys[index] = 0;
        this.values[index] = null;
        this.states[index] = REMOVED;
        this.size--;
        return previous;
    }

    public void clear() {
        Arrays.fill(this.keys, 0);
        Arrays.fill(this.values, null);
        Arrays.fill(this.states, FREE);
        this.size = 0;
        this.free = this.states.length;
    }

    @SuppressWarnings("unchecked")
    public void forEachValue(final Consumer<? super V> consumer) {
        for (int index = this.states.length; index-- > 0; ) {
            if (this.states[index] == FULL) {
                //noinspection DataFlowIssue the state already asserts it's not null
                consumer.accept((V) this.values[index]);
            }
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public TroveIntObjectMap<V> clone() {
        try {
            final var clone = (TroveIntObjectMap<V>) super.clone();
            clone.keys = this.keys.clone();
            clone.values = this.values.clone();
            clone.states = this.states.clone();
            return clone;
        } catch (final CloneNotSupportedException e) {
            throw new AssertionError(e);
        }
    }

    private int index(final int key) {
        final var states = this.states;
        final var keys = this.keys;
        final int length = states.length;
        final int hash = key & 0x7FFFFFFF;
        final int index = hash % length;
        final byte state = states[index];

        if (state == FREE) {
            return -1;
        }
        if (state == FULL && keys[index] == key) {
            return index;
        }
        return this.indexRehashed(key, index, hash, state);
    }

    private int indexRehashed(final int key, int index, final int hash, byte state) {
        final int probe = 1 + (hash % (this.states.length - 2));
        final int loopIndex = index;
        do {
            index -= probe;
            if (index < 0) {
                index += this.states.length;
            }
            state = this.states[index];
            if (state == FREE) {
                return -1;
            }
            if (state == FULL && this.keys[index] == key) {
                return index;
            }
        } while (index != loopIndex);
        return -1;
    }

    private int insertKey(final int key) {
        final int hash = key & 0x7FFFFFFF;
        int index = hash % this.states.length;
        byte state = this.states[index];
        this.consumedFreeSlot = false;

        if (state == FREE) {
            this.consumedFreeSlot = true;
            this.insertKeyAt(index, key);
            return index;
        }
        if (state == FULL && this.keys[index] == key) {
            return -index - 1;
        }

        final int probe = 1 + (hash % (this.states.length - 2));
        final int loopIndex = index;
        int firstRemoved = -1;
        do {
            if (state == REMOVED && firstRemoved == -1) {
                firstRemoved = index;
            }
            index -= probe;
            if (index < 0) {
                index += this.states.length;
            }
            state = this.states[index];
            if (state == FREE) {
                if (firstRemoved != -1) {
                    this.insertKeyAt(firstRemoved, key);
                    return firstRemoved;
                }
                this.consumedFreeSlot = true;
                this.insertKeyAt(index, key);
                return index;
            }
            if (state == FULL && this.keys[index] == key) {
                return -index - 1;
            }
        } while (index != loopIndex);

        this.insertKeyAt(firstRemoved, key);
        return firstRemoved;
    }

    private void insertKeyAt(final int index, final int key) {
        this.keys[index] = key;
        this.states[index] = FULL;
    }

    private void postInsertHook() {
        if (this.consumedFreeSlot) {
            this.free--;
        }
        this.size++;
        if (this.size > this.maxSize || this.free == 0) {
            this.rehash(
                    this.size > this.maxSize
                            ? TrovePrimeFinder.nextPrime(this.states.length << 1)
                            : this.states.length);
        }
    }

    private void rehash(final int capacity) {
        final var oldKeys = this.keys;
        final var oldValues = this.values;
        final var oldStates = this.states;
        this.keys = new int[capacity];
        this.values = new Object[capacity];
        this.states = new byte[capacity];

        for (int oldIndex = oldStates.length; oldIndex-- > 0; ) {
            if (oldStates[oldIndex] == FULL) {
                final int newIndex = this.insertKey(oldKeys[oldIndex]);
                this.values[newIndex] = oldValues[oldIndex];
            }
        }
        this.computeMaxSize();
    }

    private void computeMaxSize() {
        this.maxSize = Math.min(this.states.length - 1, (int) (this.states.length * LOAD_FACTOR));
        this.free = this.states.length - this.size;
    }
}
