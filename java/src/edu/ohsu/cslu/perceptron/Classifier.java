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

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Serializable;

import edu.ohsu.cslu.datastructs.vectors.BitVector;
import edu.ohsu.cslu.datastructs.vectors.Vector;

public abstract class Classifier implements Serializable {

    private static final long serialVersionUID = 1L;

    protected int bins[];

    public abstract void train(final int goldClass, final BitVector featureVector);

    public abstract short classify(final Vector featureVector);

    public abstract float computeLoss(final int goldClass, final int guessClass);

    public abstract void writeModel(BufferedWriter stream) throws IOException;

    public abstract String getFeatureTemplate();

    // TODO Remove this if we retire PerceptronBeamWidthModel and BinaryPerceptronSet
    public abstract void setBias(final String biasString);

    public int numClasses() {
        return bins.length + 1;
    }

    public float class2value(final int c) {
        if (c == numClasses() - 1) {
            return Integer.MAX_VALUE;
        }
        return bins[c];
    }

    public int value2class(final float value) {
        for (int i = 0; i < bins.length; i++) {
            if (value <= bins[i]) {
                return i;
            }
        }
        return numClasses() - 1;
    }
}
