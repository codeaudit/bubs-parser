/*
 * Copyright 2010, 2011 Aaron Dunlop and Nathan Bodenstab
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
package edu.ohsu.cslu.lela;

import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import cltool4j.GlobalConfigProperties;
import edu.ohsu.cslu.datastructs.narytree.NaryTree;
import edu.ohsu.cslu.datastructs.narytree.NaryTree.Factorization;
import edu.ohsu.cslu.grammar.Grammar;
import edu.ohsu.cslu.grammar.GrammarFormatType;
import edu.ohsu.cslu.grammar.LeftCscSparseMatrixGrammar;
import edu.ohsu.cslu.grammar.SparseMatrixGrammar;
import edu.ohsu.cslu.lela.ProductionListGrammar.NoiseGenerator;
import edu.ohsu.cslu.lela.TrainGrammar.EmIterationResult;
import edu.ohsu.cslu.parser.ParseTask;
import edu.ohsu.cslu.parser.Parser;
import edu.ohsu.cslu.parser.ParserDriver;
import edu.ohsu.cslu.parser.ml.CartesianProductHashSpmlParser;
import edu.ohsu.cslu.tests.JUnit;
import edu.ohsu.cslu.util.Evalb.BracketEvaluator;
import edu.ohsu.cslu.util.Evalb.EvalbResult;
import edu.ohsu.cslu.util.Strings;

/**
 * Unified tests for training of a split-merge grammar.
 * 
 * @author Aaron Dunlop
 * @since Mar 9, 2011
 */
public class TestTrainGrammar {

    /**
     * Learns a 2-split grammar from a 1-sentence sample corpus. Verifies that likelihood increases with successive EM
     * runs.
     * 
     * @throws IOException
     */
    @Test
    public void testSample() throws IOException {
        testEmTraining(new BufferedReader(new StringReader(AllLelaTests.STRING_SAMPLE_TREE)));
    }

    /**
     * Learns a 2-split grammar from short 1-sentence 'corpus'. Verifies that corpus likelihood increases with
     * successive EM runs.
     * 
     * @throws IOException
     */
    @Test
    public void testSentence1() throws IOException {
        final BufferedReader br = new BufferedReader(new StringReader(
                "(TOP (S (NP (NNP FCC) (NN counsel)) (VP (VBZ joins) (NP (NN firm))) (: :)))"), 1024);

        testEmTraining(br);
    }

    /**
     * Learns a 2-split grammar from short 1-sentence 'corpus'. Verifies that corpus likelihood increases with
     * successive EM runs.
     * 
     * @throws IOException
     */
    @Test
    public void testSentence2() throws IOException {
        final BufferedReader br = new BufferedReader(new StringReader(
                "(TOP (X (X (SYM z)) (: -) (ADJP (RB Not) (JJ available)) (. .)))"), 1024);

        testEmTraining(br);
    }

    /**
     * Learns a 2-split grammar from longer 1-sentence 'corpus'. Verifies that corpus likelihood increases with
     * successive EM runs.
     * 
     * @throws IOException
     */
    @Test
    public void testSentence3() throws IOException {
        final BufferedReader br = new BufferedReader(
                new StringReader(
                        "(TOP (S (S (CC But) (NP (NNS investors)) (VP (MD should) (VP (VB keep) (PP (IN in) (NP (NN mind))) (, ,) (PP (IN before) (S (VP (VBG paying) (ADVP (RB too) (JJ much))))) (, ,) (SBAR (IN that) (S (NP (NP (DT the) (JJ average) (JJ annual) (NN return)) (PP (IN for) (NP (NN stock) (NNS holdings)))) (, ,) (ADVP (JJ long-term)) (, ,) (VP (AUX is) (NP (NP (QP (CD 9) (NN %) (TO to) (CD 10) (NN %))) (NP (DT a) (NN year))))))))) (: ;) (S (NP (NP (DT a) (NN return)) (PP (IN of) (NP (CD 15) (NN %)))) (VP (AUX is) (VP (VBN considered) (S (ADJP (JJ praiseworthy)))))) (. .)))"),
                1024);

        testEmTraining(br);
    }

