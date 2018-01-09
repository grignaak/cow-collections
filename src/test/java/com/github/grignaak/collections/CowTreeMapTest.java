package com.github.grignaak.collections;

import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertThat;
import static org.junit.Assume.assumeThat;

import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.IntStream;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ErrorCollector;
import org.junit.rules.ExpectedException;

public class CowTreeMapTest {
    @Rule public final ErrorCollector asserts = new ErrorCollector();
    @Rule public final ExpectedException thrown = ExpectedException.none();

    private CowOrderedMap<String, Object> b = new CowTreeMap<>(Comparator.<String>naturalOrder());

    @Test
    public void actOnEmptyMap() {
        asserts.checkThat(b.size(), is(0));
        asserts.checkThat(b.remove("no"), nullValue());
        // just in case the root was destroyed
        asserts.checkThat(b.remove("no"), nullValue());

        asserts.checkThat(b.containsKey("no"), is(false));
        asserts.checkThat(b.get("no"), nullValue());
        asserts.checkThat(b, not(hasEntry("no", "no")));
    }

    @Test
    public void shouldPutAndReplaceAndRemoveFirstItem() {
        Object before = b.put("first!", "hi");

        asserts.checkThat(b.size(), is(1));
        asserts.checkThat(before, nullValue());
        asserts.checkThat(b.get("first!"), is("hi"));
        asserts.checkThat(b.containsKey("first!"), is(true));
        asserts.checkThat(b, hasEntry("first!", "hi"));

        before = b.put("first!", "again");
        asserts.checkThat(b.size(), is(1));
        asserts.checkThat(before, is("hi"));
        asserts.checkThat(b.get("first!"), is("again"));
        asserts.checkThat(b.containsKey("first!"), is(true));
        asserts.checkThat(b, hasEntry("first!", "again"));

        boolean removed = b.remove("first!", "NOT_ME");
        assertThat(removed, is(false));
        asserts.checkThat(b.size(), is(1));
        asserts.checkThat(before, is("hi"));
        asserts.checkThat(b.get("first!"), is("again"));
        asserts.checkThat(b.containsKey("first!"), is(true));
        asserts.checkThat(b, hasEntry("first!", "again"));

        before = b.remove("first!");
        asserts.checkThat(b.size(), is(0));
        asserts.checkThat(before, is("again"));
        asserts.checkThat(b.get("first!"), nullValue());
        asserts.checkThat(b.containsKey("first!"), is(false));
        asserts.checkThat(b, not(hasEntry("first!", "hi")));
    }

    @Test
    public void shouldPutAndReplaceAndRemoveSecondItem() {
        b.put("first!", "hi");
        Object before = b.put("second!", "ʕ•ᴥ•ʔ");

        asserts.checkThat(b.size(), is(2));
        asserts.checkThat(before, nullValue());
        asserts.checkThat(b.get("first!"), is("hi"));
        asserts.checkThat(b.get("second!"), is("ʕ•ᴥ•ʔ"));
        asserts.checkThat(b.containsKey("first!"), is(true));
        asserts.checkThat(b.containsKey("second!"), is(true));
        asserts.checkThat(b, hasEntry("first!", "hi"));
        asserts.checkThat(b, hasEntry("second!", "ʕ•ᴥ•ʔ"));

        before = b.put("second!", "again");
        asserts.checkThat(b.size(), is(2));
        asserts.checkThat(before, is("ʕ•ᴥ•ʔ"));
        asserts.checkThat(b.get("first!"), is("hi"));
        asserts.checkThat(b.get("second!"), is("again"));
        asserts.checkThat(b.containsKey("first!"), is(true));
        asserts.checkThat(b.containsKey("second!"), is(true));
        asserts.checkThat(b, hasEntry("first!", "hi"));
        asserts.checkThat(b, hasEntry("second!", "again"));

        before = b.remove("second!");
        asserts.checkThat(b.size(), is(1));
        asserts.checkThat(before, is("again"));
        asserts.checkThat(b.get("first!"), is("hi"));
        asserts.checkThat(b.get("second!"), nullValue());
        asserts.checkThat(b.containsKey("first!"), is(true));
        asserts.checkThat(b.containsKey("second!"), is(false));
        asserts.checkThat(b, hasEntry("first!", "hi"));
    }

    @Test
    public void insertAndRemoveAscending() {
        String[] elements = range(99);

        Map<String, String> expected = new TreeMap<>();
        for (String e : elements) {
            expected.put(e, e);
            b.put(e, e);
            assertThat(b, hasEntry(e, e));
        }

        assertThat(b, is(expected));

        for (String e : elements) {
            expected.remove(e);
            b.remove(e);
        }
    }

