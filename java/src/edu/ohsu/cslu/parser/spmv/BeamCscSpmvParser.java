package edu.ohsu.cslu.parser.spmv;

import java.util.Arrays;

import edu.ohsu.cslu.grammar.LeftCscSparseMatrixGrammar;
import edu.ohsu.cslu.parser.ParserDriver;
import edu.ohsu.cslu.parser.chart.BoundedPriorityQueue;
import edu.ohsu.cslu.parser.chart.PackedArrayChart.PackedArrayChartCell;

public class BeamCscSpmvParser extends CscSpmvParser {
    private final int beamWidth;
    private final int lexicalRowBeamWidth;
    private final int lexicalRowUnarySlots;

    public BeamCscSpmvParser(final ParserDriver opts, final LeftCscSparseMatrixGrammar grammar) {
        super(opts, grammar);
        beamWidth = opts.param1 > 0 ? (int) opts.param1 : grammar.numNonTerms();
        if (opts.param2 > 0) {
            lexicalRowBeamWidth = (int) opts.param2;
            lexicalRowUnarySlots = (int) (lexicalRowBeamWidth * (opts.param3 > 0 ? opts.param3 : 0.3f));
        } else {
            lexicalRowBeamWidth = grammar.numNonTerms();
            lexicalRowUnarySlots = 0;
        }
    }

    @Override
    protected void initParser(final int[] tokens) {
        initParser(tokens, beamWidth, lexicalRowBeamWidth);
    }

