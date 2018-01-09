package com.github.grignaak.collections;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nonnull;

import com.github.grignaak.collections.impl.MoreArrays;

/**
 * An ordered, tree-based copy-on-write map, utilizing shared structure when feasible.
 *
 * <h3>Implementation notes</h3>
 *
 * The current implementation (subject to change) is a 32-way b-tree. In practice this means that structural sharing
 * doesn't start until after the map has 32 entries; and then happens at 32-entry chunks. We found this to utilize cache
 * lines and also be a good balance in structural sharing.
 */
public final class CowTreeMap<K,V> extends AbstractMap<K,V> implements CowOrderedMap<K,V> {
    static final int MIN_CHILDREN = 16;
    private static final int MAX_CHILDREN = 2*MIN_CHILDREN;
    static final int MIN_KEYS = MIN_CHILDREN - 1;
    static final int MAX_KEYS = MAX_CHILDREN - 1;

    static class Node<K,V> {
        private final long generation;
        int numKeys;
        Object[] nodes;

        Node(long generation, int numKeys, Object[] nodes) {
            this.generation = generation;
            this.numKeys = numKeys;
            this.nodes = nodes;
        }

        @Override
        public String toString() {
            StringBuilder str = new StringBuilder(isLeaf() ? "Leaf" : "Node").append("{");
            boolean first = true;
            for (int i = 0; i < numKeys; i++) {
                if (first) {
                    first = false;
                } else {
                    str.append(", ");
                }
                str.append(keyAt(i)).append("=").append(valueAt(i));
            }
            return str.append("}").toString();
        }

        boolean isLeaf() {
            return 2*numKeys == nodes.length;
        }


        private static <K> int searchKeys(Object[] nodes, K key, int numKeys, Comparator<K> comparator) {
            int low = 0;
            int high = numKeys - 1;

            while (low <= high) {
                int mid = (low + high) >>> 1;

                @SuppressWarnings("unchecked")
                K midVal = (K) nodes[keyIndex(mid)];
                int dir = comparator.compare(midVal, key);
                if (dir < 0)
                    low = mid + 1;
                else if (dir > 0)
                    high = mid - 1;
                else
                    return mid; // key found
            }
            return -(low + 1);  // key not found.
        }

        int searchKeys(K key, Comparator<K> cmp) {
            return searchKeys(nodes, key, numKeys, cmp);
        }

        /**
         * <pre>
         *      [ - - D - - ]           [ - - B D - - ]
         *           /           =>          / \
         *    [ - A B C -]            [ - A ]   [ C - ]
         * </pre>
         */
        Node<K,V> splitChildAt(long generation, int index, Node<K, V> child) {
            Node<K,V> left = new Node<>(generation, MIN_KEYS,
                child.isLeaf() ?
                    MoreArrays.copyToLength(child.nodes, 2*MIN_KEYS) :
                    MoreArrays.appendRanges(
                        child.nodes, 0, 2* MIN_KEYS,
                        child.nodes, child.childIndex(0), MIN_CHILDREN));

            Node<K,V> right = new Node<>(generation, MIN_KEYS,
                child.isLeaf() ?
                    Arrays.copyOfRange(child.nodes, keyIndex(MIN_KEYS+1), keyIndex(MAX_KEYS), Object[].class) :
                    MoreArrays.appendRanges(
                        child.nodes, keyIndex(MIN_KEYS+1), 2* MIN_KEYS,
                        child.nodes, child.childIndex(MIN_CHILDREN), MIN_CHILDREN));

            Node<K,V> parent = new Node<>(generation, numKeys+1,
                MoreArrays.arrayCopyAndInsertPairAndElement(nodes,
                    keyIndex(index), child.keyAt(MIN_KEYS), child.valueAt(MIN_KEYS),
                    childIndex(index+1), right));
            parent.nodes[parent.childIndex(index)] = left;

            return parent;
        }

        Node<K,V> insertIntoLeafAt(long generation, int index, K key, V value) {
            return edit(generation, numKeys+1,
                MoreArrays.arrayCopyAndInsert(nodes, keyIndex(index), key, value));
        }

