package com.github.grignaak.collections;

import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.Spliterator;

abstract class AbstractMapBackedSet<T> extends AbstractSet<T> implements CowSet<T> {
    protected abstract CowMap<T, Boolean> backingMap();

    /**
     * {@inheritDoc}
     */
    @Override
    public int size() {
        return backingMap().size();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isEmpty() {
        return backingMap().isEmpty();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean contains(Object o) {
        //noinspection SuspiciousMethodCalls
        return backingMap().containsKey(o);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Iterator<T> iterator() {
        return backingMap().keySet().iterator();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object[] toArray() {
        return backingMap().keySet().toArray();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T1> T1[] toArray(T1[] a) {
        return backingMap().keySet().toArray(a);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean add(T o) {
        return backingMap().put(o, Boolean.TRUE) != null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean remove(Object o) {
        return backingMap().remove(o) != null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean containsAll(Collection<?> c) {
        return backingMap().keySet().containsAll(c);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean retainAll(Collection<?> c) {
        return backingMap().keySet().retainAll(c);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean removeAll(Collection<?> c) {
        return backingMap().keySet().removeAll(c);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clear() {
        backingMap().clear();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object o) {
        return backingMap().keySet().equals(o);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return backingMap().keySet().hashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Spliterator<T> spliterator() {
        return backingMap().keySet().spliterator();
    }
}