    /**
     * Learns a 2-split grammar from 2-sentence 'corpus'. Verifies that corpus likelihood increases with successive EM
     * runs.
     * 
     * @throws IOException
     */
    @Test
    public void testSentence1Twice() throws IOException {
        final BufferedReader br = new BufferedReader(new StringReader(
                "(TOP (S (NP (NNP FCC) (NN counsel)) (VP (VBZ joins) (NP (NN firm))) (: :)))\n"
                        + "(TOP (S (NP (NNP FCC) (NN counsel)) (VP (VBZ joins) (NP (NN firm))) (: :)))"), 1024);

        testEmTraining(br);
    }

    /**
     * Learns a 2-split grammar from 2-sentence 'corpus'. Verifies that corpus likelihood increases with successive EM
     * runs.
     * 
     * @throws IOException
     */
    @Test
    public void testSentence2Twice() throws IOException {
        final BufferedReader br = new BufferedReader(new StringReader(
                "(TOP (X (X (SYM z)) (: -) (ADJP (RB Not) (JJ available)) (. .)))\n"
                        + "(TOP (X (X (SYM z)) (: -) (ADJP (RB Not) (JJ available)) (. .)))"), 1024);

        testEmTraining(br);
    }

    /**
     * Learns a 2-split grammar from 2-sentence 'corpus'. Verifies that corpus likelihood increases with successive EM
     * runs.
     * 
     * @throws IOException
     */
    @Test
    public void testSentences1and2() throws IOException {
        final BufferedReader br = new BufferedReader(new StringReader(
                "(TOP (S (NP (NNP FCC) (NN counsel)) (VP (VBZ joins) (NP (NN firm))) (: :)))\n"
                        + "(TOP (X (X (SYM z)) (: -) (ADJP (RB Not) (JJ available)) (. .)))"), 1024);

        testEmTraining(br);
    }

    /**
     * Learns a 2-split grammar from 3-sentence 'corpus'. Verifies that corpus likelihood increases with successive EM
     * runs.
     * 
     * @throws IOException
     */
    @Test
    public void test3Sentences() throws IOException {
        GlobalConfigProperties.singleton().setProperty(Parser.PROPERTY_MAX_BEAM_WIDTH, "50");
        final BufferedReader br = new BufferedReader(
                new StringReader(
                        "(TOP (S (NP (NNP FCC) (NN counsel)) (VP (VBZ joins) (NP (NN firm))) (: :)))\n"
                                + "(TOP (X (X (SYM z)) (: -) (ADJP (RB Not) (JJ available)) (. .)))\n"
                                + "(TOP (S (S (CC But) (NP (NNS investors)) (VP (MD should) (VP (VB keep) (PP (IN in) (NP (NN mind))) (, ,) (PP (IN before) (S (VP (VBG paying) (ADVP (RB too) (JJ much))))) (, ,) (SBAR (IN that) (S (NP (NP (DT the) (JJ average) (JJ annual) (NN return)) (PP (IN for) (NP (NN stock) (NNS holdings)))) (, ,) (ADVP (JJ long-term)) (, ,) (VP (AUX is) (NP (NP (QP (CD 9) (NN %) (TO to) (CD 10) (NN %))) (NP (DT a) (NN year))))))))) (: ;) (S (NP (NP (DT a) (NN return)) (PP (IN of) (NP (CD 15) (NN %)))) (VP (AUX is) (VP (VBN considered) (S (ADJP (JJ praiseworthy)))))) (. .)))"),
                4096);

        testEmTraining(br);
    }

    private void testEmTraining(final BufferedReader trainingCorpusReader) throws IOException {
        final TrainGrammar tg = new TrainGrammar();
        tg.factorization = Factorization.RIGHT;
        tg.grammarFormatType = GrammarFormatType.Berkeley;

        trainingCorpusReader.mark(20 * 1024 * 1024);

        final ProductionListGrammar plg0 = tg.induceGrammar(trainingCorpusReader);
        trainingCorpusReader.reset();

        tg.loadGoldTreesAndCharts(trainingCorpusReader, plg0);

        // Parse the training 'corpus' with induced grammar and report F-score
        final long t0 = System.currentTimeMillis();
        System.out.format("Initial F-score: %.3f  Time: %.1f seconds\n", parseFScore(cscGrammar(plg0), tg.goldTrees),
                (System.currentTimeMillis() - t0) / 1000f);

        // TODO Remove fixed random seed
        final NoiseGenerator noiseGenerator = new ProductionListGrammar.RandomNoiseGenerator(0.01f, 1);

        // Split and train with the 1-split grammar
        final ProductionListGrammar plg1 = runEm(tg, plg0.split(noiseGenerator), 1, 25);

        // Split again and train with the 2-split grammar
        tg.reloadGoldTreesAndCharts(cscGrammar(plg1));
        runEm(tg, plg1.split(noiseGenerator), 2, 25);
    }

