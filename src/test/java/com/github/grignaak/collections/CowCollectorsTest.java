package com.github.grignaak.collections;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class CowCollectorsTest {
    @Test
    public void testToCowList() {
        List<Integer> numbers = Arrays.asList(-2, -1, 0, 1, 2);

        final List<Integer> expected = Arrays.asList(0, 1, 2);

        CowList<Integer> actual = numbers.stream()
                .filter(n -> n >= 0)
                .collect(CowCollectors.toCowList());

        assertEquals(expected, actual);
    }
}