        Node<K,V> replaceValueAt(long generation, int index, V value) {
            if (generation == this.generation) {
                nodes[valueIndex(index)] = value;
                return this;
            } else {
                return new Node<>(generation, numKeys,
                    MoreArrays.arrayCopyAndReplace(nodes, valueIndex(index), value));
            }
        }

        private static int valueIndex(int index) {
            return keyIndex(index) + 1;
        }

        private static int keyIndex(int index) {
            return 2*index;
        }

        private int childIndex(int index) {
            return 2*numKeys + index;
        }

        void replaceChildAt(int index, Node<K,V> child) {
            nodes[childIndex(index)] = child;
        }

        Node<K,V> squash() {
            if (numKeys > 0 || isLeaf()) {
                return this;
            } else {
                return childAt(0);
            }
        }

        /**
         * <pre>
         *      [ - - A - - ]           [ - - A - - ]
         *           /                       /
         *    [ - - ]                 [ - - ]
         *           \                       \
         *            [ - - B ]               [ - - B ]
         * </pre>
         */
        Node<K, V> mergeChildAt(CowTreeMap<K,V> editor, int index) {
            int leftKeys = index == 0 ? 0 : childAt(index - 1).numKeys;
            int rightKeys = index == numKeys ? 0 : childAt(index + 1).numKeys;

            if (leftKeys > MIN_KEYS) {
                return rotateRightAt(editor.generation, index-1);
            } else if (rightKeys > MIN_KEYS) {
                return rotateLeftAt(editor.generation, index);
            } else if (index < numKeys) {
                return mergeChildrenAt(editor, index, index, childAt(index), childAt(index+1));
            } else {
                return mergeChildrenAt(editor, index-1, index-1, childAt(index-1), childAt(index));
            }
        }

        /**
         * <pre>
         *      [ - A C E - ]             [ - A E - ]
         *           / \            =>         |
         *    [ - B ]   [ D - - ]              [ - B C D - ]
         * </pre>
         */
        private Node<K, V> mergeChildrenAt(CowTreeMap<K, V> editor, int keyIndex, int childIndex, Node<K, V> left, Node<K, V> right) {
            Object[] childNodes = left.isLeaf() ?
                MoreArrays.appendRanges(
                    left.nodes, 0, 2 * MIN_KEYS,
                    this.nodes, keyIndex(keyIndex), 2,
                    right.nodes, 0, 2 * MIN_KEYS) :
                MoreArrays.appendRanges(
                    left.nodes, 0, 2 * MIN_KEYS,
                    this.nodes, keyIndex(keyIndex), 2,
                    right.nodes, 0, 2 * MIN_KEYS,

                    left.nodes, left.childIndex(0), MIN_CHILDREN,
                    right.nodes, right.childIndex(0), MIN_CHILDREN);

            Node<K,V> child = new Node<>(editor.generation, MAX_KEYS, childNodes);

            Node<K, V> newNode = edit(editor.generation, numKeys - 1,
                MoreArrays.arrayCopyAndRemovePairAndElement(nodes, keyIndex(keyIndex), childIndex(childIndex)));
            newNode.nodes[newNode.childIndex(childIndex)] = child;
            return newNode;
        }

        private Node<K,V> edit(long generation, int newNumKeys, Object[] newNodes) {
            if (generation == this.generation) {
                nodes = newNodes;
                numKeys = newNumKeys;
                return this;
            } else {
                return new Node<>(generation, newNumKeys, newNodes);
            }
        }

