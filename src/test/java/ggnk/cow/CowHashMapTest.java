package ggnk.cow;

import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ErrorCollector;

public class CowHashMapTest {
    @Rule public final ErrorCollector errors = new ErrorCollector();

    private final CowMap<Object, Integer> b = new CowHashMap<>();
    private final Set<Entry<Object, Integer>> es = b.entrySet();
    private final Set<Object> ks = b.keySet();

    @Test
    public void emptiness() {
        checkThat(b.size(), is(0));
        checkThat(b, not(hasKey(nullValue())));
        checkThat(b, not(hasKey("hello")));
        checkThat(es.iterator().hasNext(), is(false));
    }

    @Test
    public void canAddNullKey() {
        Integer before = b.put(null, null);

        checkThat(before, nullValue());
        checkThat(b.size(), is(1));
        checkThat(b.containsKey(null), is(true));
        checkThat(b, hasKey(nullValue()));
        checkThat(b, hasEntry(nullValue(), nullValue()));
        checkThat(b, not(hasKey("hello")));


        assertThat(es.iterator().hasNext(), is(true));
        checkThat(es.iterator().next(), is(kv(null, null)));

        // Part 2: replace the null

        before = b.put(null, 1);

        checkThat(before, nullValue());
        checkThat(b.size(), is(1));
        checkThat(b, hasKey(nullValue()));
        checkThat(b, hasEntry(nullValue(), is(1)));
        checkThat(ks.contains(null), is(true));

        assertThat(es.iterator().hasNext(), is(true));
        checkThat(es.iterator().next(), is(kv(null, 1)));

        // Part 3: replace with null

        before = b.put(null, null);

        checkThat(before, is(1));
        checkThat(b.size(), is(1));
        checkThat(b, hasKey(nullValue()));
        checkThat(b, hasEntry(nullValue(), nullValue()));

        assertThat(es.iterator().hasNext(), is(true));
        checkThat(es.iterator().next(), is(kv(null, null)));

    }

    private static class Hasher {
        private final int hash;

        Hasher(int hash) {
            this.hash = hash;
        }

        @Override
        public int hashCode() { return hash; }

        @Override
        public String toString() { return Integer.toString(hash); }
    }

    @Test
    public void canAddOneValue() {
        Integer before = b.put("key", null);

        checkThat(before, nullValue());
        checkThat(b.size(), is(1));
        checkThat(b.containsKey("key"), is(true));
        checkThat(b, hasKey("key"));
        checkThat(b, hasEntry(is("key"), nullValue()));
        checkThat(b, not(hasKey("hello")));


        assertThat(es.iterator().hasNext(), is(true));
        checkThat(es.iterator().next(), is(kv("key", null)));

        // Part 2: replace the null

        before = b.put("key", 1);

        checkThat(before, nullValue());
        checkThat(b.size(), is(1));
        checkThat(b, hasKey("key"));
        checkThat(b, hasEntry(is("key"), is(1)));

        assertThat(es.iterator().hasNext(), is(true));
        checkThat(es.iterator().next(), is(kv("key", 1)));

        // Part 3: replace with null

        before = b.put("key", null);

        checkThat(before, is(1));
        checkThat(b.size(), is(1));
        checkThat(b, hasKey("key"));
        checkThat(b, hasEntry(is("key"), nullValue()));

        assertThat(es.iterator().hasNext(), is(true));
        checkThat(es.iterator().next(), is(kv("key", null)));
        checkThat(ks.contains("key"), is(true));
    }

    @Test
    public void canAddTwoValues() {
        b.put("first", 1);
        Integer before = b.put("second", 2);

        checkThat(before, nullValue());
        checkThat(b.size(), is(2));
        checkThat(b, hasEntry("first", 1));
        checkThat(b, hasEntry("second", 2));
    }

    @Test
    public void canPutTwoValuesOfSameHashAtLevel() {
        Hasher first = new Hasher(0b00000_00001);
        Hasher second = new Hasher(0b00001_00001);

        b.put(first, 1);
        Integer before = b.put(second, 2);

        checkThat(before, nullValue());
        checkThat(b.size(), is(2));
        checkThat(b, hasEntry(first, 1));
        checkThat(b, hasEntry(second, 2));

        // Make sure no artifacts were left behind the merge
        checkThat(copyKeys().size(), is(2));
    }

