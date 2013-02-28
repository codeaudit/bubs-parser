package edu.berkeley.nlp.util;

import java.io.Serializable;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;

/**
 * A map from objects to doubles. Includes convenience methods for getting, setting, and incrementing element counts.
 * Objects not in the counter will return a count of zero. The counter is backed by a HashMap (unless specified
 * otherwise with the MapFactory constructor).
 * 
 * Replace with Object2DoubleOpenHashMap (or possibly with Object2FloatOpenHashMap)
 * 
 * @author Dan Klein
 */
public class Counter<E> implements Serializable {
    private static final long serialVersionUID = 1L;
    Map<E, Double> entries;
    boolean dirty = true;
    double cacheTotal = 0.0;
    double deflt = 0.0;

    public double getDeflt() {
        return deflt;
    }

    public void setDeflt(final double deflt) {
        this.deflt = deflt;
    }

    /**
     * The elements in the counter.
     * 
     * @return set of keys
     */
    public Set<E> keySet() {
        return entries.keySet();
    }

    public Set<Entry<E, Double>> entrySet() {
        return entries.entrySet();
    }

    /**
     * The number of entries in the counter (not the total count -- use totalCount() instead).
     */
    public int size() {
        return entries.size();
    }

    /**
     * True if there are no entries in the counter (false does not mean totalCount > 0)
     */
    public boolean isEmpty() {
        return size() == 0;
    }

    /**
     * Returns whether the counter contains the given key. Note that this is the way to distinguish keys which are in
     * the counter with count zero, and those which are not in the counter (and will therefore return count zero from
     * getCount().
     * 
     * @param key
     * @return whether the counter contains the key
     */
    public boolean containsKey(final E key) {
        return entries.containsKey(key);
    }

    /**
     * @param key
     * @return the count of the specified <code>key</code>, or zero if <code>key</code> is not present in the counter.
     */
    public double getCount(final E key) {
        final Double value = entries.get(key);
        if (value == null)
            return deflt;
        return value;
    }

    /**
     * I know, I know, this should be wrapped in a Distribution class, but it's such a common use...why not. Returns the
     * MLE prob. Assumes all the counts are >= 0.0 and totalCount > 0.0. If the latter is false, return 0.0 (i.e. 0/0 ==
     * 0)
     * 
     * @author Aria
     * @param key
     * @return MLE prob of the key
     */
    public double getProbability(final E key) {
        final double count = getCount(key);
        final double total = totalCount();
        if (total < 0.0) {
            throw new RuntimeException("Can't call getProbability() with totalCount < 0.0");
        }
        return total > 0.0 ? count / total : 0.0;
    }

    /**
     * Set the count for the given key, clobbering any previous count.
     * 
     * @param key
     * @param count
     */
    public void setCount(final E key, final double count) {
        entries.put(key, count);
        dirty = true;
    }

    /**
     * Set the count for the given key if it is larger than the previous one;
     * 
     * @param key
     * @param count
     */
    public void put(final E key, final double count, final boolean keepHigher) {
        if (keepHigher && entries.containsKey(key)) {
            final double oldCount = entries.get(key);
            if (count > oldCount) {
                entries.put(key, count);
            }
        } else {
            entries.put(key, count);
        }
        dirty = true;
    }

    /**
     * Increment a key's count by the given amount.
     * 
     * @param key
     * @param increment
     */
    public double incrementCount(final E key, final double increment) {
        final double newVal = getCount(key) + increment;
        setCount(key, newVal);
        dirty = true;
        return newVal;
    }

    /**
     * Increment each element in a given collection by a given amount.
     */
    public void incrementAll(final Collection<? extends E> collection, final double count) {
        for (final E key : collection) {
            incrementCount(key, count);
        }
        dirty = true;
    }

    public <T extends E> void incrementAll(final Counter<T> counter) {
        for (final T key : counter.keySet()) {
            final double count = counter.getCount(key);
            incrementCount(key, count);
        }
        dirty = true;
    }

    /**
     * Finds the total of all counts in the counter. This implementation iterates through the entire counter every time
     * this method is called.
     * 
     * @return the counter's total
     */
    public double totalCount() {
        if (!dirty) {
            return cacheTotal;
        }
        double total = 0.0;
        for (final Map.Entry<E, Double> entry : entries.entrySet()) {
            total += entry.getValue();
        }
        cacheTotal = total;
        dirty = false;
        return total;
    }

    public List<E> getSortedKeys() {
        final PriorityQueue<E> pq = this.asPriorityQueue();
        final List<E> keys = new ArrayList<E>();
        while (pq.hasNext()) {
            keys.add(pq.next());
        }
        return keys;
    }

    /**
     * Finds the key with maximum count. This is a linear operation, and ties are broken arbitrarily.
     * 
     * @return a key with minumum count
     */
    public E argMax() {
        double maxCount = Double.NEGATIVE_INFINITY;
        E maxKey = null;
        for (final Map.Entry<E, Double> entry : entries.entrySet()) {
            if (entry.getValue() > maxCount || maxKey == null) {
                maxKey = entry.getKey();
                maxCount = entry.getValue();
            }
        }
        return maxKey;
    }

    public double min() {
        return maxMinHelp(false);
    }

    public double max() {
        return maxMinHelp(true);
    }

