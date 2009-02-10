package edu.ohsu.cslu.tests;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

import junit.framework.Assert;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

import edu.ohsu.cslu.alignment.AllAlignmentTests;
import edu.ohsu.cslu.common.AllCommonTests;
import edu.ohsu.cslu.counters.TestCoocurrenceCounter;
import edu.ohsu.cslu.matching.ProfileMatchers;
import edu.ohsu.cslu.matching.approximate.TestApproximateMatchers;
import edu.ohsu.cslu.matching.exact.TestExactMatchers;
import edu.ohsu.cslu.math.AllMathTests;
import edu.ohsu.cslu.narytree.AllTreeTests;
import edu.ohsu.cslu.parsing.grammar.TestStringGrammar;
import edu.ohsu.cslu.tools.AllToolTests;
import edu.ohsu.cslu.util.AllUtilTests;

@RunWith(Suite.class)
@Suite.SuiteClasses( {AllCommonTests.class, TestExactMatchers.class, TestApproximateMatchers.class,
                      ProfileMatchers.class, AllAlignmentTests.class, TestStringGrammar.class,
                      TestCoocurrenceCounter.class, AllMathTests.class, AllTreeTests.class, AllToolTests.class,
                      AllUtilTests.class})
public class SharedNlpTests
{
    public final static String UNIT_TEST_DIR = "unit-test-data/";

    public static InputStream unitTestDataAsStream(String filename) throws IOException
    {
        InputStream is = new FileInputStream(SharedNlpTests.UNIT_TEST_DIR + filename);
        if (filename.endsWith(".gz"))
        {
            is = new GZIPInputStream(is);
        }
        return is;
    }

    public static byte[] readUnitTestData(String filename) throws IOException
    {
        InputStream is = new FileInputStream(SharedNlpTests.UNIT_TEST_DIR + filename);
        if (filename.endsWith(".gz"))
        {
            is = new GZIPInputStream(is);
        }
        return readUnitTestData(is);
    }

    private static byte[] readUnitTestData(InputStream is) throws IOException
    {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(1024);
        byte[] buf = new byte[1024];
        for (int i = is.read(buf); i >= 0; i = is.read(buf))
        {
            bos.write(buf, 0, i);
        }
        is.close();
        return bos.toByteArray();
    }

    public static void assertEquals(String message, float[] expected, float[] actual, float delta)
    {
        Assert.assertEquals(message, expected.length, actual.length);
        for (int i = 0; i < expected.length; i++)
        {
            Assert.assertEquals(message, expected[i], actual[i], delta);
        }
    }

    public static void assertEquals(float[] expected, float[] actual, float delta)
    {
        assertEquals(null, expected, actual, delta);
    }

    public static void assertEquals(String message, float[][] expected, float[][] actual, float delta)
    {
        for (int i = 0; i < actual.length; i++)
        {
            for (int j = 0; j < actual[0].length; j++)
            {
                Assert.assertEquals(message, expected[i][j], actual[i][j], delta);
            }
        }
    }

    public static void assertEquals(float[][] expected, float[][] actual, float delta)
    {
        assertEquals(null, expected, actual, delta);
    }

    public static void assertEquals(String message, int[] expected, int[] actual)
    {
        Assert.assertEquals(message, expected.length, actual.length);
        for (int i = 0; i < expected.length; i++)
        {
            Assert.assertEquals(message, expected[i], actual[i]);
        }
    }

    public static void assertEquals(int[] expected, int[] actual)
    {
        assertEquals(null, expected, actual);
    }

    public static void assertEquals(String message, char[] expected, char[] actual)
    {
        Assert.assertEquals(message, expected.length, actual.length);
        for (int i = 0; i < expected.length; i++)
        {
            Assert.assertEquals(message, expected[i], actual[i]);
        }
    }

    public static void assertEquals(char[] expected, char[] actual)
    {
        assertEquals(null, expected, actual);
    }

    public static void assertEquals(String message, String[] expected, String[] actual)
    {
        Assert.assertEquals(message, expected.length, actual.length);
        for (int i = 0; i < expected.length; i++)
        {
            Assert.assertEquals(message, expected[i], actual[i]);
        }
    }

    public static void assertEquals(String[] expected, String[] actual)
    {
        assertEquals(null, expected, actual);
    }
}
