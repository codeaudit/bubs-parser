package edu.ohsu.cslu.parsing.trees;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

/**
 * Tree test suite.
 * 
 * @author Aaron Dunlop
 * @since Sep 25, 2008
 * 
 *        $Id$
 */
@RunWith(Suite.class)
@Suite.SuiteClasses( {TestIntegerNaryTree.class, TestCharacterNaryTree.class, TestParseTree.class,
                      TestStringNaryTree.class, TestIntShiftRegister.class, TestCharShiftRegister.class})
public class AllTreeTests
{}
