package com.raffaeleconforti.noisefiltering.timestamp.collections;

import com.google.common.annotations.Beta;
import com.google.common.annotations.GwtCompatible;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.*;
import com.google.common.primitives.Ints;
import com.raffaeleconforti.kernelestimation.distribution.EventDistributionCalculator;
import com.sun.istack.Nullable;
import org.deckfour.xes.model.XEvent;

import java.util.*;

import static com.google.common.base.Preconditions.*;

/**
 * Provides static methods for working with {@code Collection} instances.
 *
 * @author Chris Povirk
 * @author Mike Bostock
 * @author Jared Levy
 * @author Raffaele Conforti
 * @since 2.0 (imported from Google Collections Library)
 */
@GwtCompatible
public final class Collections2 {

    private Collections2() {}

    /**
     * Returns the elements of {@code unfiltered} that satisfy a predicate. The
     * returned collections is a live view of {@code unfiltered}; changes to one
     * affect the other.
     *
     * <p>The resulting collections's iterator does not support {@code remove()},
     * but all other collections methods are supported. When given an element that
     * doesn't satisfy the predicate, the collections's {@code add()} and {@code
     * addAll()} methods throw an {@link IllegalArgumentException}. When methods
     * such as {@code removeAll()} and {@code clear()} are called on the filtered
     * collections, only elements that satisfy the filter will be removed from the
     * underlying collections.
     *
     * <p>The returned collections isn't threadsafe or serializable, even if
     * {@code unfiltered} is.
     *
     * <p>Many of the filtered collections's methods, such as {@code size()},
     * iterate across every element in the underlying collections and determine
     * which elements satisfy the filter. When a live view is <i>not</i> needed,
     * it may be faster to copy {@code Iterables.filter(unfiltered, predicate)}
     * and use the copy.
     *
     * <p><b>Warning:</b> {@code predicate} must be <i>consistent with equals</i>,
     * as documented at {@link Predicate#apply}. Do not provide a predicate such
     * as {@code Predicates.instanceOf(ArrayList.class)}, which is inconsistent
     * with equals. (See {@link Iterables#filter(Iterable, Class)} for related
     * functionality.)
     */
    // TODO(kevinb): how can we omit that Iterables link when building gwt
    // javadoc?
    public static <E> Collection<E> filter(
            Collection<E> unfiltered, Predicate<? super E> predicate) {
        if (unfiltered instanceof FilteredCollection) {
            // Support clear(), removeAll(), and retainAll() when filtering a filtered
            // collections.
            return ((FilteredCollection<E>) unfiltered).createCombined(predicate);
        }

        return new FilteredCollection<E>(
                checkNotNull(unfiltered), checkNotNull(predicate));
    }

    /**
     * Delegates to {@link Collection#contains}. Returns {@code false} if the
     * {@code contains} method throws a {@code ClassCastException}.
     */
    static boolean safeContains(Collection<?> collection, Object object) {
        try {
            return collection.contains(object);
        } catch (ClassCastException e) {
            return false;
        }
    }

    static class FilteredCollection<E> implements Collection<E> {
        final Collection<E> unfiltered;
        final Predicate<? super E> predicate;

        FilteredCollection(Collection<E> unfiltered,
                           Predicate<? super E> predicate) {
            this.unfiltered = unfiltered;
            this.predicate = predicate;
        }

        FilteredCollection<E> createCombined(Predicate<? super E> newPredicate) {
            return new FilteredCollection<E>(unfiltered,
                    Predicates.and(predicate, newPredicate));
            // .<E> above needed to compile in JDK 5
        }

        @Override
        public boolean add(E element) {
            checkArgument(predicate.apply(element));
            return unfiltered.add(element);
        }

        @Override
        public boolean addAll(Collection<? extends E> collection) {
            for (E element : collection) {
                checkArgument(predicate.apply(element));
            }
            return unfiltered.addAll(collection);
        }

        @Override
        public void clear() {
            Iterables.removeIf(unfiltered, predicate);
        }