        /**
         * <pre>
         *     [ - - - A - - - ]          [ - - - B - - - ]
         *            / \           =>           / \
         *     [ - - ]   [ B - - ]      [ - - A ]   [ - - ]
         * </pre>
         */
        private Node<K, V> rotateLeftAt(long generation, int index) {
            Node<K,V> leftChild = childAt(index);
            Node<K,V> rightChild = childAt(index + 1);
            boolean areLeaves = leftChild.isLeaf();

            Node<K,V> newLeftChild = new Node<>(generation, leftChild.numKeys+1,
                areLeaves ?
                    MoreArrays.arrayCopyAndAppend(leftChild.nodes, keyAt(index), valueAt(index)) :
                    MoreArrays.arrayCopyAndInsertPairAndElement(leftChild.nodes, keyIndex(leftChild.numKeys), keyAt(index), valueAt(index),
                        leftChild.childIndex(numKeys), rightChild.childAt(0)));

            K newKey = rightChild.keyAt(0);
            V newValue = rightChild.valueAt(0);

            Node<K,V> newRightChild = new Node<>(generation, rightChild.numKeys-1,
                areLeaves ?
                    MoreArrays.arrayCopyAndRemovePair(rightChild.nodes, 0) :
                    MoreArrays.arrayCopyAndRemovePairAndElement(rightChild.nodes, 0, childIndex(0)));

            if (this.generation == generation) {
                nodes[keyIndex(index)] = newKey;
                nodes[valueIndex(index)] = newValue;
                nodes[childIndex(index)] = newLeftChild;
                nodes[childIndex(index+1)] = newRightChild;

                return this;
            } else {
                Object[] newNodes = MoreArrays.arrayCopyAndReplacePair(nodes, keyIndex(index), newKey, newValue);
                newNodes[childIndex(index)] = newLeftChild;
                newNodes[childIndex(index+1)] = newRightChild;

                return  new Node<>(generation, numKeys, newNodes);
            }
        }

        /**
         * <pre>
         *     [ - - - B - - - ]      [ - - - A - - - ]
         *            / \         =>         / \
         *   [ - - A ]   [ - - ]      [ - - ]   [ B - - ]
         * </pre>
         */
        private Node<K, V> rotateRightAt(long generation, int index) {
            Node<K,V> leftChild = childAt(index);
            Node<K,V> rightChild = childAt(index + 1);
            boolean areLeaves = leftChild.isLeaf();

            int leftLastIndex = leftChild.numKeys - 1;
            Node<K,V> newLeftChild = new Node<>(generation, leftChild.numKeys-1,
                areLeaves ?
                    MoreArrays.arrayCopyAndRemovePair(leftChild.nodes, keyIndex(leftLastIndex)) :
                    MoreArrays.arrayCopyAndRemovePairAndElement(leftChild.nodes, keyIndex(leftLastIndex), leftChild.childIndex(leftLastIndex+1)));

            K newKey = leftChild.keyAt(leftLastIndex);
            V newValue = leftChild.valueAt(leftLastIndex);

            Node<K,V> newRightChild = new Node<>(generation, rightChild.numKeys+1,
                areLeaves ?
                    MoreArrays.arrayCopyAndInsert(rightChild.nodes, 0, keyAt(index), valueAt(index)) :
                    MoreArrays.arrayCopyAndInsertPairAndElement(rightChild.nodes, 0, keyAt(index), valueAt(index),
                        rightChild.childIndex(0), leftChild.childAt(leftLastIndex+1)));

            if (this.generation == generation) {
                nodes[keyIndex(index)] = newKey;
                nodes[valueIndex(index)] = newValue;
                nodes[childIndex(index)] = newLeftChild;
                nodes[childIndex(index+1)] = newRightChild;

                return this;
            } else {
                Object[] newNodes = MoreArrays.arrayCopyAndReplacePair(this.nodes, keyIndex(index), newKey, newValue);
                newNodes[childIndex(index)] = newLeftChild;
                newNodes[childIndex(index+1)] = newRightChild;

                return  new Node<>(generation, numKeys, newNodes);
            }
        }

        /**
         * <pre>
         *     [ A B C ]    =>    [ A C ]
         * </pre>
         */
        Node<K,V> removeFromLeafAt(long generation, int index) {
            if (numKeys == 1) {
                // this only ever happens at the root of the tree
                return emptyNode();
            } else {
                return new Node<>(generation, numKeys-1,
                    MoreArrays.arrayCopyAndRemovePair(nodes, keyIndex(index)));
            }
        }

