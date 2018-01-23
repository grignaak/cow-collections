package com.github.grignaak.collections;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

class ToForkedListCollector<T> implements Collector<T, CowList<T>, List<T>> {
    @Override
    public Supplier<CowList<T>> supplier() {
        return CowArrayList::new;
    }

    @Override
    public BiConsumer<CowList<T>, T> accumulator() {
        return List::add;
    }

    @Override
    public BinaryOperator<CowList<T>> combiner() {
        return (left, right) -> {
            left.addAll(right);
            return left;
        };
    }

    @Override
    public Function<CowList<T>, List<T>> finisher() {
        return CowList::fork;
    }

    @Override
    public Set<Characteristics> characteristics() {
        return Collections.emptySet();
    }
}
