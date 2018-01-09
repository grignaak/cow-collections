package com.github.grignaak.collections;

import java.util.Comparator;

public class CowTreeSet<T> extends AbstractMapBackedSet<T> implements CowSet<T> {
    private final CowTreeMap<T, Boolean> impl;

    public CowTreeSet(Comparator<T> comparator) {
        this(new CowTreeMap<>(comparator));
    }

    private CowTreeSet(CowTreeMap<T, Boolean> impl) {
        this.impl = impl;
    }

    @Override
    protected CowMap<T, Boolean> backingMap() {
        return impl;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CowTreeSet<T> fork() {
        return new CowTreeSet<>(impl.fork());
    }
}