        Node<K,V> replaceWithChildValueAt(CowTreeMap<K,V> editor, int replaceIndex) {
            Node<K,V> left = childAt(replaceIndex);
            Node<K,V> right = childAt(replaceIndex + 1);

            if (left.numKeys == MIN_KEYS && right.numKeys == MIN_KEYS) {
                // how very unlucky. We'll have to push the key down into a merged node and recurse.
                Node<K,V> node = mergeChildrenAt(editor, replaceIndex, replaceIndex, left, right);
                Node<K, V> child = node.childAt(replaceIndex);
                if (child.isLeaf()) {
                    node.replaceChildAt(replaceIndex,
                        child.removeFromLeafAt(editor.generation, MIN_KEYS));
                } else {
                    node.replaceChildAt(replaceIndex,
                        child.replaceWithChildValueAt(editor, MIN_KEYS));
                }
                return node;
            }


            if (right.numKeys > MIN_KEYS) {
                return replaceWithSuccessor(editor, replaceIndex, right);
            } else {
                return replaceWithPredecessor(editor, replaceIndex, left);
            }
        }

        private Node<K, V> replaceWithSuccessor(CowTreeMap<K,V> editor, int replaceIndex, Node<K, V> right) {
            Node<K,V> me = this.editable(editor.generation);
            Node<K,V> parent = me;
            int nodeIndex = replaceIndex + 1;
            Node<K,V> cur = right;

            // invariant: parent is editable
            while (!cur.isLeaf()) {
                Node<K,V> next = cur.childAt(0);
                if (next.numKeys == MIN_KEYS) {
                    cur = cur.mergeChildAt(editor, 0);
                    parent.replaceChildAt(nodeIndex, cur);
                    next = cur.childAt(0);
                }

                parent = cur;
                nodeIndex = 0;
                cur = next;
            }

            me.nodes[keyIndex(replaceIndex)] = cur.keyAt(0);
            me.nodes[valueIndex(replaceIndex)] = cur.valueAt(0);

            parent.replaceChildAt(nodeIndex,
                cur.removeFromLeafAt(editor.generation, 0));
            return me;
        }

        /**
         * <pre>
         *      [ - - A - - ]            [ - - B - - ]
         *           /                        /
         *    [ - - ]             =>   [ - - ]
         *           \                        \
         *            [ - - B ]                [ - - ]
         * </pre>
         */
        private Node<K, V> replaceWithPredecessor(CowTreeMap<K,V> editor, int replaceIndex, Node<K, V> left) {
            Node<K,V> me = this.editable(editor.generation);
            Node<K,V> parent = me;
            int nodeIndex = replaceIndex;
            Node<K,V> cur = left;

            // invariant: parent is editable
            while (!cur.isLeaf()) {
                int nextIndex = cur.numKeys;
                Node<K,V> next = cur.childAt(nextIndex);
                if (next.numKeys == MIN_KEYS) {
                    cur = cur.mergeChildAt(editor, nextIndex);
                    parent.replaceChildAt(nodeIndex, cur);
                    nextIndex = cur.numKeys;
                    next = cur.childAt(nextIndex);
                }

                parent = cur;
                nodeIndex = nextIndex;
                cur = next;
            }

            int keyIndex = cur.numKeys - 1;
            me.nodes[keyIndex(replaceIndex)] = cur.keyAt(keyIndex);
            me.nodes[valueIndex(replaceIndex)] = cur.valueAt(keyIndex);

            parent.replaceChildAt(nodeIndex,
                cur.removeFromLeafAt(editor.generation, keyIndex));
            return me;
        }

        private Node<K, V> editable(long generation) {
            return generation == this.generation ? this : new Node<>(generation, numKeys, nodes.clone());
        }

        @SuppressWarnings("unchecked")
        V valueAt(int index) {
            return (V) nodes[valueIndex(index)];
        }

        @SuppressWarnings("unchecked")
        K keyAt(int index) {
            return (K) nodes[keyIndex(index)];
        }

        @SuppressWarnings("unchecked")
        Node<K, V> childAt(int index) {
            return (Node<K,V>) nodes[childIndex(index)];
        }
    }

    private static final Node<?,?> EMPTY_NODE = new Node<>(-1, 0, new Object[0]);

    @SuppressWarnings("unchecked")
    private static <K,V> Node<K,V> emptyNode() {
        return (Node<K,V>) EMPTY_NODE;
    }

    private long generation;
    private final Comparator<K> comparator;
    private int size;
    private Node<K,V> root;

