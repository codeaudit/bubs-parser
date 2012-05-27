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

package edu.ohsu.cslu.dep;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.LinkedList;

import org.junit.Before;
import org.junit.Test;

import edu.ohsu.cslu.datastructs.vectors.SparseBitVector;
import edu.ohsu.cslu.dep.DependencyGraph.Arc;
import edu.ohsu.cslu.grammar.SymbolSet;

/**
 * @author Aaron Dunlop
 * @since May 24, 2012
 */
public class TestNivreParserFeatureExtractor {

    private final static String NULL_SYMBOL = "<null>";

    private SymbolSet<String> lexicon;
    private int lexiconSize;
    private SymbolSet<String> pos;
    private int posSetSize;

    private Arc[] arcs;

    @Before
    public void setUp() {
        lexicon = new SymbolSet<String>();
        lexicon.addSymbol(NULL_SYMBOL);
        lexicon.addSymbol("the");
        lexicon.addSymbol("dog");
        lexicon.addSymbol("barked");
        lexicon.addSymbol(DependencyGraph.ROOT.token);
        lexiconSize = lexicon.size();

        pos = new SymbolSet<String>();
        pos.addSymbol(NULL_SYMBOL);
        pos.addSymbol("DT");
        pos.addSymbol("NN");
        pos.addSymbol("VBD");
        pos.addSymbol(DependencyGraph.ROOT.pos);
        posSetSize = pos.size();

        arcs = new Arc[4];
        arcs[0] = new Arc("the", "DT", "DT", 1, 2, "_");
        arcs[1] = new Arc("dog", "NN", "NN", 2, 3, "_");
        arcs[2] = new Arc("barked", "VB", "VBD", 3, 0, "_");
        arcs[3] = DependencyGraph.ROOT;
    }

    /**
     * Simple examples using only unigram and bigram word features
     */
    @Test
    public void testWordFeatures() {

        NivreParserFeatureExtractor fe = new NivreParserFeatureExtractor("iw1,sw1,sw2_sw1", lexicon, pos);
        assertEquals(lexiconSize * 2 + lexiconSize * lexiconSize, fe.featureVectorLength);
        final LinkedList<Arc> stack = new LinkedList<Arc>();
        stack.push(arcs[0]);

        // Features when the stack contains 2 words
        SparseBitVector features = fe.forwardFeatureVector(new NivreParserContext(stack, arcs), 1);
        assertTrue(features.contains(lexicon.getIndex("dog")));
        assertTrue(features.contains(lexiconSize + lexicon.getIndex("the")));
        assertTrue(features.contains(lexiconSize * 2 + lexicon.getIndex(NULL_SYMBOL) * lexiconSize
                + lexicon.getIndex("the")));

        // A version when the stack contains 2 words
        stack.push(arcs[1]);
        features = fe.forwardFeatureVector(new NivreParserContext(stack, arcs), 2);
        assertTrue(features.contains(lexicon.getIndex("barked")));
        assertTrue(features.contains(lexiconSize + lexicon.getIndex("dog")));
        assertTrue(features.contains(lexiconSize * 2 + lexicon.getIndex("the") * lexiconSize + lexicon.getIndex("dog")));

        // A version when all words have been pushed onto the stack
        stack.push(arcs[2]);
        features = fe.forwardFeatureVector(new NivreParserContext(stack, arcs), 3);
        assertTrue(features.contains(lexicon.getIndex(DependencyGraph.ROOT.token)));
        assertTrue(features.contains(lexiconSize + lexicon.getIndex("barked")));
        assertTrue(features.contains(lexiconSize * 2 + lexicon.getIndex("dog") * lexiconSize
                + lexicon.getIndex("barked")));

        // And using lookahead into the buffer
        fe = new NivreParserFeatureExtractor("iw1,sw1,sw1_iw1", lexicon, pos);
        features = fe.forwardFeatureVector(new NivreParserContext(stack, arcs), 3);
        assertTrue(features.contains(lexicon.getIndex(DependencyGraph.ROOT.token)));
        assertTrue(features.contains(lexiconSize + lexicon.getIndex("barked")));
        assertTrue(features.contains(lexiconSize * 2 + lexicon.getIndex("barked") * lexiconSize
                + lexicon.getIndex(DependencyGraph.ROOT.token)));
    }

    /**
     * A simple example using unigram and bigram tag features
     */
    @Test
    public void testPosFeatures() {
        NivreParserFeatureExtractor fe = new NivreParserFeatureExtractor("it1,st1,st2_sw1", lexicon, pos);
        assertEquals(posSetSize * 2 + posSetSize * lexiconSize, fe.featureVectorLength);
        final LinkedList<Arc> stack = new LinkedList<Arc>();
        stack.push(arcs[0]);

        // Features when the stack contains only 1 word
        SparseBitVector features = fe.forwardFeatureVector(new NivreParserContext(stack, arcs), 1);
        assertTrue(features.contains(pos.getIndex("NN")));
        assertTrue(features.contains(posSetSize + pos.getIndex("DT")));
        assertTrue(features
                .contains(posSetSize * 2 + pos.getIndex(NULL_SYMBOL) * lexiconSize + lexicon.getIndex("the")));

        // A version when the stack contains 2 words
        stack.push(arcs[1]);
        features = fe.forwardFeatureVector(new NivreParserContext(stack, arcs), 2);
        assertTrue(features.contains(pos.getIndex("VBD")));
        assertTrue(features.contains(posSetSize + pos.getIndex("NN")));
        assertTrue(features.contains(posSetSize * 2 + pos.getIndex("DT") * lexiconSize + lexicon.getIndex("dog")));

        // A version when all words have been pushed onto the stack
        stack.push(arcs[2]);
        features = fe.forwardFeatureVector(new NivreParserContext(stack, arcs), 3);
        assertTrue(features.contains(pos.getIndex(DependencyGraph.ROOT.pos)));
        assertTrue(features.contains(posSetSize + pos.getIndex("VBD")));
        assertTrue(features.contains(posSetSize * 2 + pos.getIndex("NN") * lexiconSize + lexicon.getIndex("barked")));

        // And using lookahead into the buffer
        fe = new NivreParserFeatureExtractor("it1,st1,st1_it1", lexicon, pos);
        features = fe.forwardFeatureVector(new NivreParserContext(stack, arcs), 3);
        assertTrue(features.contains(pos.getIndex(DependencyGraph.ROOT.pos)));
        assertTrue(features.contains(posSetSize + pos.getIndex("VBD")));
        assertTrue(features.contains(posSetSize * 2 + pos.getIndex("VBD") * posSetSize
                + pos.getIndex(DependencyGraph.ROOT.pos)));
    }

    /**
     * A simple example using distance tag features
     */
    @Test
    public void testDistanceFeatures() {
        final NivreParserFeatureExtractor fe = new NivreParserFeatureExtractor("st2_st1,d", lexicon, pos);
        assertEquals(posSetSize * posSetSize + NivreParserFeatureExtractor.DISTANCE_BINS, fe.featureVectorLength);
        final LinkedList<Arc> stack = new LinkedList<Arc>();
        stack.push(arcs[0]);

        // Features when the stack contains only 1 word and the stack and input buffer aren't sequential (1 word has
        // already been reduced between the two)
        final SparseBitVector features = fe.forwardFeatureVector(new NivreParserContext(stack, arcs), 2);
        assertTrue(features.contains(pos.getIndex(NULL_SYMBOL) * posSetSize + pos.getIndex("DT")));
        assertTrue(features.contains(posSetSize * posSetSize + NivreParserFeatureExtractor.DISTANCE_2));
    }
}