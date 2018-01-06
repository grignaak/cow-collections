package com.github.grignaak.collections.impl;

/**
 * Can't use Optional as search results b/c null values can also mean "present".
 */
@Beta
public class Search<T> {
    private static final Search<?> NOT_FOUND = new Search<>(null);

    private final T value;

    private Search(T value) {
        this.value = value;
    }

    public boolean isFound() {
        return this != NOT_FOUND;
    }

    @SuppressWarnings("unchecked")
    public static <T> Search<T> notFound() {
        return (Search<T>) NOT_FOUND;
    }

    public static <V> Search<V> found(V value) {
        return new Search<>(value);
    }

    public T value() {
        return value;
    }
}
