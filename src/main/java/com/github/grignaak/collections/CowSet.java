package com.github.grignaak.collections;

import java.util.Set;

/**
 * {@inheritDoc}
 */
public interface CowSet<E> extends Set<E>, CowCollection<E> {
    /**
     * {@inheritDoc}
     */
    @Override
    CowSet<E> fork();
}
