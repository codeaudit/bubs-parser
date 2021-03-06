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
package edu.ohsu.cslu.hash;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;

public class BasicIntPair2IntHash implements ImmutableIntPair2IntHash {

    // TODO Vary the shift

    private static final long serialVersionUID = 1L;

    private final Int2IntOpenHashMap hash;

    public BasicIntPair2IntHash(final int[][] keyPairs) {
        hash = new Int2IntOpenHashMap();
        for (int i = 0; i < keyPairs[0].length; i++) {
            final int packedKey = keyPairs[0][i] << 16 | keyPairs[1][i];
            hash.put(packedKey, packedKey);
        }
        hash.defaultReturnValue(Integer.MIN_VALUE);
    }

    @Override
    public int hashcode(final int key1, final int key2) {
        return unsafeHashcode(key1, key2);
    }

    @Override
    public int unsafeHashcode(final int key1, final int key2) {
        return hash.get(key1 << 16 | key2);
    }

    @Override
    public int key1(final int hashcode) {
        return unsafeKey1(hashcode);
    }

    @Override
    public int unsafeKey1(final int hashcode) {
        final int value = hash.get(hashcode);
        return value < 0 ? Integer.MIN_VALUE : value >> 16;
    }

    @Override
    public int key2(final int hashcode) {
        return unsafeKey2(hashcode);
    }

    @Override
    public int unsafeKey2(final int hashcode) {
        final int value = hash.get(hashcode);
        return value < 0 ? Integer.MIN_VALUE : value & 0xffff;
    }

}
