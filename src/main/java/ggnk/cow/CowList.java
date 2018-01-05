package ggnk.cow;

import java.util.List;

/**
 * {@inheritDoc}
 *
 * <p><pre>
 *          (    )
 *           (oo)
 *  )\.-----/(O O)
 * # ;       / u
 *   (  .   |} )
 *    |/ `.;|/;
 *    "     " "
 * </pre></p>
 */
public interface CowList<E> extends List<E>, CowCollection<E> {
    @Override CowList<E> fork();
}