    /**
     * Learns a 2-split grammar from a small corpus (WSJ section 24). Verifies that corpus likelihood increases with
     * successive EM runs.
     * 
     * @throws IOException
     */
    @Test
    public void testWithoutMerging() throws IOException {
        final TrainGrammar tg = new TrainGrammar();
        tg.factorization = Factorization.RIGHT;
        tg.grammarFormatType = GrammarFormatType.Berkeley;

        // final BufferedReader br = new BufferedReader(
        // new StringReader(
        // "(TOP (S (NP (NNP FCC) (NN COUNSEL)) (VP (VBZ JOINS) (NP (NN FIRM))) (: :)))\n"
        // + "(TOP (X (X (SYM z)) (: -) (ADJP (RB Not) (JJ available)) (. .)))\n"
        // +
        // "(TOP (S (S (CC But) (NP (NNS investors)) (VP (MD should) (VP (VB keep) (PP (IN in) (NP (NN mind))) (, ,) (PP (IN before) (S (VP (VBG paying) (ADVP (RB too) (JJ much))))) (, ,) (SBAR (IN that) (S (NP (NP (DT the) (JJ average) (JJ annual) (NN return)) (PP (IN for) (NP (NN stock) (NNS holdings)))) (, ,) (ADVP (JJ long-term)) (, ,) (VP (AUX is) (NP (NP (QP (CD 9) (NN %) (TO to) (CD 10) (NN %))) (NP (DT a) (NN year))))))))) (: ;) (S (NP (NP (DT a) (NN return)) (PP (IN of) (NP (CD 15) (NN %)))) (VP (AUX is) (VP (VBN considered) (S (ADJP (JJ praiseworthy)))))) (. .)))"),
        // 1024);

        final BufferedReader br = new BufferedReader(JUnit.unitTestDataAsReader("corpora/wsj/wsj_24.mrgEC.gz"),
                20 * 1024 * 1024);
        br.mark(20 * 1024 * 1024);

        final ProductionListGrammar plg0 = tg.induceGrammar(br);
        br.reset();

        tg.loadGoldTreesAndCharts(br, plg0);

        // TODO re-enable initial parse
        // Parse the training corpus with induced grammar and report F-score
        System.out.println("Initial F-score: .630");
        // final long t0 = System.currentTimeMillis();
        // System.out.format("Initial F-score: %.3f  Time: %.1f seconds\n", parseFScore(csrGrammar(plg0), tg.goldTrees),
        // (System.currentTimeMillis() - t0) / 1000f);

        final NoiseGenerator noiseGenerator = new ProductionListGrammar.RandomNoiseGenerator(0.01f);

        // Split and train with the 1-split grammar
        final ProductionListGrammar plg1 = runEm(tg, plg0.split(noiseGenerator), 1, 25);
        // final FileWriter fw = new FileWriter("/tmp/1split");
        // fw.write(plg1.toString());
        // fw.close();

        // Split again and train with the 2-split grammar
        runEm(tg, plg1.split(noiseGenerator), 2, 25);
    }

