package edu.ohsu.cslu.parser;

import java.util.Arrays;

import edu.ohsu.cslu.grammar.JsaSparseMatrixGrammar;
import edu.ohsu.cslu.grammar.Tokenizer.Token;
import edu.ohsu.cslu.parser.traversal.ChartTraversal.ChartTraversalType;

/**
 * SparseMatrixVectorParser implementation which uses a grammar stored in Java Sparse Array (JSA) format. Stores cell populations and cross-product densely, for efficient array
 * access and to avoid hashing (even though it's not quite as memory-efficient).
 * 
 * @author Aaron Dunlop
 * @since Jan 28, 2010
 * 
 * @version $Revision$ $Date$ $Author$
 */
public class JsaSparseMatrixVectorParser extends SparseMatrixVectorParser {

    private final JsaSparseMatrixGrammar jsaSparseMatrixGrammar;

    public JsaSparseMatrixVectorParser(final JsaSparseMatrixGrammar grammar, final ChartTraversalType traversalType) {
        super(grammar, traversalType);
        this.jsaSparseMatrixGrammar = grammar;
    }

    @Override
    protected void initParser(final int sentLength) {
        chartSize = sentLength;
        chart = new BaseChartCell[chartSize][chartSize + 1];

        // The chart is (chartSize+1)*chartSize/2
        for (int start = 0; start < chartSize; start++) {
            for (int end = start + 1; end < chartSize + 1; end++) {
                chart[start][end] = new JsaSparseVectorChartCell(chart, (JsaSparseMatrixGrammar) grammar, start, end);
            }
        }
        rootChartCell = chart[0][chartSize];
    }

    // TODO Do this with a matrix multiply?
    @Override
    protected void addLexicalProductions(final Token[] sent) throws Exception {
        super.addLexicalProductions(sent);
        for (int start = 0; start < chartSize; start++) {
            ((SparseVectorChartCell) chart[start][start + 1]).finalizeCell();
        }
    }

    @Override
    protected void visitCell(final ChartCell cell) {

        final JsaSparseVectorChartCell spvChartCell = (JsaSparseVectorChartCell) cell;
        // TODO Change ChartCell.start() and end() to return shorts (since we shouldn't have to handle sentences longer than 32767)
        final short start = (short) cell.start();
        final short end = (short) cell.end();

        final long t0 = System.currentTimeMillis();
        // int totalProducts = 0;

        final CrossProductVector crossProductVector = crossProductUnion(start, end);

        final long t1 = System.currentTimeMillis();
        final double crossProductTime = t1 - t0;

        // Multiply the unioned vector with the grammar matrix and populate the current cell with the
        // vector resulting from the matrix-vector multiplication
        spvChartCell.spmvMultiply(crossProductVector, jsaSparseMatrixGrammar.binaryRuleMatrix(), jsaSparseMatrixGrammar.binaryProbabilities());

        final long t2 = System.currentTimeMillis();
        final double binarySpmvTime = t2 - t1;

        // Handle unary productions
        // TODO: This only goes through unary rules one time, so it can't create unary chains unless such chains are encoded in the grammar. Iterating a few times would probably
        // work, although it's a big-time hack.
        spvChartCell.spmvMultiply(jsaSparseMatrixGrammar.unaryRuleMatrix(), jsaSparseMatrixGrammar.unaryProbabilities());

        final long t3 = System.currentTimeMillis();
        final double unarySpmvTime = t3 - t2;

        // TODO We won't need to do this once we're storing directly into the packed array
        spvChartCell.finalizeCell();

        // System.out.format("Visited cell: %2d,%2d (%5d ms). Cross-product: %6d/%6d combinations (%5.0f ms, %4.2f/ms), Multiply: %5d edges (%5.0f ms, %4.2f /ms)\n", start, end, t3
        // - t0, crossProductSize, totalProducts, crossProductTime, crossProductSize / crossProductTime, edges, spmvTime, edges / spmvTime);
        totalCrossProductTime += crossProductTime;
        totalSpMVTime += binarySpmvTime + unarySpmvTime;
    }

    public static class JsaSparseVectorChartCell extends SparseVectorChartCell {

