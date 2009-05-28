package edu.ohsu.cslu.alignment.column;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntListIterator;

import java.util.Arrays;
import java.util.LinkedList;

import edu.ohsu.cslu.alignment.AlignmentVocabulary;
import edu.ohsu.cslu.alignment.pairwise.SequenceAlignment;
import edu.ohsu.cslu.common.LogLinearMappedSequence;
import edu.ohsu.cslu.common.MappedSequence;
import edu.ohsu.cslu.common.MultipleVocabularyMappedSequence;
import edu.ohsu.cslu.common.Sequence;
import edu.ohsu.cslu.common.Vocabulary;
import edu.ohsu.cslu.datastructs.vectors.BitVector;
import edu.ohsu.cslu.datastructs.vectors.IntVector;
import edu.ohsu.cslu.datastructs.vectors.Vector;

/**
 * Traditional dynamic-programming sequence alignment algorithm.
 * 
 * @author Aaron Dunlop
 * @since Jun 24, 2008
 * 
 *        $Id$
 */
public class FullColumnAligner extends BaseColumnAligner
{
    /** Store the dynamic programming information for toString() to format */
    private float[][] m_array;
    // TODO: Replace with a PackedIntVector and profile
    protected byte[][] m_backPointer;
    private Sequence m_sequence;
    private ColumnAlignmentModel m_model;

    @Override
    public SequenceAlignment align(MappedSequence sequence, ColumnAlignmentModel model, int[] features)
    {
        final int maxI = sequence.length() + 1;
        final int maxJ = model.columns() + 1;

        final float[][] array = new float[maxI][maxJ];
        final byte[][] backpointer = new byte[maxI][maxJ];

        final Vector gapVector = model.gapVector();

        m_array = array;
        m_backPointer = backpointer;
        m_sequence = sequence;
        m_model = model;

        for (int i = 0; i < maxI; i++)
        {
            Arrays.fill(array[i], Float.POSITIVE_INFINITY);
        }
        array[0][0] = 0.0f;

        // Initialize all the 'start' columns - probabilities of gaps all the way through
        for (int i = 1; i < maxI; i++)
        {
            array[i][0] = array[i - 1][0] + model.columnInsertionCost(sequence.elementAt(i - 1));
            backpointer[i][0] = BACKPOINTER_INSERT_COLUMN;
        }
        for (int j = 1; j < maxJ; j++)
        {
            array[0][j] = array[0][j - 1] + model.cost(gapVector, j - 1, features);
        }
        Arrays.fill(backpointer[0], BACKPOINTER_UNALIGNED_GAP);

        // Choose min of (emit, gap in unaligned sequence, insert column)
        // Emit should be i-1, j-1, gap in unaligned sequence i, j-1, insert column i-1,j
        for (int i = 1; i < maxI; i++)
        {
            final int prevI = i - 1;
            final Vector currentElement = sequence.elementAt(prevI);
            final byte[] backpointerI = backpointer[i];

            for (int j = 1; j < maxJ; j++)
            {
                final int prevJ = j - 1;

                // Probability of emission / gap in unaligned / insert column
                final float emit = array[prevI][prevJ] + model.cost(currentElement, prevJ, features);
                final float gapInNewSequence = array[i][prevJ] + model.cost(gapVector, prevJ, features);
                final float gapInPssm = array[prevI][j] + model.columnInsertionCost(currentElement);

                // Bias toward emission given equal probabilities
                if (emit <= gapInPssm && emit <= gapInNewSequence)
                {
                    array[i][j] = emit;
                    backpointerI[j] = BACKPOINTER_SUBSTITUTION;
                }
                else if (gapInNewSequence <= gapInPssm)
                {
                    array[i][j] = gapInNewSequence;
                    backpointerI[j] = BACKPOINTER_UNALIGNED_GAP;
                }
                else
                {
                    array[i][j] = gapInPssm;
                    backpointerI[j] = BACKPOINTER_INSERT_COLUMN;
                }
            }
        }

        return backtrace(sequence, model, backpointer, gapVector);
    }
    
