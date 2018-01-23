package com.github.grignaak.collections;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ToListCollectorTest {
    @Test
    public void testUsage() {
        List<Integer> numbers = Arrays.asList(-2, -1, 0, 1, 2);

        final List<Integer> expected = Arrays.asList(0, 1, 2);

        List<Integer> actual = numbers.stream()
                .filter(n -> n >= 0)
                .collect(new ToListCollector<>());

        assertTrue(actual instanceof CowList);
        assertEquals(expected, actual);
    }
}
