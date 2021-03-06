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

package edu.ohsu.cslu.perceptron;

/**
 * Represents a sequence of (possibly-tagged) tokens.
 */
public interface MulticlassSequence extends Sequence {

    public short goldClass(final int position);

    public short predictedClass(final int position);

    public short[] predictedClasses();

    public void setPredictedClass(final int position, final short newClass);

    /**
     * Returns an ordinal value associated with this instance. Used to break out error evaluation by classes (other than
     * the gold class). Optional operation.
     * 
     * @return an ordinal value associated with this instance
     * @throws UnsupportedOperationException if not supported by this implementation
     */
    public String ordinalValue();
}
