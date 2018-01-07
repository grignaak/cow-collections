package com.github.grignaak.collections;

import java.util.Comparator;

public class CowTreeSet<T> extends AbstractMapBackedSet<T> implements CowSet<T> {

    public CowTreeSet(Comparator<T> comparator) {
        this(new CowTreeMap<>(comparator));
    }

    private CowTreeSet(CowMap<T, Boolean> impl) {
        super(impl);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CowTreeSet<T> fork() {
        return new CowTreeSet<>(impl.fork());
    }
}
