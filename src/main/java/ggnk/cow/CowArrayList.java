package ggnk.cow;

import javax.annotation.CheckForNull;
import java.util.AbstractList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.RandomAccess;

import ggnk.cow.impl.Box;
import ggnk.cow.impl.MoreArrays;

/**
 * An array-based copy-on-write list, where pushing and popping from the end are
 * amortized constant time, access and updates are sub-linear (nearly constant).
 * Insertion and removal from anywhere not the end of the list are linear in time.
 *
 * <pre>
 *  _(__)_        V
 * '-e e -'__,--.__)
 *  (o_o)        )
 *     \. /___.  |
 *     ||| _)/_)/
 * gnv//_(/_(/_(
 * </pre>
 */
public final class CowArrayList<E> extends AbstractList<E> implements CowList<E>, RandomAccess {
    /*
     * For clarification, we use <em>index</em> to refer to an element's location in
     * the entire data structure, and <em>position</em> to refer to an element's location
     * in a specific array. Convert from an index to a position via {@link #valuePosition(int)}
     * and {@link #childPosition(int, int)}
     */
    
    private long generation;

    CowArrayList(long generation, int size, int shift, Node root, Object[] tail) {
        this.generation = generation;
        this.size = size;
        this.shift = shift;
        this.root = root;
        this.tail = tail;
    }

    //region pulled down TODO

    protected static final int LOW_5_BITS_MASK = 0x1f;
    protected static final int HIGH_27_BITS_MASK = ~LOW_5_BITS_MASK;

    /**
     * A 32-way trie node. The leaves at {@code level == 0} hold the data; all other layers house only nodes.
     *
     * All nodes are densely packed; all leaves have arrays of size 32.
     */
    protected static final class Node {
        private final long generation;
        protected Object[] nodes;

        Node(long generation, Object[] nodes) {
            this.generation = generation;
            this.nodes = nodes;
        }

        /**
         * Copy the node if it isn't owned by the editor; should only be used inside Node
         *
         * @return this or a copy of this
         */
        private Node _editable(long generation) {
            return this.generation == generation ? this : new Node(generation, nodes.clone());
        }

        /**
         * Return a potential copy of the array. Use when stealing the array
         * for use in the same builder.
         *
         * <p>That is, this is equivalent to {@code this._editable(editor).nodes}
         * but a node is not created.</p>
         */
        Object[] editableArray(long generation) {
            if (this.generation == generation) {
                return nodes;
            } else {
                return nodes.clone();
            }
        }

        /**
         * Copy the array that houses the index within the tree. Use when
         * stealing the array for use outside this builder.
         *
         * @return this or a copy of this
         */
        Object[] cloneArrayForIndex(int index, int level) {
            return leafNode(index, level).nodes.clone();
        }

        /**
         * Fetch the leaf node holding the given index in the tree. The level
         * tells where in the tree this node is.
         */
        Node leafNode(int index, int level) {
            Node node = this;
            for (int shift = level; shift > 0; shift -= 5) {
                node = (Node) node.nodes[childPosition(index, shift)];
            }
            return node;
        }

        /**
         * Put the child at the position.
         *
         * @return this or a copy of this
         */
        Node putNode(long generation, int nodePosition, Node child) {
            Node me = _editable(generation);
            me.nodes[nodePosition] = child;
            return me;
        }

        /**
         * Append the child.
         *
         * @return this or a copy of this
         */
        Node appendNode(long generation, Node child) {
            Object[] appended = MoreArrays.arrayCopyAndAppend(nodes, child);
            if (this.generation == generation) {
                nodes = appended;
                return this;
            } else {
                return new Node(generation, appended);
            }
        }

        /**
         * Remove the last child.
         *
         * @return this or a copy of this
         */
        Node shrink(long generation) {
            Object[] shrunk = nodes.length == 1 ? EMPTY_ARRAY : MoreArrays.copyToLength(this.nodes, nodes.length - 1);
            if (this.generation == generation) {
                this.nodes = shrunk;
                return this;
            } else {
                return new Node(generation, shrunk);
            }
        }


        /**
         * Put the child at the index into the tree; level giving the depth into the tree.
         *
         * @return this or a copy of this
         */
        <E> Node swapOut(long generation, int index, int level, E value, Box<E> returned) {
            Node me = _editable(generation);
            if (level == 0) {
                returned.box( MoreArrays.swapOut(me.nodes, valuePosition(index), value) );
            } else {
                int pos = childPosition(index, level);
                Node oldChild = (Node) me.nodes[pos];
                Node newChild = oldChild.swapOut(generation, index, level-5, value, returned);
                me.nodes[pos] = newChild;
            }
            return me;
        }

        /**
         * Get the value at the index within the tree; the level tells where this
         * node is within the tree.
         */
        @SuppressWarnings("unchecked")
        <E> E get(int index, int level) {
            return (E) leafNode(index, level).nodes[valuePosition(index)];
        }

