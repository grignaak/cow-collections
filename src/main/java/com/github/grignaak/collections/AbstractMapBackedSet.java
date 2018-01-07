package com.github.grignaak.collections;

import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.Spliterator;

abstract class AbstractMapBackedSet<T> extends AbstractSet<T> implements CowSet<T> {
    protected final CowMap<T, Boolean> impl;

    AbstractMapBackedSet(CowMap<T, Boolean> impl) {
        this.impl = impl;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int size() {
        return impl.size();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isEmpty() {
        return impl.isEmpty();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean contains(Object o) {
        //noinspection SuspiciousMethodCalls
        return impl.containsKey(o);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Iterator<T> iterator() {
        return impl.keySet().iterator();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object[] toArray() {
        return impl.keySet().toArray();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T1> T1[] toArray(T1[] a) {
        return impl.keySet().toArray(a);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean add(T o) {
        return impl.put(o, Boolean.TRUE) != null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean remove(Object o) {
        return impl.remove(o) != null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean containsAll(Collection<?> c) {
        return impl.keySet().containsAll(c);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean retainAll(Collection<?> c) {
        return impl.keySet().retainAll(c);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean removeAll(Collection<?> c) {
        return impl.keySet().removeAll(c);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clear() {
        impl.clear();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object o) {
        return impl.keySet().equals(o);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return impl.keySet().hashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Spliterator<T> spliterator() {
        return impl.keySet().spliterator();
    }
}
