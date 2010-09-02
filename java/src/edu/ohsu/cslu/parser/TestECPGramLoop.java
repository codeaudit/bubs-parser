package edu.ohsu.cslu.parser;

import org.junit.Test;

import edu.ohsu.cslu.grammar.Grammar;
import edu.ohsu.cslu.grammar.GrammarByChild;
import edu.ohsu.cslu.tests.PerformanceTest;

/**
 * Unit and performance tests for {@link TestECPGramLoop}
 * 
 * @author Aaron Dunlop
 * @since Dec 23, 2009
 * 
 * @version $Revision$ $Date$ $Author$
 */
public class TestECPGramLoop extends ExhaustiveChartParserTestCase<ECPGrammarLoop> {

    @Override
    protected Class<? extends Grammar> grammarClass() {
        return GrammarByChild.class;
    }

    @Override
    @Test
    @PerformanceTest({ "mbp", "22485", "d820", "31297" })
    public void profileSentences11Through20() throws Exception {
        internalProfileSentences11Through20();
    }
}
