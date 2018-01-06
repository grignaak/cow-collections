package com.github.grignaak.collections;

import com.github.grignaak.collections.impl.Beta;

/**
 * An object that can efficiently fork itself into a copy; typically with structural sharing on large objects.
 *
 * <p>This is similar to {@link Cloneable} but where implementers guarantee some efficiency.</p>
 *
 * @since 0.9.16
 */
@Beta
public interface Forkable {

    /**
     * Create an independent copy of this object, where mutations on the returned instance do not affect this instance
     * and vice versa.
     */
    Forkable fork();
}