    /**
     * @param comparator (try using {@link Comparator#naturalOrder()})
     */
    public CowTreeMap(Comparator<K> comparator) {
        //noinspection unchecked
        this(EMPTY_NODE.generation + 1, 0, (Node<K, V>) EMPTY_NODE, comparator);
    }

    private CowTreeMap(long generation, int size, Node<K, V> root, Comparator<K> comparator) {
        this.size = size;
        this.root = root;
        this.comparator = comparator;

        this.generation = generation;
    }

    @Override
    public int size() {
        return size;
    }

    private static class NodeStack<K,V> {
        private final NodeStack<K,V> parent;
        private final Node<K,V> node;
        /** points at the next key to return; or just before */
        private int index;

        private NodeStack(NodeStack<K, V> parent, Node<K, V> node, int index) {
            this.parent = parent;
            this.node = node;
            this.index = index;
        }

        static <K,V> NodeStack<K,V> before(K upperBound, Node<K,V> root, Comparator<K> cmp) {
            if (root.numKeys == 0)
                return null;

            Node<K,V> node = root;
            NodeStack<K,V> cur = null;
            for (;;) {
                int index = node.searchKeys(upperBound, cmp);
                int insertion = -index - 1;
                if (index >= 0) {
                    return new NodeStack<>(cur, node, index).previous();
                } else if (node.isLeaf()) {
                    return new NodeStack<>(cur, node, insertion).previous();
                } else {
                    cur = new NodeStack<>(cur, node, insertion-1);
                    node = node.childAt(insertion);
                }
            }
        }

        static <K,V> NodeStack<K,V> after(K lowerBound, Node<K,V> root, Comparator<K> cmp) {
            if (root.numKeys == 0)
                return null;

            Node<K,V> node = root;
            NodeStack<K,V> cur = null;
            for (;;) {
                int index = node.searchKeys(lowerBound, cmp);
                int insertion = -index - 1;
                if (index >= 0) {
                    return new NodeStack<>(cur, node, index).next();
                } else if (node.isLeaf()) {
                    return new NodeStack<>(cur, node, insertion-1).next();
                } else {
                    cur = new NodeStack<>(cur, node, insertion);
                    node = node.childAt(insertion);
                }
            }
        }

        NodeStack<K,V> next() {
            index++;
            NodeStack<K,V> cur = this;
            if (index == ( node.isLeaf() ? node.numKeys : node.numKeys + 1 )) {
                do {
                    cur = cur.parent;
                } while (cur != null && cur.index == cur.node.numKeys);
                return cur;
            }

            if (cur.node.isLeaf()) {
                return cur;
            }

            return new NodeStack<>(this, cur.node.childAt(cur.index), 0).firstChild();
        }

        private NodeStack<K, V> firstChild() {
            NodeStack<K,V> cur = this;
            while (!cur.node.isLeaf()) {
                cur = new NodeStack<>(cur, cur.node.childAt(0), 0);
            }
            return cur;
        }

        NodeStack<K,V> previous() {
            index--;
            if (index == ( node.isLeaf() ? -1 : -2 )) {
                NodeStack<K,V> cur = this;
                do {
                    cur = cur.parent;
                } while (cur != null && cur.index < 0);
                return cur;
            } else {
                if (node.isLeaf()) {
                    return this;
                }

                Node<K, V> child = node.childAt(index+1);
                return new NodeStack<>(this, child, child.numKeys - 1).lastChild();
            }
        }

        private NodeStack<K, V> lastChild() {
            NodeStack<K, V> cur = this;
            while (!cur.node.isLeaf()) {
                Node<K,V> child = cur.node.childAt(cur.node.numKeys);
                cur = new NodeStack<>(cur, child, child.numKeys - 1);
            }
            return cur;
        }

        K getKey() { return node.keyAt(index); }
        V getValue() { return node.valueAt(index); }
    }

    private class SettableEntry implements Entry<K,V> {
        private K key;
        private V value;

