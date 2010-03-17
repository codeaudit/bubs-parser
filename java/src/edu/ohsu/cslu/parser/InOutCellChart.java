package edu.ohsu.cslu.parser;

import java.util.Arrays;

import edu.ohsu.cslu.parser.util.ParserUtil;

public class InOutCellChart extends CellChart {

    public InOutCellChart(final int size, final boolean viterbiMax, final Parser parser) {
        super(size, viterbiMax, parser);

        chart = new ChartCell[size][size + 1];
        for (int start = 0; start < size; start++) {
            for (int end = start + 1; end < size + 1; end++) {
                chart[start][end] = new ChartCell(start, end);
            }
        }
    }

    @Override
    public ChartCell getCell(final int start, final int end) {
        return (ChartCell) chart[start][end];
    }

    public class ChartCell extends edu.ohsu.cslu.parser.CellChart.ChartCell {

        float outside[];

        public ChartCell(final int start, final int end) {
            super(start, end);
            outside = new float[parser.grammar.numNonTerms()];
            Arrays.fill(outside, Float.NEGATIVE_INFINITY);

            if (start == 0 && end == size()) {
                outside[parser.grammar.startSymbol] = 0; // log(1)
            }
        }

        public float getOutside(final int nt) {
            return outside[nt];
        }

        public void updateOutside(final int nt, final float outsideProb) {
            if (viterbiMax) {
                if (outsideProb > outside[nt]) {
                    outside[nt] = outsideProb;
                }
            } else {
                outside[nt] = (float) ParserUtil.logSum(outside[nt], outsideProb);
            }
        }
    }
}