        final JsaSparseMatrixGrammar jsaSparseMatrixGrammar;

        public JsaSparseVectorChartCell(final BaseChartCell[][] chart, final JsaSparseMatrixGrammar grammar, final int start, final int end) {
            super(chart, start, end, grammar);
            this.jsaSparseMatrixGrammar = grammar;

            final int arraySize = grammar.numNonTerms();
            this.probabilities = new float[arraySize];
            Arrays.fill(probabilities, Float.NEGATIVE_INFINITY);
            this.midpoints = new short[arraySize];
            this.children = new int[arraySize];

            // TODO: Set size to the actual number of populated non-terminals in spmvMultiply()
            // this.size = grammar.numNonTerms();
        }

        /**
         * Multiplies the grammar matrix (stored sparsely) by the supplied cross-product vector (stored densely), and populates this chart cell.
         * 
         * @param crossProductVector
         * @param grammarRuleMatrix
         * @param grammarProbabilities
         */
        public void spmvMultiply(final CrossProductVector crossProductVector, final int[][] grammarRuleMatrix, final float[][] grammarProbabilities) {

            final float[] crossProductProbabilities = crossProductVector.probabilities;
            final short[] crossProductMidpoints = crossProductVector.midpoints;

            // Iterate over possible parents
            for (int parent = 0; parent < jsaSparseMatrixGrammar.numNonTerms(); parent++) {

                final int[] grammarChildrenForParent = grammarRuleMatrix[parent];
                final float[] grammarProbabilitiesForParent = grammarProbabilities[parent];

                // Production winningProduction = null;
                float winningProbability = Float.NEGATIVE_INFINITY;
                int winningChildren = Integer.MIN_VALUE;
                short winningMidpoint = 0;

                for (int i = 0; i < grammarChildrenForParent.length; i++) {
                    final int grammarChildren = grammarChildrenForParent[i];

                    final float grammarProbability = grammarProbabilitiesForParent[i];
                    final float crossProductProbability = crossProductProbabilities[grammarChildren];
                    final float jointProbability = grammarProbability + crossProductProbability;

                    if (jointProbability > winningProbability) {
                        winningProbability = jointProbability;
                        winningChildren = grammarChildren;
                        winningMidpoint = crossProductMidpoints[grammarChildren];
                    }
                }

                if (winningProbability != Float.NEGATIVE_INFINITY) {
                    this.children[parent] = winningChildren;
                    this.probabilities[parent] = winningProbability;
                    this.midpoints[parent] = winningMidpoint;
                }
            }
        }

        /**
         * Multiplies the grammar matrix (stored sparsely) by the contents of this cell (stored densely), and populates this chart cell. Used to populate unary rules.
         * 
         * @param grammarRuleMatrix
         * @param grammarProbabilities
         */
        public void spmvMultiply(final int[][] grammarRuleMatrix, final float[][] grammarProbabilities) {

            // Iterate over possible parents
            for (int parent = 0; parent < jsaSparseMatrixGrammar.numNonTerms(); parent++) {
                final int[] grammarChildrenForParent = grammarRuleMatrix[parent];
                final float[] grammarProbabilitiesForParent = grammarProbabilities[parent];

                // Production winningProduction = null;
                float winningProbability = this.probabilities[parent];
                int winningChildren = Integer.MIN_VALUE;
                short winningMidpoint = 0;

                for (int i = 0; i < grammarChildrenForParent.length; i++) {
                    final int packedChildren = grammarChildrenForParent[i];
                    final int child = jsaSparseMatrixGrammar.unpackLeftChild(packedChildren);

                    final float grammarProbability = grammarProbabilitiesForParent[i];
                    final float crossProductProbability = this.probabilities[child];
                    final float jointProbability = grammarProbability + crossProductProbability;

                    if (jointProbability > winningProbability) {
                        winningProbability = jointProbability;
                        winningChildren = packedChildren;
                        winningMidpoint = (short) end();
                    }
                }

                if (winningChildren != Integer.MIN_VALUE) {
                    this.children[parent] = winningChildren;
                    this.probabilities[parent] = winningProbability;
                    this.midpoints[parent] = winningMidpoint;
                }
            }
        }
    }
}