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
package edu.ohsu.cslu.parser.ecp;

import java.util.LinkedList;
import java.util.List;

import edu.ohsu.cslu.grammar.ChildMatrixGrammar;
import edu.ohsu.cslu.grammar.Production;
import edu.ohsu.cslu.parser.ChartParser;
import edu.ohsu.cslu.parser.ParserDriver;
import edu.ohsu.cslu.parser.chart.CellChart;
import edu.ohsu.cslu.parser.chart.CellChart.HashSetChartCell;
import edu.ohsu.cslu.parser.chart.Chart.ChartCell;

/**
 * Exhaustive chart parser which performs grammar intersection by iterating over grammar rules matching the observed
 * non-terminals in the left child child pairs in the cartesian product of non-terminals observed in child cells.
 * 
 * @author Nathan Bodenstab
 */
public class ECPCellCrossMatrix extends ChartParser<ChildMatrixGrammar, CellChart> {

    public ECPCellCrossMatrix(final ParserDriver opts, final ChildMatrixGrammar grammar) {
        super(opts, grammar);
    }

    @Override
    protected void computeInsideProbabilities(final ChartCell c) {
        final long t0 = collectDetailedStatistics ? System.nanoTime() : 0;

        final HashSetChartCell cell = (HashSetChartCell) c;
        final short start = cell.start();
        final short end = cell.end();

        for (int mid = start + 1; mid <= end - 1; mid++) { // mid point
            final HashSetChartCell leftCell = chart.getCell(start, mid);
            final HashSetChartCell rightCell = chart.getCell(mid, end);
            for (final int leftNT : leftCell.getLeftChildNTs()) {
                // gramByLeft = GrammarMatrix.binaryProdMatrix.get(leftEdge.p.parent);
                final LinkedList<Production>[] gramByLeft = grammar.binaryProdMatrix[leftNT];
                for (final int rightNT : rightCell.getRightChildNTs()) {
                    // validProductions = gramByLeft.get(rightEdge.p.parent);
                    final List<Production> validProductions = gramByLeft[rightNT];
                    if (validProductions != null) {
                        for (final Production p : validProductions) {
                            final float prob = p.prob + leftCell.getInside(leftNT) + rightCell.getInside(rightNT);
                            cell.updateInside(p, leftCell, rightCell, prob);
                        }
                    }
                }
            }
        }

        if (collectDetailedStatistics) {
            chart.parseTask.insideBinaryNs += System.nanoTime() - t0;
        }

        for (final int childNT : cell.getNtArray()) {
            for (final Production p : grammar.getUnaryProductionsWithChild(childNT)) {
                cell.updateInside(chart.new ChartEdge(p, cell));
            }
        }
    }
}
