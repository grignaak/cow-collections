package com.github.grignaak.collections;

public class CowHashSet<T> extends AbstractMapBackedSet<T> implements CowSet<T> {
    protected final CowMap<T, Boolean> impl;

    public CowHashSet() {
        this(new CowHashMap<>());
    }

    private CowHashSet(CowMap<T, Boolean> impl) {
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
    public CowHashSet<T> fork() {
        return new CowHashSet<>(impl.fork());
    }
}