    @Test
    public void pessimisticCopy() {
        String[] elements = range(99);

        Map<String, String> expected = new TreeMap<>();
        for (String e : elements) {
            expected.put(e, e);
            b.put(e, e);
        }

        assertThat(b, is(expected));

        Map<String, Object> clone = b.fork();
        assertThat(clone, is(expected));
        assertThat(clone, is(not(sameInstance(b))));

        clone.put("one", "two");
        assertThat(b, is(expected));
        assertThat(clone, hasEntry("one", "two"));
    }

    @Test
    public void iteratorFollowsOriginal() {
        String[] elements = range(99);
        for (String e : elements) {
            b.put(e, e);
        }

        int I = 18;
        Iterator<Map.Entry<String, Object>> iter = b.entrySet().iterator();
        List<Object> consumedValues = new ArrayList<>(b.size());

        for (int i = 0; i < I; i++) {
            consumedValues.add( iter.next().getValue() );
        }

        Map<String, Object> other = b.fork();
        other.put(elements[I], "changed-element");

        while (iter.hasNext()) {
            consumedValues.add( iter.next().getValue() );
        }

        assertThat(consumedValues, is(Arrays.asList(elements)));
    }

    private <K,V> Map.Entry<K,V> entry(K key, V value) {
        return new SimpleImmutableEntry<>(key, value);
    }

    @Test
    public void insertAndRemoveDescending() {
        String[] elements = range(99);
        Collections.reverse(Arrays.asList(elements));

        Map<String, String> expected = new TreeMap<>();
        for (String e : elements) {
            expected.put(e, e);
            b.put(e, e);
            assertThat(b, hasEntry(e, e));
        }

        assertThat(b, is(expected));
        for (String e : elements) {
            expected.remove(e);
            b.remove(e);
        }
    }

    @Test
    public void insertAndRemoveFromMiddle() {
        String[] elements = zigZag(99);

        Map<String, String> expected = new TreeMap<>();
        for (String e : elements) {
            expected.put(e, e);
            b.put(e, e);
            assertThat(b, hasEntry(e, e));
        }

        assertThat(b, is(expected));
        for (String e : elements) {
            expected.remove(e);
            b.remove(e);
            assertThat(b, is(expected));
        }
    }

    @Test
    public void insertFromEdgesAndRemoveFromMiddle() {
        String[] elements = zigZag(99);

        Map<String, String> expected = new TreeMap<>();

        Collections.reverse(Arrays.asList(elements));
        for (String e : elements) {
            expected.put(e, e);
            b.put(e, e);
            assertThat(b, hasEntry(e, e));
        }
        assertThat(b, is(expected));

        Collections.reverse(Arrays.asList(elements));
        for (String e : elements) {
            expected.remove(e);
            b.remove(e);
            assertThat(b, is(expected));
        }
    }

    @Test
    public void insertAndRemoveFromEdges() {
        String[] elements = zigZag(99);
        Collections.reverse(Arrays.asList(elements));

        Map<String, String> expected = new TreeMap<>();

        for (String e : elements) {
            expected.put(e, e);
            b.put(e, e);
            assertThat(b, hasEntry(e, e));
        }
        assertThat(b, is(expected));

        for (String e : elements) {
            expected.remove(e);
            b.remove(e);
            assertThat(b, is(expected));
        }
    }

    @Test
    public void insertFromMiddleAndRemoveFromEdges() {
        String[] elements = zigZag(99);

        Map<String, String> expected = new TreeMap<>();

        for (String e : elements) {
            expected.put(e, e);
            b.put(e, e);
            assertThat(b, hasEntry(e, e));
        }
        assertThat(b, is(expected));

        Collections.reverse(Arrays.asList(elements));
        for (String e : elements) {
            expected.remove(e);
            b.remove(e);
            assertThat(b, is(expected));
        }
    }

    @Test
    public void removePredecessor() {
        String[] elements = zigZag(99);

        Map<String, String> expected = new TreeMap<>();

        for (String e : elements) {
            expected.put(e, e);
            b.put(e, e);
            assertThat(b, hasEntry(e, e));
        }
        assertThat(b, is(expected));

        assumeThat(expected.remove("06", "06"), is(true));
        b.remove("06");
    }

    private String[] zigZag(int max) {
        assumeThat(max, lessThan(100));

        return IntStream.range(0, max/2)
            .flatMap(i -> IntStream.of(i, -i - 1))
            .mapToObj(i -> String.format("%02d", i+max/2))
            .toArray(String[]::new);
    }

    private String[] range(int max) {
        assumeThat(max, lessThan(100));

        return IntStream.range(0, max)
            .mapToObj(i -> String.format("%02d", i))
            .toArray(String[]::new);
    }

    @Test
    public void iterate() {
        String[] elements = zigZag(99);

        for (String e : elements) {
            b.put(e, e);
        }

        Map<Object, Object> check = new TreeMap<>();
        for (Map.Entry<?,?> o : b.descendingEntriesBefore("99")) {
            check.put(o.getKey(), o.getValue());
        }

        assertThat(b, is(check));
    }
}