        private SettableEntry(K key, V value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public K getKey() {
            return key;
        }

        @Override
        public V getValue() {
            return value;
        }

        @Override
        public V setValue(V value) {
            value = CowTreeMap.this.put(key, value);
            return value;
        }

        @Override
        public String toString() {
            return "<" + key + "=" + value + ">";
        }
    }

    private abstract class EntryIter implements Iterator<Entry<K,V>> {
        NodeStack<K,V> stack;
        Entry<K,V> lastReturned;

        @Override
        public boolean hasNext() {
            return stack != null;
        }

        @Override
        public void remove() {
            CowTreeMap.this.remove(lastReturned.getKey(), lastReturned.getValue());
        }
    }

    private class AscendingEntryIter extends EntryIter {

        AscendingEntryIter(Node<K,V> root) {
            if (root.numKeys == 0)
                return;
            stack = new NodeStack<>(null, root, 0).firstChild();
        }

        AscendingEntryIter(Node<K,V> root, K lowerBoundExclusive) {
            stack = NodeStack.after(lowerBoundExclusive, root, comparator);
        }

        @Override
        public Entry<K, V> next() {
            if (stack == null)
                throw new NoSuchElementException("Forget to call hasNext()?");

            lastReturned = new SettableEntry(stack.getKey(), stack.getValue());
            stack = stack.next();
            return lastReturned;
        }
    }

    private class DescendingEntryIter extends EntryIter {

        DescendingEntryIter(Node<K,V> root) {
            if (root.numKeys == 0)
                return;
            stack = new NodeStack<>(null, root, root.numKeys - 1).lastChild();
        }

        DescendingEntryIter(Node<K,V> root, K upperBoundExclusive) {
            stack = NodeStack.before(upperBoundExclusive, root, comparator);
        }

        @Override
        public Entry<K, V> next() {
            if (stack == null)
                throw new NoSuchElementException("Forget to call hasNext()?");

            lastReturned = new SettableEntry(stack.getKey(), stack.getValue());
            stack = stack.previous();
            return lastReturned;
        }
    }

    @Override @Nonnull
    public Set<Entry<K, V>> entrySet() {
        class EntrySet extends AbstractSet<Entry<K,V>> {

            @Override @Nonnull
            public Iterator<Entry<K, V>> iterator() {
                return new AscendingEntryIter(root);
            }

            @Override
            public int size() {
                return size;
            }
        }
        return new EntrySet();
    }

    @Override
    public V get(Object oKey) {
        return getOrDefault(oKey, null);
    }

    @Override
    public V getOrDefault(Object oKey, V defaultValue) {
        @SuppressWarnings("unchecked")
        K key = (K) oKey;
        Node<K,V> cur = root;
        while (true) {
            int index = cur.searchKeys(key, comparator);
            if (index >= 0) {
                return cur.valueAt(index);
            } else if (cur.isLeaf()) {
                return null;
            } else {
                cur = cur.childAt(-index - 1);
            }

        }
    }

    @Override
    public boolean containsKey(Object oKey) {
        @SuppressWarnings("unchecked")
        K key = (K) oKey;
        Node<K,V> cur = root;
        while (true) {
            int index = cur.searchKeys(key, comparator);
            if (index >= 0) {
                return true;
            } else if (cur.isLeaf()) {
                return false;
            } else {
                cur = cur.childAt(-index - 1);
            }
        }
    }

    @Override
    public Iterable<Entry<K, V>> descendingEntries() {
        return () -> new DescendingEntryIter(root);
    }

    @Override
    public Iterable<Entry<K, V>> ascendingEntries() {
        return () -> new AscendingEntryIter(root);
    }

    @Override
    public Iterable<Entry<K, V>> descendingEntriesBefore(K upperBoundExclusive) {
        return () -> new DescendingEntryIter(root, upperBoundExclusive);
    }

    @Override
    public Iterable<Entry<K, V>> ascendingEntriesAfter(K lowerBoundExclusive) {
        return () -> new AscendingEntryIter(root, lowerBoundExclusive);
    }

    //region mutations

    private static final Object ALWAYS_REMOVE = new Object();
    private static final Object NOT_REMOVED = new Object();

    @Override
    public V put(K key, V value) {
        return put(key, value, /*replace=*/true);
    }

