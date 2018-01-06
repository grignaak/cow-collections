package ggnk.cow;

import ggnk.cow.impl.Beta;

/**
 * {@inheritDoc}
 */
@Beta
public interface CowOrderedMap<K,V> extends CowMap<K,V>, OrderedMap<K,V> {
    /**
     * {@inheritDoc}
     */
    @Override CowOrderedMap<K,V> fork();
}
