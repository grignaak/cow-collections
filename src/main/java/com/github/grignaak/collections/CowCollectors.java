package com.github.grignaak.collections;

import java.util.stream.Collector;
import java.util.stream.Collectors;

public final class CowCollectors {
    public static <T> Collector<T, ?, CowList<T>> toCowList() {
        return Collectors.toCollection(CowArrayList::new);
    }

    private CowCollectors() {
    }
}