    @Test
    public void canPutTwoValuesOfSameHashAtSecondLevel() {
        Hasher first = new Hasher(0b00000_00000_00001);
        Hasher second = new Hasher(0b00001_00000_00001);

        b.put(first, 1);
        Integer before = b.put(second, 2);

        checkThat(before, nullValue());
        checkThat(b.size(), is(2));
        checkThat(b, hasEntry(first, 1));
        checkThat(b, hasEntry(second, 2));

        // Make sure no artifacts were left behind the merge
        checkThat(copyKeys().size(), is(2));
    }

    @Test
    public void canUpdateValueOfSameHash() {
        Hasher first = new Hasher(0);
        Hasher second = new Hasher(0);

        b.put(first, 1);
        b.put(second, 2);
        Object before = b.put(first, 3);

        checkThat(before, is(1));
        checkThat(b.size(), is(2));
        checkThat(b, hasEntry(first, 3));
        checkThat(b, hasEntry(second, 2));

        checkThat(copyKeys().size(), is(2));
    }

    @Test
    public void canPutThreeValuesOfSameHashes() {
        Hasher first =  new Hasher(0);
        Hasher second = new Hasher(0);
        Hasher third = new Hasher(0);

        b.put(first, 1);

        // The second makes a full-depth tree with a hash-collision node
        Integer before = b.put(second, 2);

        checkThat(before, nullValue());
        checkThat(b.size(), is(2));
        checkThat(b, hasEntry(first, 1));
        checkThat(b, hasEntry(second, 2));
        checkThat(b.get(first), is(1));
        checkThat(b.get(second), is(2));

        // Make sure no artifacts were left behind the merge
        checkThat(copyKeys().size(), is(2));


        // The third makes adds to the hash-collision node
        before = b.put(third, 3);

        checkThat(before, nullValue());
        checkThat(b.size(), is(3));
        checkThat(b, hasEntry(first, 1));
        checkThat(b, hasEntry(second, 2));
        checkThat(b, hasEntry(third, 3));
        checkThat(b.get(first), is(1));
        checkThat(b.get(second), is(2));
        checkThat(b.get(third), is(3));

        // Make sure no artifacts were left behind the merge
        checkThat(copyKeys().size(), is(3));
    }

    /**
     * Check inlining when there are multiple elements already at the level.
     *
     * We set up the initial array to look like this:
     *
     * <code><pre>
     *
     * [ a d f | X ]
     *            \
     *             [ b c ]
     *
     * </pre></code>
     *
     * We then add {@code e} to produce the following:
     *
     * <code><pre>
     *
     * [ a f | Y X ]
     *        /   \
     * [ d e ]     [ b c ]
     *
     * </pre></code>
     *
     * We then add {@code g} to produce the following:
     * <code><pre>
     *
     *   [ a | Z Y X ]
     *        /   \ \
     * [ f g ]     \ [ b c ]
     *              [ d e ]
     * </pre></code>
     *
     */
    @Test
    public void makeSureWeGetMigratingToInlineNodesCorrect() {
        Hasher A = new Hasher(0b00000_00000),
               B = new Hasher(0b00000_00010),
               C = new Hasher(0b00100_00010),
               D = new Hasher(0b00000_00100),
               E = new Hasher(0b00010_00100),
               F = new Hasher(0b00000_01000),
               G = new Hasher(0b00010_01000);

        b.put(A, 1);
        b.put(D, 4);
        b.put(F, 6);

        b.put(B, 2);
        b.put(C, 3);

        b.put(E, 5);

        b.put(G, 7);

        checkThat(b.size(), is(7));
        checkThat(copyKeys().size(), is(7));
        checkThat(valueSet().size(), is(7));

        checkThat(b, hasEntry(A, 1));
        checkThat(b, hasEntry(B, 2));
        checkThat(b, hasEntry(C, 3));
        checkThat(b, hasEntry(D, 4));
        checkThat(b, hasEntry(E, 5));
        checkThat(b, hasEntry(F, 6));
        checkThat(b, hasEntry(G, 7));
    }

    @Test
    public void canDeleteSomethingNotInBitmapNode() {
        // The root node is a bitmap index node
        b.put("a", 1);
        b.put("b", 2);

        Object before = b.remove(new Hasher(0));

        checkThat(before, nullValue());
        checkThat(b, hasEntry("a", 1));
        checkThat(b, hasEntry("b", 2));
        checkThat(b.size(), is(2));
    }

    @Test
    public void canDeleteSomethingNotInCollisionNode() {
        Hasher A = new Hasher(0);
        Hasher B = new Hasher(0);

        b.put(A, 1);
        b.put(B, 2);

        Integer before = b.remove(new Hasher(0));

        checkThat(before, nullValue());
        checkThat(b, hasEntry(A, 1));
        checkThat(b, hasEntry(B, 2));
        checkThat(b.size(), is(2));
    }