    @Override
    protected void visitCell(final short start, final short end) {

        final PackedArrayChartCell spvChartCell = chart.getCell(start, end);

        final long t0 = System.currentTimeMillis();
        long t1 = t0;
        long t2 = t0;

        // Skip binary grammar intersection for span-1 cells
        if (end - start > 1) {
            final CartesianProductVector cartesianProductVector = cartesianProductUnion(start, end);

            if (collectDetailedStatistics) {
                totalCartesianProductSize += cartesianProductVector.size();
                t1 = System.currentTimeMillis();
                totalCartesianProductTime += (t1 - t0);
            }

            // Multiply the unioned vector with the grammar matrix and populate the current cell with the
            // vector resulting from the matrix-vector multiplication
            binarySpmv(cartesianProductVector, spvChartCell);
        }

        if (collectDetailedStatistics) {
            t2 = System.currentTimeMillis();
            totalBinarySpMVTime += (t2 - t1);
        }

        /*
         * Populate the chart cell with the most probable n edges (n = beamWidth).
         * 
         * This operation depends on 3 data structures:
         * 
         * A) The temporary edge storage already populated with binary inside probabilities and (viterbi) backpointers
         * 
         * B) A bounded priority queue of non-terminal indices, prioritized by their figure-of-merit scores
         * 
         * C) A parallel array of edges. We will pop a limited number of edges off the priority queue into this array,
         * so this storage represents the actual cell population.
         * 
         * First, we push all binary edges onto the priority queue (if we're pruning significantly, most will not make
         * the queue). We then begin popping edges off the queue. With each edge popped, we 1) Add the edge to the array
         * of cell edges (C); and 2) Iterate through unary grammar rules with the edge parent as a child, inserting any
         * resulting unary edges to the queue. This insertion replaces the existing queue entry for the parent
         * non-terminal, if greater, and updates the inside probability and backpointer in (A).
         */

        // Push all binary or lexical edges onto a bounded priority queue
        final int cellBeamWidth = (end - start == 1 ? lexicalRowBeamWidth : beamWidth);
        final BoundedPriorityQueue q = new BoundedPriorityQueue(cellBeamWidth, grammar);

        final float[] tmpFoms = new float[grammar.numNonTerms()];
        Arrays.fill(tmpFoms, Float.NEGATIVE_INFINITY);

        if (end - start == 1) {
            for (short nt = 0; nt < grammar.numNonTerms(); nt++) {
                if (spvChartCell.tmpInsideProbabilities[nt] != Float.NEGATIVE_INFINITY) {
                    final float fom = edgeSelector.calcLexicalFOM(start, end, nt,
                            spvChartCell.tmpInsideProbabilities[nt]);
                    q.insert(nt, fom);
                    tmpFoms[nt] = fom;
                }
            }
            // Truncate the tail and reserve a few entries for unary productions
            q.setMaxSize(lexicalRowBeamWidth - lexicalRowUnarySlots);
            q.setMaxSize(lexicalRowBeamWidth);

        } else {
            for (short nt = 0; nt < grammar.numNonTerms(); nt++) {
                if (spvChartCell.tmpInsideProbabilities[nt] != Float.NEGATIVE_INFINITY) {
                    final float fom = edgeSelector.calcFOM(start, end, nt, spvChartCell.tmpInsideProbabilities[nt]);
                    q.insert(nt, fom);
                    tmpFoms[nt] = fom;
                }
            }
        }

        final int[] cellPackedChildren = new int[grammar.numNonTerms()];
        final float[] cellInsideProbabilities = new float[grammar.numNonTerms()];
        Arrays.fill(cellInsideProbabilities, Float.NEGATIVE_INFINITY);
        final float[] cellFoms = new float[grammar.numNonTerms()];
        Arrays.fill(cellFoms, Float.NEGATIVE_INFINITY);
        final short[] cellMidpoints = new short[grammar.numNonTerms()];

        // Pop edges off the queue until we fill the beam width. With each non-terminal popped off the queue, push
        // unary edges for each unary grammar rule with the non-terminal as a child
        for (int edgesPopulated = 0; edgesPopulated < cellBeamWidth && q.size() > 0;) {

            final int headIndex = q.headIndex();
            final short nt = q.parentIndices[headIndex];
            final float fom = q.foms[headIndex];
            q.popHead();

            if (cellFoms[nt] == Float.NEGATIVE_INFINITY) {
                cellPackedChildren[nt] = spvChartCell.tmpPackedChildren[nt];
                cellInsideProbabilities[nt] = spvChartCell.tmpInsideProbabilities[nt];
                cellFoms[nt] = fom;
                cellMidpoints[nt] = spvChartCell.tmpMidpoints[nt];

                // Insert all unary edges with the current parent as child into the queue
                final int child = nt;

                // Iterate over possible parents of the child (rows with non-zero entries)
                for (int i = grammar.cscUnaryColumnOffsets[child]; i < grammar.cscUnaryColumnOffsets[child + 1]; i++) {

                    final short parent = grammar.cscUnaryRowIndices[i];
                    final float jointProbability = grammar.cscUnaryProbabilities[i]
                            + spvChartCell.tmpInsideProbabilities[child];
                    final float parentFom = edgeSelector.calcFOM(start, end, parent, jointProbability);

                    if (parentFom > tmpFoms[parent] && parentFom > cellFoms[parent] && q.replace(parent, parentFom)) {
                        // The FOM was high enough that the edge was added to the queue; update temporary storage
                        // (A) to reflect the new unary child and probability
                        spvChartCell.tmpPackedChildren[parent] = grammar.cartesianProductFunction().packUnary(child);
                        spvChartCell.tmpInsideProbabilities[parent] = jointProbability;
                    }
                }

                edgesPopulated++;

            } else if (fom > cellFoms[nt]) {
                // We just re-popped a non-terminal we've already seen (meaning a unary which was added to the
                // queue). Replace the existing edge with the new unary edge.
                cellPackedChildren[nt] = spvChartCell.tmpPackedChildren[nt];
                cellInsideProbabilities[nt] = spvChartCell.tmpInsideProbabilities[nt];
                cellMidpoints[nt] = end;
                cellFoms[nt] = fom;
            }
        }

        spvChartCell.finalizeCell(cellPackedChildren, cellInsideProbabilities, cellMidpoints);
    }

}