    private ProductionListGrammar runEm(final TrainGrammar tg, final ProductionListGrammar plg, final int split,
            final int iterations) {

        ConstrainedInsideOutsideGrammar cscGrammar = cscGrammar(plg);

        final int lexiconSize = cscGrammar.lexSet.size();
        float previousCorpusLikelihood = Float.NEGATIVE_INFINITY;
        ArrayList<ConstrainedChart> previousCharts = null;
        ProductionListGrammar previousGrammar = null;

        final long t0 = System.currentTimeMillis();

        EmIterationResult result = null;
        // final ProductionListGrammar originalMergedGrammar = mergeAllSplits(plg);
        // final int originalMergedRules = originalMergedGrammar.binaryProductions.size()
        // + originalMergedGrammar.unaryProductions.size() + originalMergedGrammar.lexicalProductions.size();

        for (int i = 0; i < iterations; i++) {

            result = tg.emIteration(cscGrammar, -8f);
            result.plGrammar.verifyProbabilityDistribution();
            cscGrammar = cscGrammar(result.plGrammar);

            // Re-merge all splits and verify that the resulting grammar hasn't lost any rules from the original
            // final ProductionListGrammar mergedGrammar = mergeAllSplits(result.plGrammar);
            // final int mergedGrammarRules = mergedGrammar.binaryProductions.size()
            // + mergedGrammar.unaryProductions.size() + mergedGrammar.lexicalProductions.size();
            // if (mergedGrammarRules != originalMergedRules) {
            // System.out.println("Missing rules. Expected " + originalMergedRules + " but was " + mergedGrammarRules);
            // final ConstrainedInsideOutsideGrammar previousCscGrammar = cscGrammar(previousGrammar);
            // final ParserDriver opts = new ParserDriver();
            // opts.cellSelectorModel = ConstrainedCellSelector.MODEL;
            // final ConstrainedInsideOutsideParser parser = new ConstrainedInsideOutsideParser(opts,
            // previousCscGrammar);
            // parser.findBestParse(tg.constrainingCharts.get(0));
            // parser.countRuleOccurrences();
            // }

            // Ensure we have rules matching each lexical entry
            for (int j = 0; j < lexiconSize; j++) {
                if (cscGrammar.getLexicalProductionsWithChild(j).size() == 0) {
                    System.out.println("Iteration " + (i + 1) + " : Missing parent for "
                            + cscGrammar.lexSet.getSymbol(j));
                }
                assertTrue("Iteration " + (i + 1) + " : No parents found for " + cscGrammar.lexSet.getSymbol(j),
                        cscGrammar.getLexicalProductionsWithChild(j).size() > 0);
            }

            System.out.format("=== Split %d, iteration %d   Likelihood: %.3f\n", split, i + 1, result.corpusLikelihood);

            if (i > 1) {
                // Allow a small delta on corpus likelihood comparison to avoid floating-point errors
                assertTrue(String.format("Corpus likelihood declined from %.2f to %.2f on iteration %d",
                        previousCorpusLikelihood, result.corpusLikelihood, i + 1), result.corpusLikelihood
                        - previousCorpusLikelihood >= -.001f);
            }
            previousCorpusLikelihood = result.corpusLikelihood;
            previousGrammar = result.plGrammar;
            previousCharts = result.charts;
        }

        final long t1 = System.currentTimeMillis();
        System.out.format("Training Time: %.1f seconds\n", (t1 - t0) / 1000f);

        // Parse the training corpus with the new CSC grammar and report F-score
        System.out.format("F-score: %.2f\n", parseFScore(cscGrammar, tg.goldTrees) * 100,
                (System.currentTimeMillis() - t1) / 1000f);

        return result.plGrammar;
    }

    private ProductionListGrammar mergeAllSplits(final ProductionListGrammar plg) {
        final short[] mergeIndices = new short[plg.vocabulary.size() / 2];
        for (short j = 0, k = 1; j < mergeIndices.length; j++, k += 2) {
            mergeIndices[j] = k;
        }
        return plg.merge(mergeIndices);
    }

    private ConstrainedInsideOutsideGrammar cscGrammar(final ProductionListGrammar plg) {
        return new ConstrainedInsideOutsideGrammar(plg.binaryProductions, plg.unaryProductions, plg.lexicalProductions,
                plg.vocabulary, plg.lexicon, GrammarFormatType.Berkeley,
                SparseMatrixGrammar.PerfectIntPairHashPackingFunction.class, plg);
    }

    private double parseFScore(final Grammar grammar, final List<NaryTree<String>> goldTrees) {
        final LeftCscSparseMatrixGrammar cscGrammar = new LeftCscSparseMatrixGrammar(grammar);
        final CartesianProductHashSpmlParser parser = new CartesianProductHashSpmlParser(new ParserDriver(), cscGrammar);

        final BracketEvaluator evaluator = new BracketEvaluator();
        for (final NaryTree<String> goldTree : goldTrees) {
            // Extract tokens from training tree, parse, and evaluate
            // TODO Parse from tree instead
            final String sentence = Strings.join(goldTree.leafLabels(), " ");
            final ParseTask context = parser.parseSentence(sentence);
            // try {
            // final FileWriter fw = new FileWriter("/tmp/chart");
            // fw.write(parser.chart.toString());
            // fw.close();
            // } catch (final IOException e) {
            // e.printStackTrace();
            // }
            evaluator.evaluate(goldTree, context.binaryParse.unfactor(cscGrammar.grammarFormat));
        }

        final EvalbResult evalbResult = evaluator.accumulatedResult();
        return evalbResult.f1();
    }
}