    @Test
    public void canDeleteLastKeyFromRootNode() {
        b.put("thing", 1);
        b.remove("thing");

        checkThat(b.size(), is(0));
        checkThat(copyKeys(b).size(), is(0));
    }

    @Test
    public void canLeaveFirstKeyInRootNode() {
        Hasher A = new Hasher(1);
        Hasher B = new Hasher(2);

        b.put(A, 1);
        b.put(B, 2);

        b.remove(A);

        checkThat(b.size(), is(1));
        checkThat(copyKeys(b).size(), is(1));
        checkThat(b, hasEntry(B, 2));
    }

    @Test
    public void canLeaveSecondKeyInRootNode() {
        Hasher A = new Hasher(1);
        Hasher B = new Hasher(2);

        b.put(A, 1);
        b.put(B, 2);

        b.remove(B);

        checkThat(b.size(), is(1));
        checkThat(copyKeys(b).size(), is(1));
        checkThat(b, hasEntry(A, 1));
    }

    /**
     * <code><pre>
     * [ N ]
     *    \
     *     [ k v k v k v ]
     * </pre></code>
     *
     * becomes
     *
     * <code><pre>
     * [ N ]
     *    \
     *     [ k v k v ]
     * </pre></code>
     */
    @Test
    public void canDeleteFromChildNode() {
        Hasher A = new Hasher(0b00000_00000);
        Hasher B = new Hasher(0b00001_00000);
        Hasher C = new Hasher(0b00011_00000);

        b.put(A, 1);
        b.put(B, 2);
        b.put(C, 3);

        b.remove(B);

        checkThat(b.size(), is(2));
        checkThat(copyKeys(b).size(), is(2));
        checkThat(b, hasEntry(A, 1));
        checkThat(b, hasEntry(C, 3));
    }

    /**
     * <code><pre>
     * [ N ]
     *    .
     *     .
     *      .
     *       [ k v k v k v ]
     * </pre></code>
     *
     * becomes
     *
     * <code><pre>
     * [ N ]
     *    .
     *     .
     *      .
     *       [ k v k v ]
     * </pre></code>
     */
    @Test
    public void canDeleteFromHashNode() {
        Hasher A = new Hasher(0);
        Hasher B = new Hasher(0);
        Hasher C = new Hasher(0);

        b.put(A, 1);
        b.put(B, 2);
        b.put(C, 3);

        b.remove(B);

        checkThat(b.size(), is(2));
        checkThat(copyKeys(b).size(), is(2));
        checkThat(b, hasEntry(A, 1));
        checkThat(b, hasEntry(C, 3));
    }

    /**
     * <code><pre>
     * [ N ]
     *    \
     *     [ k v k v ]
     * </pre></code>
     *
     * becomes
     *
     * <code><pre>
     * [ N ]
     *    \
     *     [ k v ]
     * </pre></code>
     */
    @Test
    public void canFoldFromChildNode() {
        Hasher A = new Hasher(0b00000_00000);
        Hasher B = new Hasher(0b00001_00000);

        b.put(A, 1);
        b.put(B, 2);

        b.remove(B);

        checkThat(b.size(), is(1));
        checkThat(copyKeys(b).size(), is(1));
        checkThat(b, hasEntry(A, 1));
    }

    /**
     * <code><pre>
     * [ N ]
     *    \
     *     [ k v k v ]
     * </pre></code>
     *
     * becomes
     *
     * <code><pre>
     * [ N ]
     *    \
     *     [ k v | N ]
     *              \
     *               [ k v k v ]
     * </pre></code>
     */
    @Test
    public void hashNodeCanBecomeChildOfNewNode() {
        Hasher A = new Hasher(0b00000_00000),
               B = new Hasher(0b00000_00000),
               C = new Hasher(0b00001_00000);

        b.put(A, 1);
        b.put(B, 2);
        b.put(C, 3);

        checkThat(b.size(), is(3));
        checkThat(copyKeys(b).size(), is(3));
        checkThat(valueSet(b).size(), is(3));
    }

    @Test
    public void attemptAFullLengthTree() {
        Hasher A = new Hasher(0b00_00000_00000_00000_00000_00000_00000),
               B = new Hasher(0b10_00000_00000_00000_00000_00000_00000);

        b.put(A, 1);
        b.put(B, 2);

        checkThat(b.size(), is(2));
        checkThat(copyKeys(b).size(), is(2));
        checkThat(valueSet(b).size(), is(2));
        checkThat(b, hasEntry(A, 1));
        checkThat(b, hasEntry(B, 2));
    }