        @Override
        public boolean contains(Object element) {
            try {
                // unsafe cast can result in a CCE from predicate.apply(), which we
                // will catch
                @SuppressWarnings("unchecked")
                E e = (E) element;

        /*
         * We check whether e satisfies the predicate, when we really mean to
         * check whether the element contained in the set does. This is ok as
         * long as the predicate is consistent with equals, as required.
         */
                return predicate.apply(e) && unfiltered.contains(element);
            } catch (NullPointerException e) {
                return false;
            } catch (ClassCastException e) {
                return false;
            }
        }

        @Override
        public boolean containsAll(Collection<?> collection) {
            for (Object element : collection) {
                if (!contains(element)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public boolean isEmpty() {
            return !Iterators.any(unfiltered.iterator(), predicate);
        }

        @Override
        public Iterator<E> iterator() {
            return Iterators.filter(unfiltered.iterator(), predicate);
        }

        @Override
        public boolean remove(Object element) {
            try {
                // unsafe cast can result in a CCE from predicate.apply(), which we
                // will catch
                @SuppressWarnings("unchecked")
                E e = (E) element;

                // See comment in contains() concerning predicate.apply(e)
                return predicate.apply(e) && unfiltered.remove(element);
            } catch (NullPointerException e) {
                return false;
            } catch (ClassCastException e) {
                return false;
            }
        }

        @Override
        public boolean removeAll(final Collection<?> collection) {
            checkNotNull(collection);
            Predicate<E> combinedPredicate = new Predicate<E>() {
                @Override
                public boolean apply(E input) {
                    return predicate.apply(input) && collection.contains(input);
                }
            };
            return Iterables.removeIf(unfiltered, combinedPredicate);
        }

        @Override
        public boolean retainAll(final Collection<?> collection) {
            checkNotNull(collection);
            Predicate<E> combinedPredicate = new Predicate<E>() {
                @Override
                public boolean apply(E input) {
                    // See comment in contains() concerning predicate.apply(e)
                    return predicate.apply(input) && !collection.contains(input);
                }
            };
            return Iterables.removeIf(unfiltered, combinedPredicate);
        }

        @Override
        public int size() {
            return Iterators.size(iterator());
        }

        @Override
        public Object[] toArray() {
            // creating an ArrayList so filtering happens once
            return Lists.newArrayList(iterator()).toArray();
        }

        @Override
        public <T> T[] toArray(T[] array) {
            return Lists.newArrayList(iterator()).toArray(array);
        }

        @Override public String toString() {
            return Iterators.toString(iterator());
        }
    }

    /**
     * Returns a collections that applies {@code function} to each element of
     * {@code fromCollection}. The returned collections is a live view of {@code
     * fromCollection}; changes to one affect the other.
     *
     * <p>The returned collections's {@code add()} and {@code addAll()} methods
     * throw an {@link UnsupportedOperationException}. All other collections
     * methods are supported, as long as {@code fromCollection} supports them.
     *
     * <p>The returned collections isn't threadsafe or serializable, even if
     * {@code fromCollection} is.
     *
     * <p>When a live view is <i>not</i> needed, it may be faster to copy the
     * transformed collections and use the copy.
     *
     * <p>If the input {@code Collection} is known to be a {@code List}, consider
     * {@link Lists#transform}. If only an {@code Iterable} is available, use
     * {@link Iterables#transform}.
     */
    public static <F, T> Collection<T> transform(Collection<F> fromCollection,
                                                 Function<? super F, T> function) {
        return new TransformedCollection<F, T>(fromCollection, function);
    }

    static class TransformedCollection<F, T> extends AbstractCollection<T> {
        final Collection<F> fromCollection;
        final Function<? super F, ? extends T> function;

        TransformedCollection(Collection<F> fromCollection,
                              Function<? super F, ? extends T> function) {
            this.fromCollection = checkNotNull(fromCollection);
            this.function = checkNotNull(function);
        }

        @Override public void clear() {
            fromCollection.clear();
        }

        @Override public boolean isEmpty() {
            return fromCollection.isEmpty();
        }

        @Override public Iterator<T> iterator() {
            return Iterators.transform(fromCollection.iterator(), function);
        }

        @Override public int size() {
            return fromCollection.size();
        }
    }

    /**
     * Returns {@code true} if the collections {@code self} contains all of the
     * elements in the collections {@code c}.
     *
     * <p>This method iterates over the specified collections {@code c}, checking
     * each element returned by the iterator in turn to see if it is contained in
     * the specified collections {@code self}. If all elements are so contained,
     * {@code true} is returned, otherwise {@code false}.
     *
     * @param self a collections which might contain all elements in {@code c}
     * @param c a collections whose elements might be contained by {@code self}
     */
    static boolean containsAllImpl(Collection<?> self, Collection<?> c) {
        checkNotNull(self);
        for (Object o : c) {
            if (!self.contains(o)) {
                return false;
            }
        }
        return true;
    }

    /**
     * An implementation of {@link Collection#toString()}.
     */
    static String toStringImpl(final Collection<?> collection) {
        StringBuilder sb
                = newStringBuilderForCollection(collection.size()).append('[');
        STANDARD_JOINER.appendTo(
                sb, Iterables.transform(collection, new Function<Object, Object>() {
                    @Override public Object apply(Object input) {
                        return input == collection ? "(this Collection)" : input;
                    }
                }));
        return sb.append(']').toString();
    }

    /**
     * Returns best-effort-sized StringBuilder based on the given collections size.
     */
    static StringBuilder newStringBuilderForCollection(int size) {
        checkArgument(size >= 0, "size must be non-negative");
        return new StringBuilder((int) Math.min(size * 8L, Ints.MAX_POWER_OF_TWO));
    }

    /**
     * Used to avoid http://bugs.sun.com/view_bug.do?bug_id=6558557
     */
    static <T> Collection<T> cast(Iterable<T> iterable) {
        return (Collection<T>) iterable;
    }

    static final Joiner STANDARD_JOINER = Joiner.on(", ").useForNull("null");

    /**
     * Returns a {@link Collection} of all the permutations of the specified
     * {@link Iterable}.
     *
     * <p><i>Notes:</i> This is an implementation of the algorithm for
     * Lexicographical Permutations Generation, described in Knuth's "The Art of
     * Computer Programming", Volume 4, Chapter 7, Section 7.2.1.2. The
     * iteration order follows the lexicographical order. This means that
     * the first permutation will be in ascending order, and the last will be in
     * descending order.
     *
     * <p>Duplicate elements are considered equal. For example, the list [1, 1]
     * will have only one permutation, instead of two. This is why the elements
     * have to implement {@link Comparable}.
     *
     * <p>An empty iterable has only one permutation, which is an empty list.
     *
     * <p>This method is equivalent to
     * {@code Collections2.orderedPermutations(list, Ordering.natural())}.
     *
     * @param elements the original iterable whose elements have to be permuted.
     * @return an immutable {@link Collection} containing all the different
     *     permutations of the original iterable.
     * @throws NullPointerException if the specified iterable is null or has any
     *     null elements.
     * @since 12.0
     */
    @Beta public static <E extends Comparable<? super E>>
    Collection<List<E>> orderedPermutations(Iterable<E> elements) {
        return orderedPermutations(elements, Ordering.natural());
    }

    /**
     * Returns a {@link Collection} of all the permutations of the specified
     * {@link Iterable} using the specified {@link Comparator} for establishing
     * the lexicographical ordering.
     *
     * <p>Examples: <pre>   {@code
     *
     *   for (List<String> perm : orderedPermutations(asList("b", "c", "a"))) {
     *     println(perm);
     *   }
     *   // -> ["a", "b", "c"]
     *   // -> ["a", "c", "b"]
     *   // -> ["b", "a", "c"]
     *   // -> ["b", "c", "a"]
     *   // -> ["c", "a", "b"]
     *   // -> ["c", "b", "a"]
     *
     *   for (List<Integer> perm : orderedPermutations(asList(1, 2, 2, 1))) {
     *     println(perm);
     *   }
     *   // -> [1, 1, 2, 2]
     *   // -> [1, 2, 1, 2]
     *   // -> [1, 2, 2, 1]
     *   // -> [2, 1, 1, 2]
     *   // -> [2, 1, 2, 1]
     *   // -> [2, 2, 1, 1]}</pre>
     *
     * <p><i>Notes:</i> This is an implementation of the algorithm for
     * Lexicographical Permutations Generation, described in Knuth's "The Art of
     * Computer Programming", Volume 4, Chapter 7, Section 7.2.1.2. The
     * iteration order follows the lexicographical order. This means that
     * the first permutation will be in ascending order, and the last will be in
     * descending order.
     *
     * <p>Elements that compare equal are considered equal and no new permutations
     * are created by swapping them.
     *
     * <p>An empty iterable has only one permutation, which is an empty list.
     *
     * @param elements the original iterable whose elements have to be permuted.
     * @param comparator a comparator for the iterable's elements.
     * @return an immutable {@link Collection} containing all the different
     *     permutations of the original iterable.
     * @throws NullPointerException If the specified iterable is null, has any
     *     null elements, or if the specified comparator is null.
     * @since 12.0
     */
    @Beta public static <E> Collection<List<E>> orderedPermutations(
            Iterable<E> elements, Comparator<? super E> comparator) {
        return new OrderedPermutationCollection<E>(elements, comparator);
    }

    private static final class OrderedPermutationCollection<E>
            extends AbstractCollection<List<E>> {
        final ImmutableList<E> inputList;
        final Comparator<? super E> comparator;
        final int size;

        OrderedPermutationCollection(Iterable<E> input,
                                     Comparator<? super E> comparator) {
            this.inputList = Ordering.from(comparator).immutableSortedCopy(input);
            this.comparator = comparator;
            this.size = calculateSize(inputList, comparator);
        }

        /**
         * The number of permutations with repeated elements is calculated as
         * follows:
         * <ul>
         * <li>For an empty list, it is 1 (base case).</li>
         * <li>When r numbers are added to a list of n-r elements, the number of
         * permutations is increased by a factor of (n choose r).</li>
         * </ul>
         */
        static <E> int calculateSize(List<E> sortedInputList,
                                     Comparator<? super E> comparator) {
            try {
                long permutations = 1;
                int n = 1;
                int r = 1;
                for (; n < sortedInputList.size(); n++, r++) {
                    int comparison = comparator.compare(sortedInputList.get(n - 1),
                            sortedInputList.get(n));
                    // The list is sorted, this is an invariant.
                    checkState(comparison <= 0);
                    if (comparison < 0) {
                        // We move to the next non-repeated element.
                        permutations *= binomialCoefficient(n, r);
                        r = 0;

                        // Return early if we have more than MAX_VALUE permutations.
                        if (!isPositiveInt(permutations)) {
                            return Integer.MAX_VALUE;
                        }
                    }
                }
                permutations *= binomialCoefficient(n, r);
                if (!isPositiveInt(permutations)) {
                    return Integer.MAX_VALUE;
                }
                return (int) permutations;
            } catch (IllegalArgumentException e) {
                // Overflow. Fall back to max size.
                return Integer.MAX_VALUE;
            }
        }

        @Override public int size() {
            return size;
        }

        @Override public boolean isEmpty() {
            return false;
        }

        @Override public Iterator<List<E>> iterator() {
            return new OrderedPermutationIterator<E>(inputList, comparator);
        }

        @Override public boolean contains(@Nullable Object obj) {
            if (obj instanceof List<?>) {
                List<?> list = (List<?>) obj;
                return isPermutation(inputList, list);
            }
            return false;
        }

        @Override public String toString() {
            return "orderedPermutationCollection(" + inputList + ")";
        }
    }

    private static final class OrderedPermutationIterator<E>
            extends AbstractIterator<List<E>> {

        List<E> nextPermutation;
        final Comparator<? super E> comparator;

        OrderedPermutationIterator(List<E> list,
                                   Comparator<? super E> comparator) {
            this.nextPermutation = Lists.newArrayList(list);
            this.comparator = comparator;
        }

        @Override protected List<E> computeNext() {
            if (nextPermutation == null) {
                return endOfData();
            }
            ImmutableList<E> next = ImmutableList.copyOf(nextPermutation);
            calculateNextPermutation();
            return next;
        }

        void calculateNextPermutation() {
            int j = findNextJ();
            if (j == -1) {
                nextPermutation = null;
                return;
            }

            int l = findNextL(j);
            Collections.swap(nextPermutation, j, l);
            int n = nextPermutation.size();
            Collections.reverse(nextPermutation.subList(j + 1, n));
        }

        int findNextJ() {
            for (int k = nextPermutation.size() - 2; k >= 0; k--) {
                if (comparator.compare(nextPermutation.get(k),
                        nextPermutation.get(k + 1)) < 0) {
                    return k;
                }
            }
            return -1;
        }

        int findNextL(int j) {
            E ak = nextPermutation.get(j);
            for (int l = nextPermutation.size() - 1; l > j; l--) {
                if (comparator.compare(ak, nextPermutation.get(l)) < 0) {
                    return l;
                }
            }
            throw new AssertionError("this statement should be unreachable");
        }
    }

    /**
     * Returns a {@link Collection} of all the permutations of the specified
     * {@link Collection}.
     *
     * <p><i>Notes:</i> This is an implementation of the Plain Changes algorithm
     * for permutations generation, described in Knuth's "The Art of Computer
     * Programming", Volume 4, Chapter 7, Section 7.2.1.2.
     *
     * <p>If the input list contains equal elements, some of the generated
     * permutations will be equal.
     *
     * <p>An empty collections has only one permutation, which is an empty list.
     *
     * @param elements the original collections whose elements have to be permuted.
     * @return an immutable {@link Collection} containing all the different
     *     permutations of the original collections.
     * @throws NullPointerException if the specified collections is null or has any
     *     null elements.
     * @since 12.0
     */
    @Beta public static <E> Collection<List<E>> permutations(
            Collection<E> elements) {
        return new PermutationCollection<E>(ImmutableList.copyOf(elements));
    }

    @Beta public static <E> Collection<List<E>> permutations(
            Collection<E> elements, EventDistributionCalculator eventDistributionCalculator) {
        return new PermutationCollection<E>(ImmutableList.copyOf(elements), eventDistributionCalculator);
    }

    public static <E> Collection<List<E>> permutationsProbabilistic(
            Collection<E> elements, EventDistributionCalculator eventDistributionCalculator) {
        return new PermutationCollectionProbabilistic<E>(ImmutableList.copyOf(elements), eventDistributionCalculator);
    }

    private static final class PermutationCollection<E>
            extends AbstractCollection<List<E>> {
        final ImmutableList<E> inputList;
        final EventDistributionCalculator eventDistributionCalculator;

        PermutationCollection(ImmutableList<E> input, EventDistributionCalculator eventDistributionCalculator) {
            this.inputList = input;
            this.eventDistributionCalculator = eventDistributionCalculator;
        }

        PermutationCollection(ImmutableList<E> input) {
            this.inputList = input;
            this.eventDistributionCalculator = null;
        }

        @Override public int size() {
            return safeIntFactorial(inputList.size());
        }

        @Override public boolean isEmpty() {
            return false;
        }

        @Override public Iterator<List<E>> iterator() {
            return new PermutationIterator<E>(inputList, eventDistributionCalculator);
        }

        @Override public boolean contains(@Nullable Object obj) {
            if (obj instanceof List<?>) {
                List<?> list = (List<?>) obj;
                return isPermutation(inputList, list);
            }
            return false;
        }

        @Override public String toString() {
            return "permutations(" + inputList + ")";
        }

    }

    private static final class PermutationCollectionProbabilistic<E>
            extends AbstractCollection<List<E>> {
        final ImmutableList<E> inputList;
        final EventDistributionCalculator eventDistributionCalculator;

        PermutationCollectionProbabilistic(ImmutableList<E> input, EventDistributionCalculator eventDistributionCalculator) {
            this.inputList = input;
            this.eventDistributionCalculator = eventDistributionCalculator;
        }

        PermutationCollectionProbabilistic(ImmutableList<E> input) {
            this.inputList = input;
            this.eventDistributionCalculator = null;
        }

        @Override public int size() {
            return safeIntFactorial(inputList.size());
        }

        @Override public boolean isEmpty() {
            return false;
        }

        @Override public Iterator<List<E>> iterator() {
            return new PermutationIteratorProbabilistic<>(inputList, eventDistributionCalculator);
        }

        @Override public boolean contains(@Nullable Object obj) {
            if (obj instanceof List<?>) {
                List<?> list = (List<?>) obj;
                return isPermutation(inputList, list);
            }
            return false;
        }

        @Override public String toString() {
            return "permutations(" + inputList + ")";
        }

    }

    private static class PermutationIterator<E>
            extends AbstractIterator<List<E>> {
        final EventDistributionCalculator eventDistributionCalculator;
        final List<E> list;
        final int[] c;
        final int[] o;
        int j;

        PermutationIterator(List<E> list, EventDistributionCalculator eventDistributionCalculator) {
            this.eventDistributionCalculator = eventDistributionCalculator;
            this.list = new ArrayList<E>(list);
            int n = list.size();
            c = new int[n];
            o = new int[n];
            for (int i = 0; i < n; i++) {
                c[i] = 0;
                o[i] = 1;
            }
            j = Integer.MAX_VALUE;
        }

        @Override protected List<E> computeNext() {
            if (j <= 0) {
                return endOfData();
            }
            ImmutableList<E> next = ImmutableList.copyOf(list);
            calculateNextPermutation();
            return next;
        }

        void calculateNextPermutation() {
            j = list.size() - 1;
            int s = 0;

            // Handle the special case of an empty list. Skip the calculation of the
            // next permutation.
            if (j == -1) {
                return;
            }

            boolean swapped = true;
            int initiator = -1;
            int terminator = -1;
            while (true) {
                int q = c[j] + o[j];
                if (q < 0) {
                    switchDirection();
                    continue;
                }
                if (q == j + 1) {
                    if (j == 0) {
                        break;
                    }
                    s++;
                    switchDirection();
                    continue;
                }

                int swap1 = j - c[j] + s;
                int swap2 = j - q + s;
                Collections.swap(list, swap1, swap2);
                c[j] = q;

                if(eventDistributionCalculator != null) {
                    boolean skip = false;
                    if (!swapped && (initiator != swap1 && initiator != swap2 && terminator != swap1 && terminator != swap2)) {
                        skip = true;
                    }

                    if (!skip && eventDistributionCalculator.computeLikelihood((List<XEvent>) list) == 0) {
                        initiator = eventDistributionCalculator.getInitiator();
                        terminator = eventDistributionCalculator.getTerminator();
                        swapped = false;
                        skip = true;
                    }

                    if(skip) {
                        j = list.size() - 1;
                        s = 0;
                        continue;
                    }
                }

                break;
            }
        }

        void switchDirection() {
            o[j] = -o[j];
            j--;
        }
    }

    private static class PermutationIteratorProbabilistic<E>
            extends AbstractIterator<List<E>> {
        final EventDistributionCalculator eventDistributionCalculator;
        final List<E> list;
        double best = 0;
        final int[] c;
        final int[] o;
        int j;

        PermutationIteratorProbabilistic(List<E> list, EventDistributionCalculator eventDistributionCalculator) {
            this.eventDistributionCalculator = eventDistributionCalculator;
            this.list = new ArrayList<E>(list);
            int n = list.size();
            c = new int[n];
            o = new int[n];
            for (int i = 0; i < n; i++) {
                c[i] = 0;
                o[i] = 1;
            }
            j = Integer.MAX_VALUE;
        }

        @Override protected List<E> computeNext() {
            if (j <= 0) {
                return endOfData();
            }
            ImmutableList<E> next = ImmutableList.copyOf(list);
            calculateNextPermutation();
            return next;
        }

        void calculateNextPermutation() {
            j = list.size() - 1;
            int s = 0;

            // Handle the special case of an empty list. Skip the calculation of the
            // next permutation.
            if (j == -1) {
                return;
            }

            boolean swapped = true;
            int initiator = -1;
            int terminator = -1;
            while (true) {
                int q = c[j] + o[j];
                if (q < 0) {
                    switchDirection();
                    continue;
                }
                if (q == j + 1) {
                    if (j == 0) {
                        break;
                    }
                    s++;
                    switchDirection();
                    continue;
                }

                int swap1 = j - c[j] + s;
                int swap2 = j - q + s;
                Collections.swap(list, swap1, swap2);
                c[j] = q;

                if(eventDistributionCalculator != null) {
                    boolean skip = false;
                    if (!swapped && (initiator != swap1 && initiator != swap2 && terminator != swap1 && terminator != swap2)) {
                        j = list.size() - 1;
                        s = 0;
                        continue;
                    }

                    if (!skip) {
                        double likelihood = eventDistributionCalculator.computeLikelihood((List<XEvent>) list, best);
                        if(likelihood == 0 || likelihood < best) {
                            initiator = eventDistributionCalculator.getInitiator();
                            terminator = eventDistributionCalculator.getTerminator();
                            swapped = false;
                            if(likelihood > 0) System.out.println("SKIP");
                            j = list.size() - 1;
                            s = 0;
                            continue;
                        }else {
                            best = likelihood;
                            System.out.println(likelihood);
                        }
                    }
                }

                break;
            }
        }

        void switchDirection() {
            o[j] = -o[j];
            j--;
        }
    }

    /**
     * Returns {@code true} if the second list is a permutation of the first.
     */
    private static boolean isPermutation(List<?> first,
                                         List<?> second) {
        if (first.size() != second.size()) {
            return false;
        }
        Multiset<?> firstSet = HashMultiset.create(first);
        Multiset<?> secondSet = HashMultiset.create(second);
        return firstSet.equals(secondSet);
    }

    // TODO(user): Maybe move the mathematical methods to a separate
    // package-permission class.
    /**
     * We could do a better job if we reused a library function here.
     * We do not check for overflow here. The caller will do it, since the result
     * will be narrowed to an int anyway.
     */
    private static long binomialCoefficient(int n, int k) {
        checkArgument(n >= k);
        if (k > n - k) {
            return factorialQuotient(n, k) / factorial(n - k);
        } else {
            return factorialQuotient(n, n - k) / factorial(k);
        }
    }

    private static boolean isPositiveInt(long n) {
        return n >= 0 && n <= Integer.MAX_VALUE;
    }

    /**
     * Returns the factorial of n as a int, or Integer.MAX_VALUE if the result
     * is too large.
     */
    private static int safeIntFactorial(int n) {
        checkArgument(n >= 0);
        if (n > 12) {
            return Integer.MAX_VALUE;
        }
        return (int) FACTORIALS[n];
    }

    /** Precalculated factorials */
    private static final long[] FACTORIALS = {
            1L,
            1L,
            2L,
            6L,
            24L,
            120L,
            720L,
            5040L,
            40320L,
            362880L,
            3628800L,
            39916800L,
            479001600L,
            6227020800L,
            87178291200L,
            1307674368000L,
            20922789888000L,
            355687428096000L,
            6402373705728000L,
            121645100408832000L,
            2432902008176640000L };

    /**
     * We could do a better job if we reused a library function here.
     */
    @VisibleForTesting static long factorial(int n) {
        checkArgument(n >= 0);
        checkArgument(n <= 20,
                "Numeric overflow calculating the factorial of: %d", n);
        return FACTORIALS[n];
    }

    /**
     * Returns n! / d!.
     */
    private static long factorialQuotient(int n, int d) {
        checkArgument(n > 0);
        checkArgument(d > 0);
        checkArgument(n >= d);

        long result = 1;
        for (int i = d + 1; i <= n; i++) {
            result *= i;
            if (result < 0) {
                throw new IllegalArgumentException("Numeric overflow");
            }
        }
        return result;
    }
}