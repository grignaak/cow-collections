package com.github.grignaak.collections;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class CowCollectorsTest {
    @Test
    public void testToCowList() {
        List<Integer> numbers = Arrays.asList(-2, -1, 0, 1, 2);

        final List<Integer> expected = Arrays.asList(0, 1, 2);

        CowList<Integer> actual = numbers.stream()
                .filter(number -> number >= 0)
                .collect(CowCollectors.toCowList());

        assertEquals(expected, actual);
    }

    @Test
    public void testToCowSet() {
        Collection<Integer> numbers = Arrays.asList(-1, -1, 2, 3, 4, 4, 5);

        final Set<Integer> expected = new HashSet<>(Arrays.asList(2, 3, 4, 5));

        CowSet<Integer> actual = numbers.stream()
                .filter(number -> number >= 0)
                .collect(CowCollectors.toCowSet());

        assertEquals(expected, actual);
    }

    @Test
    public void testToOrderedCowSet() {
        Collection<Integer> numbers = Arrays.asList(5, 4, 3, 3, -2, -1, 2, 2, -1, 4, 5);

        final Set<Integer> expected = new HashSet<>(Arrays.asList(2, 3, 4, 5));

        CowSet<Integer> actual = numbers.stream()
                .filter(number -> number >= 0)
                .collect(CowCollectors.toOrderedCowSet());

        assertEquals(expected, actual);
    }
}
