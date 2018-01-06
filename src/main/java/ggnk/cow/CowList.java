package ggnk.cow;

import java.util.List;

/**
 * {@inheritDoc}
 */
public interface CowList<E> extends List<E>, CowCollection<E> {
    /**
     * {@inheritDoc}
     */
    @Override CowList<E> fork();
}