    @Override
    public SequenceAlignment alignWithGaps(MappedSequence sequence, MatrixColumnAlignmentModel model, int[] features)
    {
        final int maxI = sequence.length() + 1;
        final int maxJ = model.columns() + 1;

        final float[][] array = new float[maxI][maxJ];
        final byte[][] backpointer = new byte[maxI][maxJ];

        final Vocabulary[] vocabularies = model.vocabularies();
        final Vector gapSymbol = new IntVector(vocabularies.length);
        for (int i = 0; i < vocabularies.length; i++)
        {
            gapSymbol.set(i, ((AlignmentVocabulary) vocabularies[i]).gapSymbol());
        }
        
        m_array = array;
        m_backPointer = backpointer;
        m_sequence = sequence;
        m_model = model;

        for (int i = 0; i < maxI; i++)
        {
            Arrays.fill(array[i], Float.MAX_VALUE);
        }
        array[0][0] = 0.0f;

        // Initialize all the 'start' columns - probabilities of gaps all the way through
        for (int i = 1; i < maxI; i++)
        {
            //array[i][0] = array[i - 1][0] + model.costOfInsertingAGapIntoThisAlignmentModel/*_reflexive*/(sequence.features(i - 1));
            array[i][0] = array[i - 1][0] + model.costOfInsertingAGapIntoThisAlignmentModel_reflexive(sequence.elementAt(i - 1));
            backpointer[i][0] = BACKPOINTER_INSERT_COLUMN;
        }
        for (int j = 1; j < maxJ; j++)
        {
            array[0][j] = array[0][j - 1] + model.cost(gapSymbol, j - 1, features);
        }
        Arrays.fill(backpointer[0], BACKPOINTER_UNALIGNED_GAP);

        // Choose min of (emit, gap in unaligned sequence, gap in pssm)
        // Emit should be i-1, j-1, gap in unaligned sequence i, j-1, gap in pssm i-1,j
        for (int i = 1; i < maxI; i++)
        {
            final int prevI = i - 1;
            final Vector currentElement = sequence.elementAt(prevI);
            final byte[] backpointerI = backpointer[i];

            for (int j = 1; j < maxJ; j++)
            {
                final int prevJ = j - 1;

                // Probability of emission / gap in unaligned / gap in pssm
                final float emit = array[prevI][prevJ] + model.cost(currentElement, prevJ, features);
                final float gapInNewSequence = array[i][prevJ] + model.cost(gapSymbol, prevJ, features);
                //final float gapInPssm = array[prevI][j] + model.costOfInsertingAGapIntoThisAlignmentModel/*_reflexive*/(currentElement);
                final float gapInPssm = array[prevI][j] + model.costOfInsertingAGapIntoThisAlignmentModel_reflexive(currentElement);

                // Bias toward emission given equal probabilities
                if (emit <= gapInPssm && emit <= gapInNewSequence)
                {
                    array[i][j] = emit;
                    backpointerI[j] = BACKPOINTER_SUBSTITUTION;
                }
                else if (gapInNewSequence <= gapInPssm)
                {
                    array[i][j] = gapInNewSequence;
                    backpointerI[j] = BACKPOINTER_UNALIGNED_GAP;
                }
                else
                {
                    array[i][j] = gapInPssm;
                    backpointerI[j] = BACKPOINTER_INSERT_COLUMN;
                }
            }
        }

        System.out.println();
        System.out.println(this);
        
        return backtraceWithGaps(sequence, model, backpointer, gapSymbol);
    }


