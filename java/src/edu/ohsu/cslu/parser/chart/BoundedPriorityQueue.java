/*
 * Copyright 2010-2014, Oregon Health & Science University
 * 
 * This file is part of the BUBS Parser.
 * 
 * The BUBS Parser is free software: you can redistribute it and/or 
 * modify  it under the terms of the GNU Affero General Public License 
 * as published by the Free Software Foundation, either version 3 of 
 * the License, or (at your option) any later version.
 * 
 * The BUBS Parser is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with the BUBS Parser. If not, see <http://www.gnu.org/licenses/>.
 * 
 * Further documentation and contact information is available at
 *   https://code.google.com/p/bubs-parser/ 
 */
package edu.ohsu.cslu.parser.chart;

import java.util.Arrays;

import edu.ohsu.cslu.grammar.Grammar;

/**
 * Represents a one-shot bounded priority queue of non-terminal cell entries, ordered by a figure-of-merit. Stored as a
 * double-ended queue so we can simultaneously pop the max-priority entries and replace the min-priority entries.
 * 
 * One-shot behavior: We know that we'll never pop more than the initial maxSize edges, so once we start popping, we
 * reduce maxSize with each pop, reducing the number of edges subsequently added which will never make it to the head of
 * the queue.
 * 
 * 
 * We could replace the bubble-sort with a min-max heap or a tree, but in our (limited) experiments, we've found
 * bubble-sort to be faster than other data structures at small beam widths (we often use a beam of 30 for pruned search
 * with the Berkeley grammar).
 * 
 * @author Aaron Dunlop
 * @since Sep 10, 2010
 */
public final class BoundedPriorityQueue {

    /**
     * Parallel array storing a bounded cell population (parents and a figure-of-merit for each). Analagous to
     * {@link ParallelArrayChart#insideProbabilities} and {@link PackedArrayChart#nonTerminalIndices}. The most probable
     * entry should be stored in index 0.
     */
    public final short[] nts;
    public final float[] foms;

    /** The array index of the head (maximum-probability) entry. */
    private int head = -1;

    /** The array index of the tail (minimum-probability) entry. */
    private int tail = -1;

    /** The maximum tail index (as determined by the current size bound) */
    private int maxTail;

    /** Optional reference to a {@link Grammar} instance. Used in {@link #toString()}. */
    private final Grammar grammar;

    public BoundedPriorityQueue(final int maxSize, final Grammar grammar) {
        foms = new float[maxSize];
        Arrays.fill(foms, Float.NEGATIVE_INFINITY);
        nts = new short[maxSize];
        this.grammar = grammar;
        this.maxTail = maxSize - 1;
    }

    public BoundedPriorityQueue(final int maxSize) {
        this(maxSize, null);
    }

    public int maxSize() {
        return maxTail + 1;
    }

    public void setMaxSize(final int maxSize) {
        final int currentMaxSize = maxTail - head + 1;
        if (maxSize != currentMaxSize) {
            if (maxSize > nts.length) {
                throw new IllegalArgumentException("Specified size (" + maxSize + ") exceeds storage capacity ("
                        + nts.length + ")");
            }
            if (maxSize < 0) {
                throw new IllegalArgumentException("Negative size specified (" + maxSize + ")");
            }
            this.maxTail = maxSize - 1;

            final int size = size();
            if (size > 0) {
                for (int i = 0; i < size; i++) {
                    nts[i] = nts[i + head];
                    foms[i] = foms[i + head];
                }
                head = 0;
                tail = Math.min(size, maxSize) - 1;
                Arrays.fill(foms, tail + 1, foms.length, Float.NEGATIVE_INFINITY);
            } else {
                Arrays.fill(foms, 0, foms.length, Float.NEGATIVE_INFINITY);
                head = -1;
                tail = 0;
            }
        }
    }

    public void clear(final int maxSize) {
        head = -1;
        tail = -1;
        maxTail = maxSize - 1;
    }

    /**
     * Returns the array index of the head (maximum-probability) entry.
     * 
     * @return the array index of the head (maximum-probability) entry.
     */
    public int headIndex() {
        return head;
    }

    /**
     * Returns the array index of the tail (minimum-probability) entry.
     * 
     * @return the array index of the tail (minimum-probability) entry.
     */
    public int tailIndex() {
        return tail;
    }

    /**
     * Removes the head (maximum-probability) entry.
     */
    public boolean popHead() {
        if (tail < 0 || tail < head) {
            return false;
        }

        head++;
        return true;
    }

    /**
     * Inserts an entry in the priority queue, if its figure-of-merit meets the current threshold (ejecting the lowest
     * item in the queue if the size bound is exceeded). Returns true if the parent was inserted into the queue and
     * false if the entry did not fit into the queue (i.e., the queue is full and the figure-of-merit was less than the
     * lowest queue entry).
     * 
     * @param nt
     * @param fom
     * @return true if the non-terminal was inserted into the queue
     */
    public boolean insert(final short nt, final float fom) {

        if (tail == maxTail) {
            // Ignore entries which are less probable than the minimum-priority entry
            if (fom <= foms[tail]) {
                return false;
            }
        } else {
            tail++;
        }

        if (head < 0) {
            head = 0;
        }

        foms[tail] = fom;
        nts[tail] = nt;

        // Bubble-sort the new entry into the queue
        sortUp(tail);

        return true;
    }

    protected void sortUp(final int entry) {
        for (int i = entry; i > head && foms[i - 1] < foms[i]; i--) {
            swap(i - 1, i);
        }
    }

    /**
     * Replaces the figure-of-merit for a parent if the new FOM is greater than the current FOM. Returns true if the
     * parent was found and replaced.
     * 
     * TODO Maintain a boolean array of NTs which are currently on the queue and skip the linear search for an NT which
     * isn't present?
     * 
     * @param nt
     * @param fom
     * @return True if the non-terminal was found and replaced.
     */
    public boolean replace(final short nt, final float fom) {
        // Short circuit if the queue is full and the FOM won't make the cut
        if (tail == maxTail && fom <= foms[maxTail]) {
            return false;
        }

        for (int i = head; i <= tail; i++) {
            if (nts[i] == nt) {
                if (fom > foms[i]) {
                    foms[i] = fom;
                    sortUp(i);
                    return true;
                }
                return false;
            }
        }
        return insert(nt, fom);
    }

    private void swap(final int i1, final int i2) {
        final float t1 = foms[i1];
        foms[i1] = foms[i2];
        foms[i2] = t1;

        final short t2 = nts[i1];
        nts[i1] = nts[i2];
        nts[i2] = t2;
    }

    public int size() {
        return head >= 0 ? tail - head + 1 : 0;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder(1024);
        if (head >= 0) {
            for (int i = head; i <= tail; i++) {
                if (grammar != null) {
                    sb.append(String.format("%s %.3f\n", grammar.mapNonterminal(nts[i]), foms[i]));
                } else {
                    sb.append(String.format("%d %.3f\n", nts[i], foms[i]));
                }
            }
        }
        return sb.toString();
    }
}