        /**
         * Put the leaf at the end of the tree, as given by lastIndex (which should
         * include the values in the leaf)
         *
         * @return this or a copy of this
         */
        Node pushLeaf(long generation, int lastIndex, int level, Node leaf) {
            int pos = childPosition(lastIndex, level);
            if (level == 5) {
                // the penultimate layer!
                return appendNode(generation, leaf);
            } else {
                if (nodes.length == pos) {
                    return appendNode(generation,
                        newPath(generation, level - 5, leaf));
                } else {
                    Node child = (Node) nodes[pos];
                    return putNode(generation, pos,
                        child.pushLeaf(generation, lastIndex, level - 5, leaf));
                }

            }
        }

        /**
         * Remove the last leaf from the tree; putting its values into the box.
         *
         * The values put into the box are editable by the given Owner.
         *
         * @param lastIndex the last index in the tree
         * @return this, a copy of this, or null if this is now empty
         */
        @CheckForNull
        Node popLeaf(long generation, int lastIndex, int level, Box<Object[]> tail) {
            int pos = childPosition(lastIndex, level);
            Node origChild = (Node) nodes[pos];

            if (level == 5) {
                // the penultimate layer!
                tail.box( origChild.editableArray(generation) );
                return pos == 0 ? null : shrink(generation);
            } else {
                Node newChild = origChild.popLeaf(generation, lastIndex, level - 5, tail);
                if (newChild == null) {
                    return pos == 0 ? null : shrink(generation);
                } else {
                    return putNode(generation, pos, newChild);
                }
            }
        }
    }

    protected static final Object[] EMPTY_ARRAY = new Object[0];
    protected static final Node EMPTY_NODE = new Node(-1, EMPTY_ARRAY);

    protected Node root;
    protected Object[] tail;
    protected int size;
    protected int shift;


    static int childPosition(int index, int level) {
        return (index >>> level) & LOW_5_BITS_MASK;
    }

    static int valuePosition(int index) {
        return index & LOW_5_BITS_MASK;
    }

    static Node newPath(long generation, int level, Node leaf) {
        if (level == 0)
            return leaf;
        Node parent = new Node(generation, new Object[1]);
        parent.nodes[0] = newPath(generation, level - 5, leaf);
        return parent;
    }


    @Override
    public int size() {
        return size;
    }

    @Override
    @SuppressWarnings("unchecked")
    public E get(int index) {
        checkIndexBoundsExclusive(index);
        if (index >= tailOffset()) {
            return (E) tail[valuePosition(index)];
        } else {
            return root.get(index, shift);
        }
    }

    protected final void checkIndexBoundsExclusive(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException(String.format(
                "expected an index up to %d; got %d",
                size,  index));
        }
    }

    protected final int tailOffset() {
        return size == 0 ? 0 : (size - 1) & HIGH_27_BITS_MASK;
    }

    protected final boolean isTreeFull(int size) {
        int sizeWithoutTail = size & HIGH_27_BITS_MASK;
        int treeCapacity = 1 << (shift+5);
        return sizeWithoutTail > treeCapacity;
    }

    /**
     * Length of the tail. Since all the nodes have 32 children, the tail
     * starts at {@code (size-1) % 32}.
     */
    protected final int tailLength() {
        return size - tailOffset();
    }

    protected final Object[] trimmedTail(int newLength) {
        return tail.length == newLength ? tail : MoreArrays.copyToLength(tail, newLength);
    }
    //endregion

    //region migrated TODO

    public static <E> CowList<E> create() {
        return new CowArrayList<>(EMPTY_NODE.generation + 1, 0, 5, EMPTY_NODE, new Object[32]);

    }

    @Override
    public CowList<E> fork() {
        return new CowArrayList<>(++generation, size, shift, root, tail.clone());
    }

    //    /**
