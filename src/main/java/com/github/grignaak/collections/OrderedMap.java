package com.github.grignaak.collections;

import java.util.Map;

import com.github.grignaak.collections.impl.Beta;

/**
 * A map ordered by keys. This is similar to {@link java.util.NavigableMap} but simpler.
 */
@Beta
public interface OrderedMap<K,V> extends Map<K,V> {
    Iterable<Entry<K,V>> descendingEntries();
    Iterable<Entry<K,V>> descendingEntriesBefore(K upperBoundExclusive);
    default Iterable<Entry<K,V>> ascendingEntries() { return entrySet(); }
    Iterable<Entry<K,V>> ascendingEntriesAfter(K lowerBoundExclusive);
}
