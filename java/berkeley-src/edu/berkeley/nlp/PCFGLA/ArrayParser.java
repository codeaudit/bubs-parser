package edu.berkeley.nlp.PCFGLA;

import java.util.ArrayList;

import edu.berkeley.nlp.syntax.StateSet;
import edu.berkeley.nlp.syntax.Tree;
import edu.berkeley.nlp.util.IEEEDoubleScaling;
import edu.berkeley.nlp.util.Numberer;

/**
 * Simple mixture parser.
 */
public class ArrayParser {

    protected Numberer tagNumberer = Numberer.getGlobalNumberer("tags");

    Lexicon lexicon;

    int numStates;
    int maxNSubStates;
    int[] idxC;
    double[] scoresToAdd;
    int touchedRules;
    double[] tmpCountsArray;
    Grammar grammar;
    int[] stateClass;

    public ArrayParser(final Grammar gr, final Lexicon lex) {
        this.touchedRules = 0;
        this.grammar = gr;
        this.lexicon = lex;
        this.tagNumberer = Numberer.getGlobalNumberer("tags");
        this.numStates = gr.numStates;
        this.maxNSubStates = gr.maxSubStates();
        this.idxC = new int[maxNSubStates];
        this.scoresToAdd = new double[maxNSubStates];
        tmpCountsArray = new double[scoresToAdd.length * scoresToAdd.length * scoresToAdd.length];
    }

    /**
     * Calculate the inside scores, P(words_i,j|nonterminal_i,j) of a tree given the string if words it should parse to.
     * 
     * @param tree
     * @param noSmoothing
     */
    void doInsideScores(final Tree<StateSet> tree, final boolean noSmoothing) {

        if (tree.isLeaf()) {
            return;
        }

        final ArrayList<Tree<StateSet>> children = tree.children();
        for (final Tree<StateSet> child : children) {
            doInsideScores(child, noSmoothing);
        }
        final StateSet parent = tree.label();
        final short unsplitParent = parent.getState();
        final int nParentStates = parent.numSubStates();

        if (tree.isPreTerminal()) {
            // Plays a role similar to initializeChart()
            final StateSet wordStateSet = tree.children().get(0).label();
            final double[] lexiconScores = lexicon.score(wordStateSet, unsplitParent, noSmoothing, false);
            if (lexiconScores.length != nParentStates) {
                throw new IllegalArgumentException("Have more scores than substates!" + lexiconScores.length + " "
                        + nParentStates);
            }
            parent.setIScores(lexiconScores);
            parent.setIScale(IEEEDoubleScaling.scaleArray(lexiconScores, 0));

        } else {
            switch (children.size()) {
            case 0:
                break;

            case 1:
                final StateSet child = children.get(0).label();
                final short unsplitChild = child.getState();

                final double[] iScores = new double[nParentStates];
                final Grammar.PackedUnaryRule packedUnaryRule = grammar.getPackedUnaryScores(unsplitParent,
                        unsplitChild);

                for (int i = 0, j = 0; i < packedUnaryRule.ruleScores.length; i++, j += 2) {
                    final short childSplit = packedUnaryRule.substates[j];
                    final short parentSplit = packedUnaryRule.substates[j + 1];

                    final double childInsideScore = child.getIScore(childSplit);
                    iScores[parentSplit] += packedUnaryRule.ruleScores[i] * childInsideScore;
                }

                parent.setIScores(iScores);
                parent.setIScale(IEEEDoubleScaling.scaleArray(iScores, child.getIScale()));
                break;

            case 2:
                final StateSet leftChild = children.get(0).label();
                final StateSet rightChild = children.get(1).label();
                final short unsplitLeftChild = leftChild.getState();
                final short unsplitRightChild = rightChild.getState();

                final Grammar.PackedBinaryRule packedBinaryRule = grammar.getPackedBinaryScores(unsplitParent,
                        unsplitLeftChild, unsplitRightChild);

                final double[] iScores2 = new double[nParentStates];

                for (int i = 0, j = 0; i < packedBinaryRule.ruleScores.length; i++, j += 3) {
                    final short leftChildSplit = packedBinaryRule.substates[j];
                    final short rightChildSplit = packedBinaryRule.substates[j + 1];
                    final short parentSplit = packedBinaryRule.substates[j + 2];

                    final double leftChildInsideScore = leftChild.getIScore(leftChildSplit);
                    final double rightChildInsideScore = rightChild.getIScore(rightChildSplit);

                    iScores2[parentSplit] += packedBinaryRule.ruleScores[i] * leftChildInsideScore
                            * rightChildInsideScore;
                }

                parent.setIScores(iScores2);
                parent.setIScale(IEEEDoubleScaling.scaleArray(iScores2, leftChild.getIScale() + rightChild.getIScale()));
                break;

            default:
                throw new IllegalArgumentException("Malformed tree: more than two children");
            }
        }
    }

