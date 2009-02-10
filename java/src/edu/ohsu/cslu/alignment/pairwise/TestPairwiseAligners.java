package edu.ohsu.cslu.alignment.pairwise;

import org.junit.BeforeClass;
import org.junit.Test;

import edu.ohsu.cslu.alignment.AlignmentModel;
import edu.ohsu.cslu.alignment.AlignmentVocabulary;
import edu.ohsu.cslu.alignment.MatrixSubstitutionAlignmentModel;
import edu.ohsu.cslu.alignment.SubstitutionAlignmentModel;
import edu.ohsu.cslu.alignment.bio.DnaVocabulary;
import edu.ohsu.cslu.math.linear.FloatMatrix;
import edu.ohsu.cslu.math.linear.Matrix;

import static junit.framework.Assert.assertEquals;

/**
 * Unit tests for pairwise aligners.
 * 
 * @author Aaron Dunlop
 * @since Oct 8, 2008
 * 
 *        $Id$
 */
public class TestPairwiseAligners
{
    private final static DnaVocabulary DNA_VOCABULARY = new DnaVocabulary();

    private static SubstitutionAlignmentModel identityMatrixModel;

    @BeforeClass
    public static void suiteSetUp() throws Exception
    {
        Matrix alignmentMatrix = new FloatMatrix(new float[][] { {0f, 8f, 8f, 8f, 8f, 8f},
                                                                {8f, 0f, 10f, 10f, 10f, 10f},
                                                                {8f, 10f, 0f, 10f, 10f, 10f},
                                                                {8f, 10f, 10f, 0f, 10f, 10f},
                                                                {8f, 10f, 10f, 10f, 0f, 10f},
                                                                {8f, 10f, 10f, 10f, 10f, 0f}});
        identityMatrixModel = new MatrixSubstitutionAlignmentModel(alignmentMatrix, DNA_VOCABULARY);
    }

    @Test
    public void testFixedLengthDynamicAligner() throws Exception
    {
        FixedLengthDynamicAligner aligner = new FixedLengthDynamicAligner();

        assertEquals("AC--AC", alignStrings(aligner, identityMatrixModel, "ACAC", "ACT-AC"));
        assertEquals("-CTG-", alignStrings(aligner, identityMatrixModel, "CTG", "ACTGA"));
        assertEquals("ACXGA", alignStrings(aligner, identityMatrixModel, "ACXGA", "ACTGA"));

        // We expect a clearly-wrong alignment, since this aligner disallows adding columns
        assertEquals("ACTGA", alignStrings(aligner, identityMatrixModel, "ACTGA", "CTGAC"));
    }

    private String alignStrings(PairwiseAligner aligner, AlignmentModel model, String unaligned, String aligned)
    {
        return DNA_VOCABULARY.mapSequence(aligner.alignPair(DNA_VOCABULARY.mapSequence(unaligned),
            DNA_VOCABULARY.mapSequence(aligned), model).alignedSequence());
    }

    @Test
    public void testVariableLengthDynamicAligner() throws Exception
    {
        VariableLengthDynamicAligner aligner = new VariableLengthDynamicAligner();
        assertEquals("AC--AC", alignStrings(aligner, identityMatrixModel, "ACAC", "ACT-AC"));
        assertEquals("-CTG-", alignStrings(aligner, identityMatrixModel, "CTG", "ACTGA"));

        // Variable length aligner can handle an unaligned string longer than the aligned string
        assertEquals("ACTGA", alignStrings(aligner, identityMatrixModel, "ACTGA", "CTG"));

        assertEquals("ACXGA", alignStrings(aligner, identityMatrixModel, "ACXGA", "ACTGA"));

        assertEquals("ACTGA-", alignStrings(aligner, identityMatrixModel, "ACTGA", "CTGAC"));
        assertEquals("-CTGAC", alignStrings(aligner, identityMatrixModel, "CTGAC", "ACTGA"));

        SequenceAlignment alignment = aligner.alignPair(DNA_VOCABULARY.mapSequence("ACTGA"), DNA_VOCABULARY
            .mapSequence("CTGACT"), identityMatrixModel);
        assertEquals("ACTGA--", DNA_VOCABULARY.mapSequence(alignment.alignedSequence()));
        assertEquals(1, alignment.gapIndices().length);
        assertEquals(0, alignment.gapIndices()[0]);

        alignment = aligner.alignPair(DNA_VOCABULARY.mapSequence("CTGACT"), DNA_VOCABULARY.mapSequence("AACTGAC"),
            identityMatrixModel);
        assertEquals("--CTGACT", DNA_VOCABULARY.mapSequence(alignment.alignedSequence()));
        assertEquals(1, alignment.gapIndices().length);
        assertEquals(7, alignment.gapIndices()[0]);

        alignment = aligner.alignPair(DNA_VOCABULARY.mapSequence("CTGGACT"), DNA_VOCABULARY.mapSequence("ACTGAC"),
            identityMatrixModel);
        assertEquals("-CTGGACT", DNA_VOCABULARY.mapSequence(alignment.alignedSequence()));
        assertEquals(2, alignment.gapIndices().length);
        assertEquals(3, alignment.gapIndices()[0]);
        assertEquals(6, alignment.gapIndices()[1]);
        assertEquals(" A | C | T | - | G | A | C | - |", DNA_VOCABULARY.mapSequence("ACTGAC").insertGaps(
            alignment.gapIndices()).toString());

        // Now test using SixCharacterAlignmentModel, which penalizes gap insertion in sequences
        // longer than 6 elements
        SixCharacterAlignmentModel model = new SixCharacterAlignmentModel(10f, DNA_VOCABULARY);
        // We expect 'normal' alignments with 5 and 6-character strings
        assertEquals("TAACG-", alignStrings(aligner, model, "TAACG", "TACG-"));
        assertEquals("TAACGG", alignStrings(aligner, model, "TAACGG", "TCACGG"));

        // And we expect bad alignments for longer strings - TAACG-X / T-ACGG-X would be a much
        // better alignment (and is in fact the alignment we expect from the more sensible model)
        assertEquals("TAACG-X", alignStrings(aligner, identityMatrixModel, "TAACGX", "TACG-X"));
        assertEquals("TAACGX", alignStrings(aligner, model, "TAACGX", "TACG-X"));
    }

    private static class SixCharacterAlignmentModel extends MatrixSubstitutionAlignmentModel
    {
        public SixCharacterAlignmentModel(float substitutionCost, AlignmentVocabulary vocabulary)
        {
            super(new float[] {substitutionCost}, new float[] {substitutionCost},
                new AlignmentVocabulary[] {vocabulary});
        }

        @Override
        public float gapInsertionCost(int feature, int sequenceLength)
        {
            float cost = super.cost(GAP_INDEX, feature);
            if (sequenceLength >= 6)
            {
                // Severely penalize gap insertion when the sequence is 6 or more elements long
                cost += 200f;
            }
            return cost;
        }

        @Override
        public float gapInsertionCost(int[] featureVector, int sequenceLength)
        {
            float cost = super.cost(gapVector, featureVector);
            if (sequenceLength >= 6)
            {
                // Severely penalize gap insertion when the sequence is 6 or more elements long
                cost += 200f;
            }
            return cost;
        }
    }
}
