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

import java.util.HashMap;
import java.util.Map;

import edu.ohsu.cslu.grammar.LeftHashGrammar;
import edu.ohsu.cslu.grammar.Production;
import edu.ohsu.cslu.parser.ChartParser;
import edu.ohsu.cslu.parser.ParserDriver;
import edu.ohsu.cslu.parser.chart.CellChart;
import edu.ohsu.cslu.parser.chart.CellChart.HashSetChartCell;
import edu.ohsu.cslu.parser.chart.Chart.ChartCell;

/**
 * Exhaustive chart parser which performs grammar intersection by iterating over grammar rules matching the observed
 * child pairs in the cartesian product of non-terminals observed in child cells.
 * 
 * @author Nathan Bodenstab
 */
public class ECPCellCrossHashGrammarLoop2 extends ChartParser<LeftHashGrammar, CellChart> {

    HashMap<Integer, Container> childHash;

    public ECPCellCrossHashGrammarLoop2(final ParserDriver opts, final LeftHashGrammar grammar) {
        super(opts, grammar);
    }

    @Override
    protected void computeInsideProbabilities(final ChartCell c) {
        final long t0 = collectDetailedStatistics ? System.nanoTime() : 0;

        final HashSetChartCell cell = (HashSetChartCell) c;
        final short start = cell.start();
        final short end = cell.end();
        float insideProb;

        childHash = new HashMap<Integer, Container>();
        final int numNT = grammar.numNonTerms();

        for (int mid = start + 1; mid <= end - 1; mid++) { // mid point
            final HashSetChartCell leftCell = chart.getCell(start, mid);
            final HashSetChartCell rightCell = chart.getCell(mid, end);
            for (final int leftNT : leftCell.getLeftChildNTs()) {
                final float leftIn = leftCell.getInside(leftNT);
                for (final int rightNT : rightCell.getRightChildNTs()) {
                    final float rightIn = rightCell.getInside(rightNT);
                    final int key = leftNT * numNT + rightNT;
                    final Container old = childHash.get(key);
                    if (old == null) {
                        childHash.put(key, new Container(leftCell, rightCell, leftIn + rightIn));
                    } else if (old.score < leftIn + rightIn) {
                        old.left = leftCell;
                        old.right = rightCell;
                        old.score = leftIn + rightIn;
                    }
                }
            }
        }

        for (final Map.Entry<Integer, Container> entry : childHash.entrySet()) {
            final int leftNT = entry.getKey() / numNT;
            final int rightNT = entry.getKey() % numNT;
            for (final Production p : grammar.getBinaryProductionsWithChildren(leftNT, rightNT)) {
                insideProb = p.prob + entry.getValue().score;
                cell.updateInside(p, entry.getValue().left, entry.getValue().right, insideProb);
            }
        }

        if (collectDetailedStatistics) {
            chart.parseTask.insideBinaryNs += System.nanoTime() - t0;
        }

        for (final int childNT : cell.getNtArray()) {
            for (final Production p : grammar.getUnaryProductionsWithChild(childNT)) {
                cell.updateInside(p, p.prob + cell.getInside(childNT));
            }
        }
    }

    private class Container {

        public HashSetChartCell left, right;
        public float score;

        public Container(final HashSetChartCell left, final HashSetChartCell right, final float score) {
            this.left = left;
            this.right = right;
            this.score = score;
        }
    }
}