    /**
     * Calculate the outside scores of a tree; that is, P(nonterminal_i,j|words_0,i; words_j,end). It is calculate from
     * the inside scores of the tree.
     * 
     * <p>
     * Note: when calling this, call setRootOutsideScore() first.
     * 
     * @param tree
     */
    void doOutsideScores(final Tree<StateSet> tree) {

        if (tree.isLeaf() || tree.isPreTerminal()) {
            return;
        }

        final ArrayList<Tree<StateSet>> children = tree.children();
        final StateSet parent = tree.label();
        final short unsplitParent = parent.getState();

        // this sets the outside scores for the children

        final double[] parentOutsideScores = parent.getOScores();

        switch (children.size()) {

        case 1:
            final StateSet child = children.get(0).label();
            final short unsplitChild = child.getState();
            final int nChildStates = child.numSubStates();
            final double[] oScores = new double[nChildStates];

            final Grammar.PackedUnaryRule packedUnaryRule = grammar.getPackedUnaryScores(unsplitParent, unsplitChild);
            for (int i = 0, j = 0; i < packedUnaryRule.ruleScores.length; i++, j += 2) {
                final short childSplit = packedUnaryRule.substates[j];
                final short parentSplit = packedUnaryRule.substates[j + 1];

                final double parentOutsideScore = parentOutsideScores[parentSplit];
                oScores[childSplit] += packedUnaryRule.ruleScores[i] * parentOutsideScore;
            }

            child.setOScores(oScores);
            child.setOScale(IEEEDoubleScaling.scaleArray(oScores, parent.getOScale()));
            break;

        case 2:
            final StateSet leftChild = children.get(0).label();
            final StateSet rightChild = children.get(1).label();

            final int nLeftChildStates = leftChild.numSubStates();
            final int nRightChildStates = rightChild.numSubStates();

            final short unsplitLeftChild = leftChild.getState();
            final short unsplitRightChild = rightChild.getState();

            final double[] lOScores = new double[nLeftChildStates];
            final double[] rOScores = new double[nRightChildStates];

            final Grammar.PackedBinaryRule packedBinaryRule = grammar.getPackedBinaryScores(unsplitParent,
                    unsplitLeftChild, unsplitRightChild);
            // Iterate through all splits of the rule. Most will have non-0 child and parent probability (since the
            // rule currently exists in the grammar), and this iteration order is very efficient
            for (int i = 0, j = 0; i < packedBinaryRule.ruleScores.length; i++, j += 3) {
                final short leftChildSplit = packedBinaryRule.substates[j];
                final short rightChildSplit = packedBinaryRule.substates[j + 1];
                final short parentSplit = packedBinaryRule.substates[j + 2];

                final double leftChildInsideScore = leftChild.getIScore(leftChildSplit);
                final double rightChildInsideScore = rightChild.getIScore(rightChildSplit);

                lOScores[leftChildSplit] += parentOutsideScores[parentSplit] * packedBinaryRule.ruleScores[i]
                        * rightChildInsideScore;
                rOScores[rightChildSplit] += parentOutsideScores[parentSplit] * packedBinaryRule.ruleScores[i]
                        * leftChildInsideScore;
            }

            leftChild.setOScores(lOScores);
            leftChild.setOScale(IEEEDoubleScaling.scaleArray(lOScores, parent.getOScale() + rightChild.getIScale()));

            rightChild.setOScores(rOScores);
            rightChild.setOScale(IEEEDoubleScaling.scaleArray(rOScores, parent.getOScale() + leftChild.getIScale()));

            break;

        default:
            throw new IllegalArgumentException("Malformed tree: more than two children");
        }

        for (final Tree<StateSet> child : children) {
            doOutsideScores(child);
        }
    }

    public void doInsideOutsideScores(final Tree<StateSet> tree, final boolean noSmoothing) {
        doInsideScores(tree, noSmoothing);
        tree.label().setOScore(0, 1);
        tree.label().setOScale(0);
        doOutsideScores(tree);
    }
}
