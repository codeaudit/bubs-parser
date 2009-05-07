package edu.ohsu.cslu.alignment;

import java.io.Serializable;

import edu.ohsu.cslu.common.MultipleVocabularyMappedSequence;
import edu.ohsu.cslu.common.Sequence;
import edu.ohsu.cslu.datastructs.matrices.FloatMatrix;
import edu.ohsu.cslu.datastructs.matrices.Matrix;
import edu.ohsu.cslu.datastructs.vectors.IntVector;
import edu.ohsu.cslu.datastructs.vectors.Vector;

/**
 * Implements the {@link SubstitutionAlignmentModel} interfaces using a {@link Matrix} of
 * probabilities.
 * 
 * @author Aaron Dunlop
 * @since Oct 8, 2008
 * 
 *        $Id$
 */
public class MatrixSubstitutionAlignmentModel implements SubstitutionAlignmentModel, Serializable
{
    private final Matrix[] matrices;
    private final AlignmentVocabulary[] vocabularies;
    protected final IntVector gapVector;

    public MatrixSubstitutionAlignmentModel(Matrix[] matrices, AlignmentVocabulary[] vocabularies)
    {
        this.matrices = matrices;
        this.vocabularies = vocabularies;
        this.gapVector = new IntVector(matrices.length, 0);
    }

    public MatrixSubstitutionAlignmentModel(Matrix matrix, AlignmentVocabulary vocabulary)
    {
        this(new Matrix[] {matrix}, new AlignmentVocabulary[] {vocabulary});
    }

    public MatrixSubstitutionAlignmentModel(float[] substitutionCosts, float[] gapCosts,
        AlignmentVocabulary[] vocabularies)
    {
        this.vocabularies = vocabularies;
        this.matrices = new Matrix[substitutionCosts.length];
        this.gapVector = new IntVector(matrices.length, 0);

        initializeIdentityMatrices(substitutionCosts, gapCosts, vocabularies);
    }

    public MatrixSubstitutionAlignmentModel(float substitutionCost, float gapCost, AlignmentVocabulary[] vocabularies)
    {
        this.vocabularies = vocabularies;
        this.matrices = new Matrix[vocabularies.length];
        this.gapVector = new IntVector(matrices.length, 0);

        float[] substitutionCosts = new float[vocabularies.length];
        float[] gapCosts = new float[vocabularies.length];
        for (int i = 0; i < substitutionCosts.length; i++)
        {
            substitutionCosts[i] = substitutionCost;
            gapCosts[i] = gapCost;
        }
        initializeIdentityMatrices(substitutionCosts, gapCosts, vocabularies);
    }

    private void initializeIdentityMatrices(float[] substitutionCosts, float[] gapCosts, AlignmentVocabulary[] vocab)
    {
        for (int m = 0; m < substitutionCosts.length; m++)
        {
            final int size = vocab[m].size();
            matrices[m] = new FloatMatrix(size, size, false);

            for (int i = 0; i < size; i++)
            {
                for (int j = 0; j < size; j++)
                {
                    matrices[m].set(i, j, (i == j) ? 0 : substitutionCosts[m]);
                }
            }

            matrices[m].setRow(0, gapCosts[m]);
            matrices[m].setColumn(0, gapCosts[m]);
            matrices[m].set(0, 0, 0);
        }
    }

    @Override
    public final AlignmentVocabulary[] vocabularies()
    {
        return vocabularies;
    }

    @Override
    public float cost(final int alignedFeature, final int unalignedFeature)
    {
        return matrices[0].getFloat(alignedFeature, unalignedFeature);
    }

    @Override
    public float gapInsertionCost(int feature, int sequenceLength)
    {
        // cost does not depend on sequence length

        // TODO: This assumes that the cost of aligning a feature with a gap is reflexive (that is,
        // that the cost is the same regardless of whether the gap is in the existing alignment or
        // in the new sequence)
        return cost(GAP_INDEX, feature);
    }

    @Override
    public float cost(final Vector alignedVector, final Vector unalignedVector)
    {
        float cost = 0f;
        for (int i = 0; i < matrices.length; i++)
        {
            cost += matrices[i].getFloat(alignedVector.getInt(i), unalignedVector.getInt(i));
        }
        return cost;
    }

    @Override
    public float gapInsertionCost(Vector featureVector, int sequenceLength)
    {
        // cost does not depend on sequence length

        // TODO: This assumes that the cost of aligning a feature with a gap is reflexive (that is,
        // the same regardless of whether the gap is in the existing alignment or in the new
        // sequence)
        return cost(gapVector, featureVector);
    }

    @Override
    public final int features()
    {
        return matrices.length;
    }

    @Override
    public Sequence createSequence(Vector[] elements)
    {
        return new MultipleVocabularyMappedSequence(elements, vocabularies);
    }

    @Override
    public Vector gapVector()
    {
        return new IntVector(new int[vocabularies.length]);
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder(8096);
        for (int m = 0; m < matrices.length; m++)
        {
            Matrix matrix = matrices[m];
            if (matrix.rows() <= 100)
            {
                AlignmentVocabulary vocabulary = vocabularies[m];

                sb.append("       |");
                for (int j = 0; j < matrix.columns(); j++)
                {
                    sb.append(String.format(" %5s |", vocabulary.map(j)));
                }
                sb.append('\n');

                for (int i = 0; i < matrix.rows(); i++)
                {
                    sb.append(String.format(" %5s |", vocabulary.map(i)));
                    for (int j = 0; j < matrix.columns(); j++)
                    {
                        sb.append(String.format(" %5.2f |", matrix.getFloat(i, j)));
                    }
                    sb.append('\n');
                }
            }
            else
            {
                sb.append(String.format("Matrix of %d rows", matrix.rows()));
            }
        }
        return sb.toString();
    }
}
