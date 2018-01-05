package ggnk.cow;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ErrorCollector;

public class CowArrayListTest {
    @Rule public final ErrorCollector asserts = new ErrorCollector();

    private final List<String> expected = new ArrayList<>();
    private CowList<String> b = new CowArrayList<>();

    @Test
    public void simplePushAndPop() {
        fill(33);

        pop();
        pop();
        assertEqualsExpected("simple pops");
    }

    @Test
    public void shouldSetAnyValue() {
        // two levels of tree
        fill(1024+32+1);

        // in tree
        set(1,"first");
        set(2,"second");

        // in tail
        set(b.size() - 1, "last");
        assertEqualsExpected("setting");
    }

    @Test
    public void pushAndPop() {
        // To make sure clear sets all the variables correctly
        b.clear();

        asserts.checkThat("size", b.size(), is(0));

        fillAndExpect("fill tail", 32);
        fillAndExpect("first tail in", 33);
        fillAndExpect("fill second tail", 64);
        fillAndExpect("move second tail in", 65);
        fillAndExpect("fill first level", 1024+32);
        fillAndExpect("overflow first level", 1024+32+1);
        fillAndExpect("fill second level", 32768+32);
        fillAndExpect("overflow second level", 32768+32+1);
        fillAndExpect("fill third level", 32768+32);
        fillAndExpect("overflow third level", 1048576+32+1);

        fill(32768+32+10);
        removeAndExpect("remove from tail", 32768+32+9);
        removeAndExpect("collapse second level", 32768+31);
        removeAndExpect("collapse first level", 1024+31);
        removeAndExpect("pre-tail", 32);
        removeAndExpect("all to tail", 31);
        removeAndExpect("emptiness", 0);
    }

    @Test
    public void removeTailInBulk() {
        fill(1024+32+1);
        int end = expected.size();
        expected.subList(25, end).clear();
        b.subList(25, end).clear();

        assertEqualsExpected("cleared a lot from the end");

        expected.subList(5, 25).clear();
        b.subList(5, 25).clear();

        assertEqualsExpected("cleared from the tail");
    }

    @Test
    public void removeFromMiddle() {
        fill(1024+32+1);
        expected.subList(36, 98).clear();
        b.subList(36, 98).clear();

        expected.remove(69);
        b.remove(69);

        assertEqualsExpected("removed range in middle");
    }

    @Test
    public void addInTheMiddle() {
        fill(1024+32+1);

        List<String> middle = Arrays.asList("one", "two", "three");

        expected.addAll(101, middle);
        b.addAll(101, middle);

        expected.add(101, "joker");
        b.add(101, "joker");

        assertEqualsExpected("added collection in middle");
    }

    @Test
    public void shrinkAfterBuild() {
        fill(1024+32+1);
        b = b.fork();

        assertEqualsExpected("built", b);
        removeAndExpect("more removal", 99);

    }

    public void fillAndExpect(String reason, int high) {
        fill(high);
        assertEqualsExpected(reason);
    }

    public void fill(int high) {
        for (int i = expected.size(); i < high; i++) {
            push(i);
        }
    }

    public void push(int index) {
        String x = Integer.toHexString(index);
        b.add(x);
        expected.add(x);
    }

    public void set(int index, String value) {
        String ex = expected.set(index, value);
        String actual = b.set(index, value);
        assertThat("index "+index, actual, is(ex));
    }

    public void removeAndExpect(String reason, int high) {
        for (int i = expected.size() - 1; i >= high; i--) {
            remove(i);
        }
        assertEqualsExpected(reason);
    }

    private String pop() {
        return remove(expected.size() - 1);
    }

    private String remove(int i) {
        String ex = expected.remove(i);
        String actual = b.remove(i);
        assertThat("index "+i, actual, is(ex));
        return actual;
    }

    public void assertEqualsExpected(String reason) {
        assertEqualsExpected(reason, this.b);
    }

    public void assertEqualsExpected(String reason, List<String> other) {
        asserts.checkThat(reason, other.size(), is(expected.size()));
        asserts.checkThat(reason, other, is(expected));
    }
}
