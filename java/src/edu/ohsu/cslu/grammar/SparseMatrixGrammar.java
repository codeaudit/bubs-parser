package edu.ohsu.cslu.grammar;

import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

import java.io.FileReader;
import java.io.Reader;
import java.util.Collection;

import edu.ohsu.cslu.datastructs.vectors.BitVector;
import edu.ohsu.cslu.datastructs.vectors.PackedBitVector;
import edu.ohsu.cslu.parser.ParserOptions.GrammarFormatType;

/**
 * Stores a grammar as a sparse matrix of probabilities.
 * 
 * Assumes fewer than 2^30 total non-terminals combinations (so that a production pair will fit into a signed
 * 32-bit int). This limit _may_ still allow more than 2^15 total non-terminals, depending on the grammar's
 * factorization. In general, we assume a left-factored grammar with many fewer valid right child productions
 * than left children.
 * 
 * @author Aaron Dunlop
 * @since Dec 31, 2009
 * 
 * @version $Revision$ $Date$ $Author$
 */

public abstract class SparseMatrixGrammar extends SortedGrammar {

    protected final int validProductionPairs;

    protected final CartesianProductFunction cartesianProductFunction;

    public SparseMatrixGrammar(final Reader grammarFile, final Reader lexiconFile,
            final GrammarFormatType grammarFormat, Class<? extends CartesianProductFunction> functionClass)
            throws Exception {
        super(grammarFile, lexiconFile, grammarFormat);

        try {
            if (functionClass == null) {
                functionClass = DefaultFunction.class;
            }
            this.cartesianProductFunction = functionClass.getConstructor(SparseMatrixGrammar.class)
                .newInstance(this);
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }

        // Some CartesianProductFunction implementation will duplicate this count, but it's not that expensive
        final IntOpenHashSet productionPairs = new IntOpenHashSet(50000);
        for (final Production p : binaryProductions) {
            productionPairs.add(cartesianProductFunction.pack(p.leftChild, (short) p.rightChild));
        }
        validProductionPairs = productionPairs.size();
    }

    public SparseMatrixGrammar(final Reader grammarFile, final Reader lexiconFile,
            final GrammarFormatType grammarFormat) throws Exception {
        this(grammarFile, lexiconFile, grammarFormat, DefaultFunction.class);
    }

    public SparseMatrixGrammar(final String grammarFile, final String lexiconFile,
            final GrammarFormatType grammarFormat) throws Exception {
        this(new FileReader(grammarFile), new FileReader(lexiconFile), grammarFormat);
    }

    /**
     * Returns the log probability of the specified parent / child production
     * 
     * @param parent Parent index
     * @param childPair Packed children
     * @return Log probability
     */
    public abstract float binaryLogProbability(final int parent, final int childPair);

    @Override
    public final float binaryLogProbability(final int parent, final int leftChild, final int rightChild) {
        return binaryLogProbability(parent, cartesianProductFunction.pack(leftChild, (short) rightChild));
    }

    /**
     * Returns the log probability of the specified parent / child production
     * 
     * @param parent Parent index
     * @param child Child index
     * @return Log probability
     */
    @Override
    public abstract float unaryLogProbability(final int parent, final int child);

    /**
     * Returns all rules as an array of maps, indexed by parent, each of which maps the packed children to the
     * probability.
     * 
     * @param productions Rules to be mapped
     * @return Array of maps from children -> probability
     */
    protected Int2FloatOpenHashMap[] mapRulesByParent(final Collection<Production> productions) {
        // Bin all rules by parent, mapping packed children -> probability
        final Int2FloatOpenHashMap[] maps = new Int2FloatOpenHashMap[numNonTerms()];
        for (int i = 0; i < numNonTerms(); i++) {
            maps[i] = new Int2FloatOpenHashMap(1000);
        }

        for (final Production p : productions) {
            maps[p.parent].put(cartesianProductFunction.pack(p.leftChild, (short) p.rightChild), p.prob);
        }
        return maps;
    }

    /**
     * Returns all rules as an array of maps, indexed by child pair, each of which maps the parent to the
     * probability.
     * 
     * @param productions Rules to be mapped
     * @return Array of maps from children -> probability
     */
    protected Int2ObjectOpenHashMap<Int2FloatOpenHashMap> mapRulesByChildPairs(
            final Collection<Production> productions) {
        // Bin all rules by child pair, mapping parent -> probability
        final Int2ObjectOpenHashMap<Int2FloatOpenHashMap> maps = new Int2ObjectOpenHashMap<Int2FloatOpenHashMap>(
            1000);

        for (final Production p : productions) {
            final int childPair = cartesianProductFunction.pack(p.leftChild, (short) p.rightChild);
            Int2FloatOpenHashMap map = maps.get(childPair);
            if (map == null) {
                map = new Int2FloatOpenHashMap(20);
                maps.put(childPair, map);
            }
            map.put(p.parent, p.prob);
        }
        return maps;
    }

