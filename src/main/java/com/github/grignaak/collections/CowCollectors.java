package com.github.grignaak.collections;

import java.util.Set;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import static java.util.stream.Collector.Characteristics;

public final class CowCollectors {
    public static <T> Collector<T, ?, CowList<T>> toCowList() {
        return Collectors.toCollection(CowArrayList::new);
    }

    public static <T> Collector<T, ?, CowSet<T>> toCowSet() {
        BinaryOperator<CowSet<T>> combiner = (left, right) -> {
            left.addAll(right);
            return left;
        };

        return Collector.of(
                CowHashSet::new,
                Set::add,
                combiner,
                Function.identity(),
                Characteristics.UNORDERED, Characteristics.IDENTITY_FINISH);
    }

    private CowCollectors() {
    }
}