    @Override
    public V putIfAbsent(K key, V value) {
        return put(key, value, /*replace=*/false);
    }

    @SuppressWarnings("OverlyLongMethod"/*top down b-tree*/)
    private V put(K key, V value, boolean replaceCurrentValue) {
        if (root.numKeys == MAX_KEYS) {
            root = new Node<K,V>(generation, 0, new Object[]{root})
                .splitChildAt(generation, 0, root);
        }

        Node<K, V> fauxRoot = new Node<>(generation, 0, new Object[1]);
        fauxRoot.replaceChildAt(0, root);

        Node<K,V> parent = fauxRoot;
        int nodeIndex = 0;
        Node<K,V> node = root;

        // loop invariant: parent is editable
        for (;;) {
            int index = node.searchKeys(key, comparator);

            if (index >= 0) {
                V val = node.valueAt(index);
                if (replaceCurrentValue) {
                    parent.replaceChildAt(nodeIndex,
                            node.replaceValueAt(generation, index, value));
                }
                root = fauxRoot.childAt(0);
                return val;
            }

            // The insertion point; N.B. may point _past_ the keys.
            index = -index - 1;

            if (node.isLeaf()) {
                parent.replaceChildAt(nodeIndex,
                        node.insertIntoLeafAt(generation, index, key, value));
                size++;
                root = fauxRoot.childAt(0);
                return null;
            }

            Node<K, V> child = node.childAt(index);
            if (child.numKeys == MAX_KEYS) {
                node = node.splitChildAt(generation, index, child);
                parent.replaceChildAt(nodeIndex, node);

                // the key location could have changed!
                int dir = comparator.compare(key, node.keyAt(index));
                if (dir == 0) {
                    V val = node.valueAt(index);
                    if (replaceCurrentValue) {
                        parent.replaceChildAt(nodeIndex,
                                node.replaceValueAt(generation, index, value));
                    }
                    root = fauxRoot.childAt(0);
                    return val;
                } else if (dir > 0) {
                    index++;
                }
                child = node.childAt(index);
            }

            Node<K, V> nextParent = node.editable(generation);
            if (node.generation != generation) {
                parent.replaceChildAt(nodeIndex, nextParent);
            }
            parent = nextParent;
            nodeIndex = index;
            node = child;
        }
    }

    @Override
    public V remove(Object key) {
        V val = doRemove(key, ALWAYS_REMOVE);
        return  val == NOT_REMOVED ? null : val;
    }

    @Override
    public boolean remove(Object key, Object value) {
        return doRemove(key, value) != NOT_REMOVED;
    }

    private V doRemove(Object oKey, Object expectedValue) {
        @SuppressWarnings("unchecked")
        K key = (K) oKey;

        Node<K,V> fauxRoot = new Node<>(generation, 0, new Object[1]);
        Node<K,V> parent = fauxRoot;
        fauxRoot.replaceChildAt(0, root);
        int nodeIndex = 0;
        Node<K,V> node = root;

        @SuppressWarnings("unchecked")
        V removed = (V) NOT_REMOVED;
        for (;;) {
            int index = node.searchKeys(key, comparator);
            if (index >= 0) {
                V valueAtIndex = node.valueAt(index);
                if (expectedValue == ALWAYS_REMOVE || Objects.equals(expectedValue, removed)) {
                    removed =  valueAtIndex;
                    parent.replaceChildAt(nodeIndex,
                        node.isLeaf() ?
                            node.removeFromLeafAt(generation, index) :
                            node.replaceWithChildValueAt(this, index));
                    size--;
                }
                break;
            } else if (node.isLeaf()) {
                break;
            } else {
                index = -index - 1;
                Node<K,V> child = node.childAt(index);
                if (child.numKeys == MIN_KEYS) {
                    node = node.mergeChildAt(this, index);
                    parent.replaceChildAt(nodeIndex, node);
                } else {
                    parent = node;
                    nodeIndex = index;
                    node = child;
                }
            }
        }

        root = fauxRoot.childAt(0).squash();
        return removed;
    }

    @Override
    public CowTreeMap<K, V> fork() {
        return new CowTreeMap<>(++generation, size, root, comparator);
    }

    //endregion
}