//     * Make a copy of the iterable, inserting items in the same order as the iteration.
//     *
//     * <p>An attempt is made to share structure with the source, if possible</p>
//     */
//    public static <E> CowList<E> copy(Iterable<E> src) {
//        if (src instanceof CowArrayList<?>) {
//            return (CowList<E>) src;
//        } else if (src instanceof CowArrayList<?>) {
//            CowArrayList<E> that = (CowArrayList<E>) src;
//            return that.pessimisticCopy();
//        } else {
//            Builder<E> builder = newBuilder();
//            for (E e : src) {
//                builder.add(e);
//            }
//            return builder.build();
//        }
//    }
    //endregion


    //region Indexed access

    @Override
    public E set(int index, E value) {
        checkIndexBoundsExclusive(index);

        if (index >= tailOffset()) {
            return MoreArrays.swapOut(tail, valuePosition(index), value);
        } else {
            Box<E> box = new Box<>();
            root = root.swapOut(generation, index, shift, value, box);
            return box.unbox();
        }

        // we don't set modCount here b/c it doesn't really affect iteration.
    }

    //endregion

    //region Addition to the tail - efficient operations

    @Override
    public boolean add(E element) {
        int sz = size;
        if (sz - tailOffset() < 32) {
            // room in the tail!
            tail[valuePosition(sz)] = element;
        } else {
            Node tailNode = new Node(generation, tail);
            tail = new Object[32];
            tail[0] = element;
            pushTail(tailNode);
        }

        // we don't set modCount here b/c it doesn't really affect iteration.

        size++;
        return true;
    }

    private void pushTail(Node tailNode) {
        if (isTreeFull(size)) {
            Node newRoot = new Node(generation, new Object[2]);
            newRoot.nodes[0] = root;
            newRoot.nodes[1] = newPath(generation, shift, tailNode);

            root = newRoot;
            shift += 5;
        } else {
            root = root.pushLeaf(generation, (size - 1), shift, tailNode);
        }
    }

    //endregion

    //region Removal from tail - efficient operations

    private E removeLastFromTail(int index) {
        size--;
        return MoreArrays.swapOut(tail, index, null);
    }

    private E removeLastItemFromTailAndPullTailFromTree() {
        // The tail will be empty, pull one out of the tree
        @SuppressWarnings("unchecked")
        E removed = (E) tail[0];
        size--;
        pullTailFromTree(new Box<>());
        return removed;
    }

    /**
     * Precondition: size reflects an empty tail.
     */
    private void pullTailFromTree(Box<Object[]> newTail) {
        root = root.popLeaf(generation, (size - 1), shift, newTail);
        if (root == null) {
            root = new Node(generation, EMPTY_ARRAY);
        } else if (shift > 5 && root.nodes.length == 1) {
            // collapse a level

            root = (Node) root.nodes[0];
            shift -= 5;
        }

        tail = newTail.unbox();
    }

    private void clear(Object[] newTail) {
        this.size = 0;
        this.shift = 5;
        this.root = EMPTY_NODE;
        this.tail = newTail;
    }

    /**
     * Removing from the back is really efficient. After emptying the tail
     * continue to pull a tail out of the tree until the right size is reached.
     */
    private void bulkRemoveFromBack(int fromIndex) {
        if (fromIndex >= tailOffset()) {
            int tailPos = valuePosition(fromIndex);
            int removed = size - fromIndex;
            Arrays.fill(tail, tailPos, tailPos + removed, null);
            size = fromIndex;
        } else {
            Box<Object[]> helper = new Box<>();

            while (fromIndex <= tailOffset()) {
                size -= tailLength();
                pullTailFromTree(helper);
            }

            Arrays.fill(tail, valuePosition(fromIndex), 32, null);
            size = fromIndex;
        }
    }

    //endregion

    //region RandomAccess addition - complex

    @Override
    public void add(int index, E element) {
        addAll(index, Collections.singleton(element));
    }

    @Override
    public boolean addAll(int index, Collection<? extends E> c) {
        CowArrayList<E> old = new CowArrayList<>(generation, size, shift, root, tail);
        clear(null);
        copyFrontOfOldSelf(old, index);
        addAll(c);
        addAll(old.subList(index, old.size()));

        modCount++;
        return true;
    }

    private void copyFrontOfOldSelf(CowArrayList<E> old, int toIndex) {
        // fromIndex will always be in the tail
        while (size + 32 < toIndex) {
            Node tailNode = old.root.leafNode(size, old.shift);
            size += 32;
            root = root.pushLeaf(generation, (size - 1), shift, tailNode);
        }

        tail = old.root.cloneArrayForIndex(toIndex, old.shift);
        size = toIndex;
    }

    //endregion

    //region Remove from middle

    @Override
    public E remove(int index) {
        checkIndexBoundsExclusive(index);

        modCount++;
        if (index == size - 1) {
            // removing at the tail is way more efficient
            int pos = valuePosition(index);
            if (pos > 0 || size == 1) {
                return removeLastFromTail(pos);
            } else {
                return removeLastItemFromTailAndPullTailFromTree();
            }
        } else {
            E old = get(index);
            removeRange(index, index+1);
            return old;
        }
    }

    @Override
    protected void removeRange(int fromIndex, int toIndex) {
        modCount++;

        if (toIndex == size && fromIndex == 0) {
            clear(new Object[32]);
        } else if (toIndex == size) {
            bulkRemoveFromBack(fromIndex);
        } else {
            bulkRemoveFromMiddle(fromIndex, toIndex);
        }
    }

    private void bulkRemoveFromMiddle(int fromIndex, int toIndex) {
        CowArrayList<E> old = new CowArrayList<>(generation, size, shift, root, tail);

        clear(null);
        copyFrontOfOldSelf(old, fromIndex);

        // Array fill b/c the rest of `old` may not fill the tail.
        Arrays.fill(tail, valuePosition(fromIndex), 32, null);

        addAll(old.subList(toIndex, old.size()));
    }

    //endregion
}