    @Test
    public void canDeleteFromIterators() {
        b.put("a", 1);
        b.put("b", 2);
        b.put("c", 3);

        Iterator<?> it = b.entrySet().iterator();
        assertTrue(it.hasNext());
        it.next();
        it.remove();

        checkThat(b.size(), is(2));

        it = b.keySet().iterator();
        assertTrue(it.hasNext());
        it.next();
        it.remove();

        checkThat(b.size(), is(1));

        it = b.values().iterator();
        assertTrue(it.hasNext());
        it.next();
        it.remove();

        checkThat(b.size(), is(0));
    }

    @Test
    public void cannotModifyBuiltBitmapNodes() {
        b.put("a", 1);

        CowMap<Object, Integer> c = b.fork();

        CowMap<Object, Integer> d = b.fork();
        d.put("b", 2);
        c.put("c", 3);

        checkThat(b.size(), is(1));
        checkThat(b, hasEntry("a", 1));

        checkThat(c.size(), is(2));

        checkThat(d.size(), is(2));
        checkThat(d, hasEntry("a", 1));
        checkThat(d, hasEntry("b", 2));
    }

    @Test
    public void cannotModifyBuiltHashNodes() {
        Hasher A = new Hasher(0);
        Hasher B = new Hasher(0);
        Hasher C = new Hasher(0);
        Hasher D = new Hasher(0);

        b.put(A, 1);
        b.put(B, 2);

        CowMap<Object, Integer> c = b.fork();

        CowMap<Object, Integer> d = b.fork();
        d.put(C, 3);
        c.put(D, 4);

        checkThat(b.size(), is(2));
        checkThat(b, hasEntry(A, 1));
        checkThat(b, hasEntry(B, 2));

        checkThat(c.size(), is(3));

        checkThat(d.size(), is(3));
        checkThat(d, hasEntry(A, 1));
        checkThat(d, hasEntry(B, 2));
        checkThat(d, hasEntry(C, 3));
    }

    @Test
    public void exerciseNewNodeCases() {
        // hashing
        Hasher A = new Hasher(0b00000_00000);
        Hasher B = new Hasher(0b00000_00000);

        Hasher C = new Hasher(0b00000_00001);

        // merging
        Hasher D = new Hasher(0b00010_00010);
        Hasher E = new Hasher(0b00001_00010);

        CowMap<Object, Integer> cow = b.fork();
        cow.put(A, 1);
        cow.put(B, 2);
        cow.put(C, 3);
        cow.put(D, 4);
        cow.put(E, 5);

        // updates
        cow.put(B, 7);
        cow.put(C, 8);

        checkThat(cow.size(), is(5));
        checkThat(cow, hasEntry(A, 1));
        checkThat(cow, hasEntry(B, 7));
        checkThat(cow, hasEntry(C, 8));
        checkThat(cow, hasEntry(D, 4));
        checkThat(cow, hasEntry(E, 5));

        cow.remove(A);
        cow.remove(B);
        cow.remove(D);
        cow.remove(E);

        checkThat(cow.size(), is(1));
        checkThat(cow, hasEntry(C, 8));
    }

    @Test
    public void equalityAndHashCode() {
        Hasher A = new Hasher(0b00000_00000);
        Hasher B = new Hasher(0b00000_00000);

        Hasher C = new Hasher(0b00000_00001);

        CowMap<Object, Integer> c = b.fork();

        b.put(A, 1);
        b.put(B, 2);
        b.put(C, 3);

        c.put(A, 1);
        c.put(B, 2);

        assertThat(b, is(new HashMap<>(b)));
        assertThat(b, Matchers.not(c));
        assertThat(b.hashCode(), not(c.hashCode()));

        c.put(C, 3);

        assertThat(b, is(c));
        assertThat(b.hashCode(), is(c.hashCode()));
    }

    private void checkFails(String description, Runnable block) {
        try {
            block.run();
        } catch (Exception e) {
            return; //success
        }
        errors.addError(new AssertionError("expected to fail " + description));
    }

    private Collection<?> copyKeys() {
        return copyKeys(b);
    }
    private <K> Collection<K> copyKeys(Map<K, ?> b) {
        return new ArrayList<>(b.keySet());
    }

    private Collection<?> valueSet() {
        return valueSet(b);
    }
    private <V> Collection<V> valueSet(Map<?, V> b) {
        return new HashSet<>(b.values());
    }

    private <T> void checkThat(T value, Matcher<T> matcher) {
        errors.checkThat(value, matcher);
    }

    private static <K,V> Map.Entry<K, V> kv(K k, V v) {
        return new SimpleImmutableEntry<>(k, v);
    }
}
