package edu.ohsu.cslu.math.linear;

import org.junit.Before;

/**
 * Unit tests for {@link PackedBitVector}.
 * 
 * @author Aaron Dunlop
 * @since Sep 11, 2008
 * 
 *        $Id$
 */
public class TestPackedBitVector extends BitVectorTestCase
{
    @Override
    @Before
    public void setUp() throws Exception
    {
        StringBuilder sb = new StringBuilder();
        sb.append("vector type=packed-bit length=35\n");
        sb.append("1 0 1 1 1 0 0 1 1 0 1 1 1 0 1 0 0 0 1 0 1 1 0 1 1 1 1 0 0 1 0 0 1 1 1\n");
        stringSampleVector = sb.toString();

        int[] sampleArray = new int[] {1, 0, 1, 1, 1, 0, 0, 1, 1, 0, 1, 1, 1, 0, 1, 0, 0, 0, 1, 0, 1, 1, 0, 1, 1, 1, 1,
                                       0, 0, 1, 0, 0, 1, 1, 1};
        sampleVector = new PackedBitVector(sampleArray);

        vectorClass = PackedBitVector.class;
    }

    @Override
    protected BitVector createEmptyBitVector()
    {
        return new PackedBitVector(16);
    }
}
