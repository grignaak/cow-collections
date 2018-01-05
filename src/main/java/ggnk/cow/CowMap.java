package ggnk.cow;

import java.util.Map;

/**
 * A Copy-On-Write map (or a <em>persistent data structure</em> in the literature.
 *
 * <p>A Copy-On-Write map has a cheap {@link #fork()} method which will return a new instance of the map,
 * but likely sharing the underlying data structure. Even when the data structure is shared, writes to either of the
 * forks are <em>not</em> reflected in the other fork. Forking can happen any number of times.</p>
 *
 * <p>The major purpose of a copy-on-write map is thread safety. A common idiom is to make updates to the collection in
 * one thread and then publish its fork to any number of reader threads. These reader threads can safely read their copy
 * of the fork while concurrent updates are happening.</p>
 *
 * <p><pre>
 *                              __.----.___
 *  ||            ||  (\(__)/)-'||      ;--` ||
 * _||____________||___`(QQ)'___||______;____||_
 * -||------------||----)  (----||-----------||-
 * _||____________||___(o  o)___||______;____||_
 * -||------------||----`--'----||-----------||-
 *  ||            ||       `|| ||| || ||     ||jgs
 * ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
 * </pre></p>
 */
public interface CowMap<K,V> extends Map<K,V>, Forkable {
    CowMap<K, V> fork();
}
