package com.github.grignaak.collections;

import java.util.List;
import java.util.stream.Collector;

public final class CowCollectors {
    public static <T> Collector<T, ?, List<T>> toList() {
        return new ToListCollector<>();
    }

    public static <T> Collector<T, ?, List<T>> toForkedList() {
        return new ToForkedListCollector<>();
    }

    private CowCollectors() {
    }
}
