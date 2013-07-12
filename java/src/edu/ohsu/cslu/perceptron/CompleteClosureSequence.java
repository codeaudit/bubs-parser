/*
 * Copyright 2010-2012 Aaron Dunlop and Nathan Bodenstab
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
 * along with the BUBS Parser. If not, see <http://www.gnu.org/licenses/>
 */

package edu.ohsu.cslu.perceptron;

import java.util.Arrays;

import edu.ohsu.cslu.datastructs.narytree.BinaryTree;
import edu.ohsu.cslu.datastructs.narytree.NaryTree;
import edu.ohsu.cslu.datastructs.narytree.NaryTree.Binarization;
import edu.ohsu.cslu.grammar.GrammarFormatType;
import edu.ohsu.cslu.grammar.SymbolSet;

/**
 * Represents a sequence of tokens and POS tags, intended for making open/closed decisions on chart cells.
 * 
 * @author Aaron Dunlop
 */
public class CompleteClosureSequence extends ConstituentBoundarySequence implements BinarySequence {

    // These fields are populated in the constructor, but possibly in a subclass constructor, so we can't label them
    // final
    // TODO We could replace these with PackedBitVectors to save a little space
    protected boolean[] classes;
    protected boolean[] predictedClasses;

    /**
     * Constructs from an array of tokens, mapped according to the classifier's lexicon. Used during inference.
     * 
     * @param mappedTokens
     * @param posTags
     * @param classifier
     */
    public CompleteClosureSequence(final int[] mappedTokens, final short[] posTags,
            final CompleteClosureClassifier classifier) {

        super(mappedTokens, posTags, classifier.lexicon, classifier.decisionTreeUnkClassSet);

        // All cells spanning more than one word
        this.length = sentenceLength * (sentenceLength + 1) / 2 - sentenceLength;
        this.classes = null;
        // this.classes = new boolean[length];
        // Arrays.fill(classes, true);
        this.predictedClasses = new boolean[length];
        Arrays.fill(predictedClasses, true);
    }

    /**
     * Constructs from a bracketed tree, populating {@link #classes} with open/closed classifications for each chart
     * cell.
     * 
     * @param parseTree
     * @param binarization
     * @param grammarFormatType
     * @param lexicon
     * @param unkClassSet
     * @param vocabulary
     */
    public CompleteClosureSequence(final String parseTree, final Binarization binarization,
            final GrammarFormatType grammarFormatType, final SymbolSet<String> lexicon,
            final SymbolSet<String> unkClassSet, final SymbolSet<String> vocabulary) {

        this(NaryTree.read(parseTree.trim(), String.class).binarize(grammarFormatType, binarization), lexicon,
                unkClassSet, vocabulary);
    }

    /**
     * Constructs from a bracketed tree, populating {@link #classes} with open/closed classifications for each chart
     * cell.
     * 
     * @param parseTree
     * @param classifier
     */
    public CompleteClosureSequence(final BinaryTree<String> parseTree, final CompleteClosureClassifier classifier) {
        this(parseTree, classifier.lexicon, classifier.decisionTreeUnkClassSet, classifier.vocabulary);
    }

    /**
     * Constructs from a bracketed tree, populating {@link #classes} with open/closed classifications for each chart
     * cell.
     * 
     * @param parseTree
     * @param lexicon
     * @param unkClassSet
     * @param vocabulary
     */
    private CompleteClosureSequence(final BinaryTree<String> parseTree, final SymbolSet<String> lexicon,
            final SymbolSet<String> unkClassSet, final SymbolSet<String> vocabulary) {

        super(parseTree, lexicon, unkClassSet, vocabulary);

        this.classes = new boolean[length];
        this.predictedClasses = new boolean[length];

        Arrays.fill(classes, true);

        // Populate classes and predictedClasses - true for each open cell
        // Iterate over the tree, marking each populated chart cell. In the case of unary productions, we'll
        // re-populate the same cell, but that's easier than trying to prevent it.
        int start = 0;
        for (final BinaryTree<String> node : parseTree.preOrderTraversal()) {

            if (node.isLeaf()) {
                if (vocabulary.isFinalized()) {
                    posTags[start] = (short) vocabulary.getIndex(node.parentLabel());
                } else {
                    posTags[start] = (short) vocabulary.addSymbol(node.parentLabel());
                }
                // Increment the start index every time we process a leaf
                start++;
            } else if (node.leaves() > 1) {
                final int span = node.leaves();
                final int index = ConstituentBoundaryFeatureExtractor.cellIndex(start, start + span, sentenceLength,
                        true);
                classes[index] = false;
            }
        }
    }

    public final boolean predictedClass(final int i) {
        return classes[i];
    }

    @Override
    public void setPredictedClass(final int i, final boolean classification) {
        classes[i] = classification;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder(256);
        for (int i = 0; i < sentenceLength; i++) {
            sb.append(lexicon.getSymbol(mappedTokens[i]));
            sb.append(' ');
            sb.append(classes[i]);
            sb.append(')');

            if (i < (length - 1)) {
                sb.append(' ');
            }
        }
        sb.append('\n');

        for (int i = 0; i < length; i++) {
            final short[] startAndEnd = ConstituentBoundaryFeatureExtractor.startAndEnd(i, sentenceLength, true);

            sb.append(String.format("%d,%d %s  ", startAndEnd[0], startAndEnd[1], classes[i] ? "T" : "F"));
        }
        return sb.toString();
    }
}