    /**
     * Returns the cartesian-product function in use
     * 
     * @return the cartesian-product function in use
     */
    public final CartesianProductFunction cartesianProductFunction() {
        return cartesianProductFunction;
    }

    @Override
    public final boolean isValidRightChild(final int nonTerminal) {
        return (nonTerminal >= cartesianProductFunction.rightChildStart()
                && nonTerminal < cartesianProductFunction.rightChildEnd() && nonTerminal != nullSymbol);
    }

    @Override
    public final boolean isValidLeftChild(final int nonTerminal) {
        return (nonTerminal >= cartesianProductFunction.leftChildStart()
                && nonTerminal < cartesianProductFunction.leftChildEnd() && nonTerminal != nullSymbol);
    }

    /**
     * Returns a string representation of all child pairs recognized by this grammar.
     * 
     * @return a string representation of all child pairs recognized by this grammar.
     */
    @Override
    public String recognitionMatrix() {

        final IntSet validChildPairs = new IntOpenHashSet(binaryProductions.size() / 2);
        for (final Production p : binaryProductions) {
            validChildPairs.add(cartesianProductFunction.pack(p.leftChild, (short) p.rightChild));
        }

        final StringBuilder sb = new StringBuilder(10 * 1024);
        for (final int childPair : validChildPairs) {
            final int leftChild = cartesianProductFunction.unpackLeftChild(childPair);
            final int rightChild = cartesianProductFunction.unpackRightChild(childPair);
            sb.append(leftChild + "," + rightChild + '\n');
        }

        sb.deleteCharAt(sb.length() - 1);
        return sb.toString();
    }

    @Override
    public String getStats() {
        final StringBuilder sb = new StringBuilder(1024);
        sb.append(super.getStats());
        sb.append("Valid production pairs: " + validProductionPairs + '\n');
        sb.append("Valid left children: " + (numNonTerms() - normalPosStart) + '\n');
        sb.append("Valid right children: " + leftChildOnlyStart + '\n');

        sb.append("Max left child: " + (numNonTerms() - 1) + '\n');
        sb.append("Max right child: " + (leftChildOnlyStart - 1) + '\n');

        return sb.toString();
    }

    public interface CartesianProductFunction {

        /**
         * Return true if the specified child pair is a valid cross-product vector entry
         * 
         * @param childPair
         * @return true if the specified child pair is a valid cross-product vector entry
         */
        public abstract boolean isValid(int childPair);

        /**
         * Returns the array size required to store all possible child combinations.
         * 
         * @return the array size required to store all possible child combinations.
         */
        public abstract int packedArraySize();

        /**
         * Returns a single int representing a child pair.
         * 
         * TODO Return -1 if the pair is invalid?
         * 
         * @param leftChild
         * @param rightChild
         * @return packed representation of the specified child pair.
         */
        public abstract int pack(final int leftChild, final int rightChild);

        /**
         * Returns the left child encoded into a packed child pair
         * 
         * @param childPair
         * @return the left child encoded into a packed child pair
         */
        public abstract int unpackLeftChild(final int childPair);

        /**
         * Returns the right child encoded into a packed child pair
         * 
         * @param childPair
         * @return the right child encoded into a packed child pair
         */
        public abstract int unpackRightChild(final int childPair);

        /**
         * Returns the index of first non-terminal valid as a left child.
         * 
         * @return the index of first non-terminal valid as a left child.
         */
        public int leftChildStart();

        /**
         * Returns the index of first child not valid as a left child (the exclusive end of the left-child
         * range).
         * 
         * @return the index of first child not valid as a left child (the exclusive end of the left-child
         *         range).
         */
        public int leftChildEnd();

        /**
         * Returns the index of first non-terminal valid as a right child.
         * 
         * @return the index of first non-terminal valid as a right child.
         */
        public int rightChildStart();

        /**
         * Returns the index of first child not valid as a left child (the exclusive end of the left-child
         * range).
         * 
         * @return the index of first child not valid as a left child (the exclusive end of the left-child
         *         range).
         */
        public int rightChildEnd();

        public abstract String openClPackDefine();
    }

    protected abstract class ShiftFunction implements CartesianProductFunction {
        // Shift lengths and masks for packing and unpacking non-terminals into an int
        public final int shift;
        protected final int highOrderMask;
        protected final int lowOrderMask;
        protected final int lowOrderNegativeBit;
        private final int packedArraySize;

        protected ShiftFunction(final int maxShiftedNonTerminal) {
            // Add 1 bit to leave empty for sign
            shift = edu.ohsu.cslu.util.Math.logBase2(maxShiftedNonTerminal) + 1;
            int m = 0;
            for (int i = 0; i < shift; i++) {
                m = m << 1 | 1;
            }
            lowOrderMask = m;
            highOrderMask = ~m;
            lowOrderNegativeBit = 1 << (shift - 1);

            packedArraySize = numNonTerms() << shift;
        }

        @Override
        public final int packedArraySize() {
            return packedArraySize;
        }

        @Override
        public boolean isValid(final int childPair) {
            return childPair < packedArraySize;
        }
    }