    private double maxMinHelp(final boolean max) {
        double maxCount = max ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;

        for (final Map.Entry<E, Double> entry : entries.entrySet()) {
            if ((max && entry.getValue() > maxCount) || (!max && entry.getValue() < maxCount)) {

                maxCount = entry.getValue();
            }
        }
        return maxCount;
    }

    /**
     * Returns a string representation with the keys ordered by decreasing counts.
     * 
     * @return string representation
     */
    @Override
    public String toString() {
        return toString(keySet().size());
    }

    public String toStringSortedByKeys() {
        final StringBuilder sb = new StringBuilder("[");

        final NumberFormat f = NumberFormat.getInstance();
        f.setMaximumFractionDigits(5);
        int numKeysPrinted = 0;
        for (final E element : new TreeSet<E>(keySet())) {

            sb.append(element.toString());
            sb.append(" : ");
            sb.append(f.format(getCount(element)));
            if (numKeysPrinted < size() - 1)
                sb.append(", ");
            numKeysPrinted++;
        }
        if (numKeysPrinted < size())
            sb.append("...");
        sb.append("]");
        return sb.toString();
    }

    /**
     * Returns a string representation which includes no more than the maxKeysToPrint elements with largest counts.
     * 
     * @param maxKeysToPrint
     * @return partial string representation
     */
    public String toString(final int maxKeysToPrint) {
        return asPriorityQueue().toString(maxKeysToPrint, false);
    }

    /**
     * Returns a string representation which includes no more than the maxKeysToPrint elements with largest counts and
     * optionally prints one element per line.
     * 
     * @param maxKeysToPrint
     * @return partial string representation
     */
    public String toString(final int maxKeysToPrint, final boolean multiline) {
        return asPriorityQueue().toString(maxKeysToPrint, multiline);
    }

    /**
     * Builds a priority queue whose elements are the counter's elements, and whose priorities are those elements'
     * counts in the counter.
     */
    public PriorityQueue<E> asPriorityQueue() {
        final PriorityQueue<E> pq = new PriorityQueue<E>(entries.size());
        for (final Map.Entry<E, Double> entry : entries.entrySet()) {
            pq.add(entry.getKey(), entry.getValue());
        }
        return pq;
    }

    public Counter() {
        entries = new HashMap<E, Double>();
    }

    public Counter(final HashMap<E, Double> mapCounts) {
        this.entries = new HashMap<E, Double>();
        for (final Entry<? extends E, Double> entry : mapCounts.entrySet()) {
            incrementCount(entry.getKey(), entry.getValue());
        }
    }

    public Counter(final Counter<? extends E> counter) {
        this();
        incrementAll(counter);
    }

    public Counter(final Collection<? extends E> collection) {
        this();
        incrementAll(collection, 1.0);
    }

    public void pruneKeysBelowThreshold(final double cutoff) {
        final Iterator<E> it = entries.keySet().iterator();
        while (it.hasNext()) {
            final E key = it.next();
            final double val = entries.get(key);
            if (val < cutoff) {
                it.remove();
            }
        }
        dirty = true;
    }

    public Set<Map.Entry<E, Double>> getEntrySet() {
        return entries.entrySet();
    }

    public void clear() {
        entries = new HashMap<E, Double>();
        dirty = true;
    }

    /**
     * Sets all counts to the given value, but does not remove any keys
     */
    public void setAllCounts(final double val) {
        for (final E e : keySet()) {
            setCount(e, val);
        }

    }

    public double dotProduct(final Counter<E> other) {
        double sum = 0.0;
        for (final Map.Entry<E, Double> entry : getEntrySet()) {
            final double otherCount = other.getCount(entry.getKey());
            if (otherCount == 0.0)
                continue;
            final double value = entry.getValue();
            if (value == 0.0)
                continue;
            sum += value * otherCount;

        }
        return sum;
    }

    public void scale(final double c) {

        for (final Map.Entry<E, Double> entry : getEntrySet()) {
            entry.setValue(entry.getValue() * c);
        }

    }

    public Counter<E> scaledClone(final double c) {
        final Counter<E> newCounter = new Counter<E>();

        for (final Map.Entry<E, Double> entry : getEntrySet()) {
            newCounter.setCount(entry.getKey(), entry.getValue() * c);
        }

        return newCounter;
    }

    public Counter<E> difference(final Counter<E> counter) {
        final Counter<E> clone = new Counter<E>(this);
        for (final E key : counter.keySet()) {
            final double count = counter.getCount(key);
            clone.incrementCount(key, -1 * count);
        }
        return clone;
    }

    public Counter<E> toLogSpace() {
        final Counter<E> newCounter = new Counter<E>(this);
        for (final E key : newCounter.keySet()) {
            newCounter.setCount(key, Math.log(getCount(key)));
        }
        return newCounter;
    }

    public boolean approxEquals(final Counter<E> other, final double tol) {
        for (final E key : keySet()) {
            if (Math.abs(getCount(key) - other.getCount(key)) > tol)
                return false;
        }
        for (final E key : other.keySet()) {
            if (Math.abs(getCount(key) - other.getCount(key)) > tol)
                return false;
        }
        return true;
    }

    public void setDirty(final boolean dirty) {
        this.dirty = dirty;
    }

    public String toStringTabSeparated() {
        final StringBuilder sb = new StringBuilder();
        for (final E key : getSortedKeys()) {
            sb.append(key.toString() + "\t" + getCount(key) + "\n");
        }
        return sb.toString();
    }

}
