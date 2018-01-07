package com.github.grignaak.collections;

public class CowHashSet<T> extends AbstractMapBackedSet<T> implements CowSet<T> {
    public CowHashSet() {
        this(new CowHashMap<>());
    }

    private CowHashSet(CowMap<T, Boolean> impl) {
        super(impl);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CowHashSet<T> fork() {
        return new CowHashSet<>(impl.fork());
    }
}