    public abstract class LeftShiftFunction extends ShiftFunction {

        public LeftShiftFunction(final int maxShiftedNonTerminal) {
            super(maxShiftedNonTerminal);
        }

        @Override
        public final int pack(final int leftChild, final int rightChild) {
            return leftChild << shift | (rightChild & lowOrderMask);
        }

        @Override
        public final int unpackLeftChild(final int childPair) {
            return childPair >> shift;
        }

        @Override
        public final int unpackRightChild(final int childPair) {
            final int lower = childPair & lowOrderMask;

            // Handle negative lower values
            if ((lower & lowOrderNegativeBit) == lowOrderNegativeBit) {
                return lower | highOrderMask;
            }
            return lower;
        }

        @Override
        public final String openClPackDefine() {
            return "#define PACK ((validLeftChildren[leftChildIndex] << " + shift
                    + ") | (validRightChildren[rightChildIndex] & " + lowOrderMask + "))";
        }
    }

    public final class DefaultFunction extends LeftShiftFunction {

        public DefaultFunction() {
            super(leftChildOnlyStart);
        }

        @Override
        public final int leftChildStart() {
            return posStart;
        }

        @Override
        public final int leftChildEnd() {
            return unaryChildOnlyStart;
        }

        @Override
        public final int rightChildStart() {
            return rightChildOnlyStart;
        }

        @Override
        public final int rightChildEnd() {
            return leftChildOnlyStart;
        }
    }

    // public class RightShiftFunction extends DefaultFunction {
    //
    // public RightShiftFunction() {
    // super(numNonTerms());
    // }
    //
    // @Override
    // public final int pack(final int leftChild, final int rightChild) {
    // return rightChild << leftShift | (leftChild & rightMask);
    // }
    //
    // @Override
    // public final int unpackLeftChild(final int childPair) {
    // final int lower = childPair & rightMask;
    //
    // // Handle negative lower values
    // if ((lower & rightNegativeBit) == rightNegativeBit) {
    // return lower | leftMask;
    // }
    // return lower;
    // }
    //
    // @Override
    // public final int unpackRightChild(final int childPair) {
    // return childPair >> leftShift;
    // }
    //
    // @Override
    // public String openClPackDefine() {
    // return "#define PACK ((validLeftChildren[leftChildIndex] << " + leftShift
    // + ") | (validRightChildren[rightChildIndex] & " + rightMask + "))";
    // }
    // }

    public final class UnfilteredFunction extends LeftShiftFunction {
        private final int numNonTerms;

        public UnfilteredFunction() {
            super(numNonTerms());
            this.numNonTerms = numNonTerms();
        }

        @Override
        public final int leftChildStart() {
            return 0;
        }

        @Override
        public final int leftChildEnd() {
            return numNonTerms;
        }

        @Override
        public final int rightChildStart() {
            return 0;
        }

        @Override
        public final int rightChildEnd() {
            return numNonTerms;
        }
    }

    public final class PosFactoredFilterFunction extends LeftShiftFunction {

        public PosFactoredFilterFunction() {
            super(leftChildOnlyStart);
        }

        @Override
        public boolean isValid(final int childPair) {
            if (childPair >= packedArraySize()) {
                return false;
            }

            final int leftChild = unpackLeftChild(childPair);
            final int rightChild = unpackRightChild(childPair);
            // Eliminate POS which cannot combine with left-factored non-terminals
            if (leftChild >= leftFactoredStart && leftChild < normalLeftChildStart
                    && rightChild >= posNonFactoredStart && rightChild < normalPosStart) {
                return false;
            }
            return true;
        }

        @Override
        public final int leftChildStart() {
            return posStart;
        }

        @Override
        public final int leftChildEnd() {
            return unaryChildOnlyStart;
        }

        @Override
        public final int rightChildStart() {
            return rightChildOnlyStart;
        }

        @Override
        public final int rightChildEnd() {
            return leftChildOnlyStart;
        }
    }

    public final class BitVectorExactFilterFunction extends LeftShiftFunction {

        /**
         * {@link BitVector} of child pairs found in binary grammar rules.
         * 
         * TODO This should really be implemented as a PackedBitMatrix, if we had such a class
         */
        private final PackedBitVector validChildPairs;

        public BitVectorExactFilterFunction() {
            super(leftChildOnlyStart);

            validChildPairs = new PackedBitVector(packedArraySize());

            for (final Production p : binaryProductions) {
                validChildPairs.add(pack(p.leftChild, (short) p.rightChild));
            }
        }

        @Override
        public final boolean isValid(final int childPair) {
            return validChildPairs.contains(childPair);
        }

        @Override
        public final int leftChildStart() {
            return posStart;
        }

        @Override
        public final int leftChildEnd() {
            return unaryChildOnlyStart;
        }

        @Override
        public final int rightChildStart() {
            return rightChildOnlyStart;
        }

        @Override
        public final int rightChildEnd() {
            return leftChildOnlyStart;
        }
    }
}