    protected final SequenceAlignment backtrace(Sequence sequence, ColumnAlignmentModel model,
        byte[][] backPointer, Vector gapVector)
    {
        // Backtrace to reconstruct the winning alignment, adding to the beginning of a linked-list
        // to implicitly construct the buffer in order, while we traverse the alignment in reverse.
        final LinkedList<Vector> backtrace = new LinkedList<Vector>();
        final IntList gapList = new IntArrayList(sequence.length());

        int i = sequence.length();
        int j = model.columns();

        while (i > 0 || j > 0)
        {
            switch (backPointer[i][j])
            {
                case BACKPOINTER_SUBSTITUTION :
                    // Emit
                    backtrace.addFirst(sequence.elementAt(--i));
                    j--;
                    break;
                case BACKPOINTER_UNALIGNED_GAP :
                    // Gap in unaligned sequence
                    backtrace.addFirst(gapVector);
                    j--;
                    break;
                case BACKPOINTER_INSERT_COLUMN :
                    // Gap in PSSM
                    backtrace.addFirst(sequence.elementAt(--i));
                    gapList.add(j);
                    break;
                default :
                    throw new IllegalArgumentException("Should never get here");
            }
        }

        // Reverse gapList
        final int[] gapIndices = new int[gapList.size()];
        final IntListIterator gapIterator = gapList.listIterator();
        for (i = gapIndices.length - 1; i >= 0; i--)
        {
            gapIndices[i] = gapIterator.nextInt();
        }

        if (sequence instanceof MultipleVocabularyMappedSequence)
        {
            return new SequenceAlignment(new MultipleVocabularyMappedSequence(backtrace.toArray(new IntVector[backtrace
                .size()]), model.vocabularies()), gapIndices);
        }
        return new SequenceAlignment(new LogLinearMappedSequence(backtrace.toArray(new BitVector[backtrace.size()]),
            model.vocabularies()[0]), gapIndices);
    }
    
    protected final SequenceAlignment backtraceWithGaps(MappedSequence sequence, ColumnAlignmentModel model,
        byte[][] backPointer, Vector gapVector)
    {
        // Backtrace to reconstruct the winning alignment, adding to the beginning of a linked-list
        // to implicitly construct the buffer in order, while we traverse the alignment in reverse.
        final LinkedList<Vector> buffer = new LinkedList<Vector>();
        final IntList gapList = new IntArrayList(sequence.length());

        int i = sequence.length();
        int j = model.columns();

        while (i > 0 || j > 0)
        {
            switch (backPointer[i][j])
            {
                case BACKPOINTER_SUBSTITUTION :
                    // Emit
                    buffer.addFirst(sequence.elementAt(--i));
                    j--;
                    break;
                case BACKPOINTER_UNALIGNED_GAP :
                    // Gap in unaligned sequence
                    buffer.addFirst(gapVector);
                    j--;
                    break;
                case BACKPOINTER_INSERT_COLUMN :
                    // Gap in PSSM
                    buffer.addFirst(sequence.elementAt(--i));
                    gapList.add(j);
                    break;
                default :
                    throw new IllegalArgumentException("Should never get here");
            }
        }

        // Reverse gapList
        final int[] gapIndices = new int[gapList.size()];
        final IntListIterator gapIterator = gapList.listIterator();
        for (i = gapIndices.length - 1; i >= 0; i--)
        {
            gapIndices[i] = gapIterator.nextInt();
        }

        return new SequenceAlignment(new LogLinearMappedSequence(buffer.toArray(new BitVector[buffer.size()]), model.vocabularies()[0]),
            gapIndices);
    }

    @Override
    public String toString()
    {
        int maxI = m_sequence.length() + 1;
        int maxJ = m_model.columns() + 1;

        Vocabulary vocabulary = m_model.vocabularies()[0];

        // TODO: Take length of mapped tokens into account in formatting

        StringBuffer sb = new StringBuffer(1024);
        sb.append("       ");
        for (int j = 0; j < maxJ; j++)
        {
            sb.append(String.format("%11d |", j));
        }
        sb.append('\n');
        for (int i = 0; i < maxI; i++)
        {
            sb.append(String.format("%5s |", i > 0 ? vocabulary.map(m_sequence.elementAt(i - 1).getInt(0)) : ""));
            for (int j = 0; j < maxJ; j++)
            {
                float value = m_array[i][j];
                String backpointer = null;
                switch (m_backPointer[i][j])
                {
                    case BACKPOINTER_SUBSTITUTION :
                        backpointer = "\\";
                        break;
                    case BACKPOINTER_UNALIGNED_GAP :
                        backpointer = "<";
                        break;
                    case BACKPOINTER_INSERT_COLUMN :
                        backpointer = "^";
                        break;
                }
                sb.append(String.format(value > 500000 ? " %s      Max |" : " %s %8.2f |", backpointer, value));
            }
            sb.append('\n');
        }
        return sb.toString();
    }
}
