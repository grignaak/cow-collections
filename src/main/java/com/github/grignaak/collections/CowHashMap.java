package com.github.grignaak.collections;

import static java.lang.Integer.bitCount;

import java.util.AbstractCollection;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;

import com.github.grignaak.collections.impl.MoreArrays;
import com.github.grignaak.collections.impl.Search;
import com.github.grignaak.collections.impl.Change;

/**
 * A hash-based copy-on-write map, where the get operation and the builder's put, update, and delete operations each
 * take sub-linear (near-constant) time.
 *
 * <h3>Implementation notes</h3>
 *
 * This current implementation (subject to change) is a MEM-CHAMP variant of Bagwell's HAMT, a 32-way trie. In practice
 * this means that structural happens at 32-entry chunks. We found this to utilize cache lines and also be a good
 * balance in structural sharing.
 */
public class CowHashMap<K,V> implements CowMap<K,V> {
    /*
     * <p>This implements the MEM-CHAMP variant of Bagwell's HAMT. It improves on
     * other java implementations in several ways:
     * <ul>
     *     <li>Deletion maintains a canonical format.</li>
     *     <li>Children and nodes are kept separately; this both simplifies the
     *         code and removes type-checks whilst traversing the tree.</li>
     *     <li>The number of node types is reduced to two: index node and hash
     *         collision node. This also simplifies the code significantly.</li>
     *     <li>The hash codes of the entries are cached.</li>
     *     <li>An empty root node is allowed, simplifying code even more.</li>
     *     <li>Simpler code is faster code. The JIT can better handle simpler
     *         code.</li>
     * </ul>
     * </p>
     *
     * <h3>Hash Array Mapped Tries (HAMT, or Bagwell Tries)</h3>
     *
     * <p>HAMT is essentially a 32-way trie on the key's hash code.</p>
     *
     * <p>HAMT splits the hash of a key into 5-bit segments Each level in the trie
     * corresponds to one of these segments. The 5 bits of the hash represent the
     * index into the array at that level. HAMT segments are read from least
     * significant to most significant</p>
     *
     * <p>Take for example a given hash value of {@code 0x32389613}. Arranging
     * the bits into groups of 5 yields {@code 00 11001 00011 10001 00101 10000 10011}
     * in binary, which corresponds to the indices {@code 0 25 3 17 5 16 19}.
     * Thus this key will be located at index 19 in the root level, 16 in the
     * next level, then 5 on the third level, and so on.</p>
     *
     * <p>Empty nodes and those with only one value are inlined to its parent node.
     * (The root node may be empty.) A 32-bit bitmap maintains the membership at
     * each index. Furthermore, the arrays are densely packed.</p>
     *
     * <p>Finally, the data and the children are kept separately; but in the same
     * array. The data comes first in the array, with keys and values alternating.
     * The tail of the array contains the children.</p>
     *
     * <pre>
     *     [ k v k v k v | N N ]
     *                    /   \
     *         [ k v | N ]     [ k v k v ]
     *                  \
     *                   [ k v k v ]
     * </pre>
     *
     * <p>Each section in the array has its own bitmap: the dataMap and the nodeMap.
     * The bits set in dataMap and nodeMap are mutually exclusive. The nodes in
     * the array are kept in reverse order to reduce the required state.</p>
     *
     * <pre>
     *      ------>         <------
     *     [ k v k v k v | N N N N ]
     * </pre>
     *
     * <p>Most nodes in the trie are {@link BitmapIndexNode}s; the leaves may be
     * {@link HashCollisionNode}s for handling keys with the same hash code</p>
     *
     * <p>Some invariants:
     * <ul>
     *     <li>{@code transitiveSize >= 2*nodeArity + payloadArity}</li>
     *     <li>if any node is editable then its parent node is also editable</li>
     * </ul>
     * </p>
     */

    private static final int TUPLE_LENGTH = 2;

    private static final int BIT_PARTITION_SIZE = 5;
    private static final int BIT_PARTITION_MASK = 0b11111;

    static final BitmapIndexNode<?,?> EMPTY_NODE = new BitmapIndexNode<>(-1, 0, 0, new Object[0], new int[0]);


    //region Nodes

    /**
     * The inner node type; containing both data and children nodes.
     */
    static abstract class Node<K,V> {
        abstract Search<V> findByKey(K key, int hash, int shift);
        abstract Node<K,V> put(long generation, K key, V value, int keyHash, int shift, Change<V> change);
        abstract Node<K,V> remove(long generation, Object key, int keyHash, int shift, Change<V> change);

        abstract K keyAt(int index);
        abstract V valueAt(int index);

        Node<K,V> nodeAt(int index) { throw new AssertionError("no nodes"); }
        abstract int hashAt(int index);

        abstract int payloadArity();
        final boolean hasPayload() { return payloadArity() > 0; }

        int nodeArity() { return 0; }
        final boolean hasNodes() { return nodeArity() > 0; }

        /**
         * In MEMCHAMP this is called the 'size predicate'
         *
         * @return x = 0, x = 1, or x > 1 when the node has 0, 1, or many transitive keys.
         */
        final int looseSize() {
            if (nodeArity() != 0) {
                return Integer.MAX_VALUE;
            } else {
                return payloadArity();
            }
        }
    }

    /**
     * The core node of MEMCHAMP Bagwell tries.
     */
    static final class BitmapIndexNode<K,V> extends Node<K,V> {

        protected long generation;
        protected int nodeMap;
        protected int dataMap;

        private Object[] nodes;
        private int[] hashes;

        BitmapIndexNode(long generation, int nodeMap, int dataMap, Object[] nodes, int[] hashes) {
            this.generation = generation;
            this.nodeMap = nodeMap;
            this.dataMap = dataMap;
            this.nodes = nodes;
            this.hashes = hashes;
        }

        @Override
        public String toString() {
            return "BIN{" +
                "vs=" + Integer.toBinaryString(dataMap) +
                ",ns=" + Integer.toBinaryString(nodeMap) +
                "," + Arrays.toString(nodes) +
                "}";
        }

        @SuppressWarnings({"OverlyComplexMethod", "unused"})
        private void checkNodeInvariants() {
            int payloadArity = payloadArity();
            int nodeArity = nodeArity();
            int arity = payloadArity + nodeArity;
            int sizePredicate = looseSize();

            int size = computeTransitiveSize();

            assert (nodeArity >= 0) && (payloadArity >= 0);

            assert arity > 0 || sizePredicate == 0;
            assert !(arity == 1 && payloadArity == 1) || sizePredicate == 1;
            assert arity < 2 || sizePredicate > 1;

            assert (size - payloadArity >= 2 * (arity - payloadArity));

            int payloadSize = TUPLE_LENGTH * payloadArity;
            assert payloadSize + nodeArity == nodes.length;

            for (int i = 0; i < payloadSize; i++) {
                assert !(nodes[i] instanceof Node<?,?>) : Arrays.toString(nodes);
            }
            for (int i = payloadSize; i < nodes.length; i++) {
                assert nodes[i] instanceof Node<?,?> : Arrays.toString(nodes);
            }
        }

        /**
         * The number of keys in this and all children nodes. Used by the node invariant check
         */
        private int computeTransitiveSize() {
            Iterator<Void> iter = new SelfIter<>(this);

            int size = 0;
            while (iter.hasNext()) {
                iter.next();
                size++;
            }

            return size;
        }

        /**
         * Check for equality of two nodes in canonical format.
         */
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof BitmapIndexNode<?,?>)) return false;

            BitmapIndexNode<?,?> that = (BitmapIndexNode<?, ?>) obj;

            return this.nodeMap == that.nodeMap &&
                this.dataMap == that.dataMap &&
                Arrays.equals(this.hashes, that.hashes) &&
                Arrays.equals(this.nodes, that.nodes);
        }

        @Override
        public Search<V> findByKey(K key, int keyHash, int shift) {
            final int mask = mask(keyHash, shift);
            final int bitpos = bitpos(mask);

            if ((dataMap & bitpos) != 0) {
                // We've got a winner in the data section
                final int index = dataIndex(bitpos);
                final K curKey = keyAt(index);
                final int curHash = hashes[index];

                if (curHash == keyHash && Objects.equals(curKey, key)) {
                    return Search.found(valueAt(index));
                } else {
                    return Search.notFound();
                }
            } else if ((nodeMap & bitpos) != 0) {
                // our search goes to the children
                Node<K,V> child = nodeAtBitpos(bitpos);
                return child.findByKey(key, keyHash, shift + BIT_PARTITION_SIZE);
            } else {
                return Search.notFound();
            }
        }

        @Override
        public BitmapIndexNode<K, V> put(long generation, K key, V value, int keyHash, int shift, Change<V> change) {
            final int mask = mask(keyHash, shift);
            final int bitpos = bitpos(mask);

            if ((dataMap & bitpos) != 0) {
                // We've got a winner in the data section.
                final int index = dataIndex(bitpos);
                final K curKey = keyAt(index);
                final int curHash = hashes[index];

                if (curHash == keyHash && Objects.equals(curKey, key)) {
                    change.updated(valueAt(index));
                    return copyAndSetValue(generation, index, value);
                } else {
                    change.modified();

                    return copyAndMigrateFromInlineToNode(generation, bitpos,
                        mergeTwoKeyValuePairs(generation, curKey, valueAt(index), hashes[index], key, value, keyHash, shift + BIT_PARTITION_SIZE));
                }
            } else if ((nodeMap & bitpos) != 0) {
                Node<K,V> oldChild = nodeAtBitpos(bitpos);
                Node<K,V> newChild = oldChild.put(generation, key, value, keyHash, shift+BIT_PARTITION_SIZE, change);
                if (!change.isModified()) {
                    return this;
                } else {
                    return copyAndSetNode(generation, bitpos, newChild);
                }
            } else {
                // there is no value
                change.modified();
                return copyAndInsertValue(generation, bitpos, key, value, keyHash);
            }
        }

        private BitmapIndexNode<K, V> copyAndSetNode(long generation, int bitpos, Node<K, V> child) {
            final int index = nodes.length - 1 - nodeIndex(bitpos);

            if (generation == this.generation) {
                nodes[index] = child;

                return this;
            } else {
                Object[] newNodes = MoreArrays.arrayCopyAndReplace(nodes, index, child);
                return new BitmapIndexNode<>(generation, nodeMap, dataMap, newNodes, hashes);
            }
        }

        private BitmapIndexNode<K, V> copyAndMigrateFromInlineToNode(long generation, int bitpos, Node<K, V> childNode) {
            final int index = dataIndex(bitpos);

            int newChildIndex = nodes.length - nodeIndex(bitpos);

            Object[] newNodes = MoreArrays.arrayCopyAndRemovePairAndInsert(nodes, TUPLE_LENGTH * index, newChildIndex, childNode);
            int[] newHashes = MoreArrays.arrayCopyAndRemove(hashes, index);
            int newNodeMap = this.nodeMap | bitpos;
            int newDataMap = this.dataMap ^ bitpos;

            if (generation == this.generation) {
                this.nodeMap = newNodeMap;
                this.dataMap = newDataMap;
                this.nodes = newNodes;
                this.hashes = newHashes;

                return this;
            } else {
                return new BitmapIndexNode<>(generation, newNodeMap, newDataMap, newNodes, newHashes);
            }
        }

        private static <K,V> Node<K, V> mergeTwoKeyValuePairs(long generation, K key0, V value0, int keyHash0, K key1, V value1, int keyHash1, int shift) {

            if (keyHash0 == keyHash1) {
                @SuppressWarnings("unchecked")
                HashCollisionNode<K, V> collision = new HashCollisionNode<K, V>(generation,
                    keyHash0, (K[]) new Object[]{key0, key1},
                    (V[]) new Object[]{value0, value1}
                );

                return collision;
            }

            int mask0 = mask(keyHash0, shift);
            int mask1 = mask(keyHash1, shift);

            if (mask0 != mask1) {
                // The two nodes fit at the same level!
                final int dataMap = bitpos(mask0) | bitpos(mask1);
                if (mask0 < mask1) {
                    return new BitmapIndexNode<>(generation, 0, dataMap,
                        new Object[] { key0, value0, key1, value1 },
                        new int[] { keyHash0, keyHash1 });
                } else {
                    return new BitmapIndexNode<>(generation, 0, dataMap,
                        new Object[] { key1, value1, key0, value0 },
                        new int[] { keyHash1, keyHash0 });
                }
            } else {
                Node<K,V> child = mergeTwoKeyValuePairs(generation, key0, value0, keyHash0, key1, value1, keyHash1, shift + BIT_PARTITION_SIZE);
                return new BitmapIndexNode<>(generation, bitpos(mask0), 0, new Object[]{child}, new int[0]);
            }
        }

        @Override
        BitmapIndexNode<K, V> remove(long generation, Object key, int keyHash, int shift, Change<V> change) {
            final int mask = mask(keyHash, shift);
            final int bitpos = bitpos(mask);

            if ((dataMap & bitpos) != 0) {
                // it's in the data section!
                final int index = dataIndex(bitpos);
                final int curHash = hashes[index];

                if (curHash == keyHash && Objects.equals(keyAt(index), key)) {
                    change.updated(valueAt(index));
                    return copyAndRemoveKeyValuePair(generation, bitpos);
                } else {
                    // not found :(
                    return this;
                }
            } else if ((nodeMap & bitpos) != 0) {
                // it's in the children!
                Node<K, V> child = nodeAt(nodeIndex(bitpos));
                Node<K, V> newChild = child.remove(generation, key, keyHash, shift + BIT_PARTITION_SIZE, change);

                if (!change.isModified()) {
                    return this;
                } else if (newChild.looseSize() == 1) {
                    // The new child only has a single key-value pair
                    return copyAndMigrateFromNodeToInline(generation, bitpos, newChild);
                } else {
                    return copyAndSetNode(generation, bitpos, newChild);
                }
            } else {
                // not found :(
                return this;
            }
        }

        private BitmapIndexNode<K, V> copyAndMigrateFromNodeToInline(long generation, int bitpos, Node<K, V> child) {
            final K key = child.keyAt(0);
            final V value = child.valueAt(0);
            final int hash = child.hashAt(0);

            final int dataIndex = dataIndex(bitpos);
            final int nodeIndex = nodes.length - 1 - nodeIndex(bitpos);

            Object[] newNodes = MoreArrays.arrayCopyAndInsertPairAndRemove(nodes, TUPLE_LENGTH * dataIndex, key, value, nodeIndex);
            int[] newHashes = MoreArrays.arrayCopyAndInsert(hashes, dataIndex, hash);
            int newDataMap = dataMap | bitpos;
            int newNodeMap = nodeMap ^ bitpos;

            if (generation == this.generation) {
                this.nodes = newNodes;
                this.hashes = newHashes;
                this.dataMap = newDataMap;
                this.nodeMap = newNodeMap;

                return this;
            } else {
                return new BitmapIndexNode<>(generation, newNodeMap, newDataMap, newNodes, newHashes);
            }
        }

        private BitmapIndexNode<K, V> copyAndRemoveKeyValuePair(long generation, int bitpos) {
            final int index = dataIndex(bitpos);

            Object[] newNodes = MoreArrays.arrayCopyAndRemovePair(nodes, TUPLE_LENGTH * index);
            int[] newHashes = MoreArrays.arrayCopyAndRemove(hashes, index);
            int newDataMap = dataMap ^ bitpos;

            if (generation == this.generation) {
                this.dataMap = newDataMap;
                this.nodes = newNodes;
                this.hashes = newHashes;

                return this;
            } else {
                return new BitmapIndexNode<>(generation, nodeMap, newDataMap, newNodes, newHashes);
            }
        }

        private BitmapIndexNode<K,V> copyAndSetValue(long generation, int index, V value) {
            int valueIndex = TUPLE_LENGTH * index + 1;
            if (this.generation == generation) {
                nodes[(valueIndex)] = value;

                return this;
            } else {
                Object[] newNodes = MoreArrays.arrayCopyAndReplace(nodes, valueIndex, value);
                return new BitmapIndexNode<>(generation, nodeMap, dataMap, newNodes, hashes);
            }
        }

        private BitmapIndexNode<K,V> copyAndInsertValue(long generation, int bitpos, K key, V value, int hash) {
            final int index = dataIndex(bitpos);

            final Object[] newNodes = MoreArrays.arrayCopyAndInsert(nodes, TUPLE_LENGTH * index, key, value);
            final int[] newhashes = MoreArrays.arrayCopyAndInsert(hashes, index, hash);

            if (generation == this.generation) {
                this.nodes = newNodes;
                this.dataMap |= bitpos;
                this.hashes = newhashes;

                return this;
            } else {
                return new BitmapIndexNode<>(generation, nodeMap, (dataMap | bitpos), newNodes, newhashes);
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        K keyAt(int index) {
            return (K) nodes[TUPLE_LENGTH * index];
        }

        @SuppressWarnings("unchecked")
        V valueAt(int index) {
            return (V) nodes[(TUPLE_LENGTH * index) + 1];
        }

        int hashAt(int index) {
            return hashes[index];
        }

        @Override
        @SuppressWarnings("unchecked")
        Node<K, V> nodeAt(int index) {
            return (Node<K,V>) nodes[nodes.length - 1 - index];
        }

        private Node<K,V> nodeAtBitpos(int bitpos) {
            return nodeAt(nodeIndex(bitpos));
        }

        /**
         * Where in the array the data is, given its bitpos
         * The key is at (index*2) and the value at (index*2 + 1)
         */
        private int dataIndex(int bitpos) {
            return bitCount(dataMap & (bitpos - 1));
        }
        /**
         *  Where in the array the node is, given its bitpos
         *  The node is at (nodes.length - index - 1)
         */
        private int nodeIndex(int bitpos) {
            return bitCount(nodeMap & (bitpos - 1));
        }

        @Override
        public int payloadArity() {
            return bitCount(dataMap);
        }

        @Override
        public int nodeArity() {
            return bitCount(nodeMap);
        }
    }

    private static class HashCollisionNode<K,V> extends Node<K,V> {

        private final long generation;
        private K[] keys;
        private V[] values;
        private int keyHash;

        public HashCollisionNode(long generation, int keyHash, K[] keys, V[] values) {
            this.generation = generation;
            this.keys = keys;
            this.values = values;
            this.keyHash = keyHash;
        }

        @Override
        public String toString() {
            return "HCN{" +
                "ks=" + Arrays.toString(keys) +
                ",vs=" + Arrays.toString(values) +
                "}";
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (!(obj instanceof HashCollisionNode<?,?>)) return false;

            HashCollisionNode<?,?> that = (HashCollisionNode<?, ?>) obj;
            if (this.keyHash != that.keyHash || this.payloadArity() != that.payloadArity())
                return false;

            // These aren't stored in any order so a linear scan must do it.
            loop:
            for (int i = 0, sz = keys.length; i < sz; i++) {
                Object key = that.keys[i];
                Object value = that.values[i];
                for (int j = 0; j < sz; j++) {
                    if (Objects.equals(keys[i], key) && Objects.equals(values[i], value))
                        continue loop;
                }
                return false;
            }

            return true;
        }

        @Override
        Node<K, V> put(long generation, K key, V value, int keyHash, int shift, Change<V> change) {
            if (keyHash != this.keyHash) {
                // This is a squashed node and the key doesn't belong!
                // Add this as a child of a new node.
                change.modified();

                return new BitmapIndexNode<>(generation,
                    bitpos(this.keyHash, shift), bitpos(keyHash, shift),
                    new Object[] { key, value, this },
                    new int[] { keyHash });
            } else {
                for (int i = 0; i < keys.length; i++) {
                    if (Objects.equals(key, keys[i])) {
                        change.updated(values[i]);
                        return copyAndReplaceValue(generation, value, i);
                    }
                }

                change.modified();
                return copyAndAppendValue(generation, key, value);
            }
        }

        @Override
        Node<K, V> remove(long generation, Object key, int keyHash, int shift, Change<V> change) {
            for (int i = 0; i < keys.length; i++) {
                if (Objects.equals(key, keys[i])) {
                    change.updated(values[i]);
                    return copyAndRemoveValue(generation, i);
                }
            }

            // not found :(
            return this;
        }

        private Node<K, V> copyAndAppendValue(long generation, K key, V value) {
            @SuppressWarnings("unchecked")
            K[] newKeys = (K[]) MoreArrays.arrayCopyAndInsert(keys, keys.length, key);

            @SuppressWarnings("unchecked")
            V[] newValues = (V[]) MoreArrays.arrayCopyAndInsert(values, values.length, value);

            if (generation == this.generation) {
                keys = newKeys;
                values = newValues;
                return this;
            } else {
                return new HashCollisionNode<>(generation, keyHash, newKeys, newValues);
            }
        }

        private Node<K, V> copyAndReplaceValue(long generation, V value, int index) {
            if (generation == this.generation) {
                values[index] = value;
                return this;
            } else {
                @SuppressWarnings("unchecked")
                V[] newValues = (V[]) MoreArrays.arrayCopyAndReplace(values, index, value);
                return new HashCollisionNode<>(generation, keyHash, keys.clone(), newValues);
            }
        }

        private Node<K, V> copyAndRemoveValue(long generation, int index) {
            @SuppressWarnings("unchecked")
            K[] newKeys = (K[]) MoreArrays.arrayCopyAndRemove(keys, index);

            @SuppressWarnings("unchecked")
            V[] newValues = (V[]) MoreArrays.arrayCopyAndRemove(values, index);

            if (generation == this.generation) {
                keys = newKeys;
                values = newValues;
                return this;
            } else {
                return new HashCollisionNode<>(generation, keyHash, newKeys, newValues);
            }
        }

        @Override
        Search<V> findByKey(K key, int hash, int shift) {
            for (int i = 0; i < keys.length; i++) {
                if (Objects.equals(key, keys[i])) {
                    return Search.found(values[i]);
                }
            }
            return Search.notFound();
        }

        @Override
        K keyAt(int index) {
            return keys[index];
        }

        @Override
        V valueAt(int index) {
            return values[index];
        }

        @Override
        int hashAt(int index) {
            return keyHash;
        }

        @Override
        int payloadArity() {
            return keys.length;
        }
    }

    //endregion

    //region Iterators

    /**
     * The base iterator. It traverses level by level. When it reaches a node,
     * it iterates through all the values and then all the children nodes.
     *
     * It keeps a stack of unfinished nodes; visiting in depth-first order.
     * If the current payload node has child nodes, it is on the top of the stack.
     */
    private static abstract class Iter<K,V, T> implements Iterator<T> {
        private static final Object NOT_DELETABLE = new Object();

        /**
         * 32 bits at 5 bits per level gives 7 maximum levels. This happens whenever
         * there are two values with the same hash.
         */
        private static final int MAX_DEPTH = 7;

        /** The current node that has partially-traversed payload. */
        protected Node<K,V> payloadNode;
        /** cached {@link Node#payloadArity()}  */
        protected int curPayloadArity;
        protected int curPayloadIndex;

        @SuppressWarnings("unchecked")
        Node<K,V>[] parentStack = new Node[MAX_DEPTH];
        private final int[] nodeCursorsAndLengths = new int[MAX_DEPTH * 2];
        private int curStackLevel = -1;

        Iter(BitmapIndexNode<K,V> rootNode) {
            if (rootNode.hasNodes()) {
                curStackLevel = 0;

                parentStack[0] = rootNode;
                nodeCursorsAndLengths[0] = 0;
                nodeCursorsAndLengths[1] = rootNode.nodeArity();
            }

            if (rootNode.hasPayload()) {
                payloadNode = rootNode;
                curPayloadIndex = 0;
                curPayloadArity = rootNode.payloadArity();
            }
        }

        @Override
        public boolean hasNext() {
            // We always iterate over values first, and then search the children
            if (curPayloadIndex < curPayloadArity) {
                return true;
            } else {
                return searchForValueNode();
            }
        }

        /**
         * Depth-first search through children nodes until we find a node with
         * a payload. If the payload node also has children it should be the top
         * of the stack.
         */
        private boolean searchForValueNode() {
            while (curStackLevel >= 0) {
                final int cursor = curStackLevel * 2;

                final int nodeIndex = nodeCursorsAndLengths[cursor];
                final int nodeLength = nodeCursorsAndLengths[cursor + 1];

                if (nodeIndex == nodeLength) {
                    curStackLevel--;
                    continue;
                }

                // got to the next index
                final Node<K,V> nextNode = parentStack[curStackLevel].nodeAt(nodeIndex);
                nodeCursorsAndLengths[cursor]++;

                if (nextNode.hasNodes()) {
                    // put node on next stack level for depth-first traversal
                    final int nextStackLevel = ++curStackLevel;
                    final int nextCursor = nextStackLevel * 2;

                    parentStack[nextStackLevel] = nextNode;
                    nodeCursorsAndLengths[nextCursor] = 0;
                    nodeCursorsAndLengths[(nextCursor + 1)] = nextNode.nodeArity();
                }

                if (nextNode.hasPayload()) {
                    payloadNode = nextNode;
                    curPayloadIndex = 0;
                    curPayloadArity = nextNode.payloadArity();

                    return true;
                }
            }

            return false;
        }

        Object lastKey = NOT_DELETABLE;
        private int lastHash;
        @Override
        public T next() {
            if (hasNext()) {
                K key = payloadNode.keyAt(curPayloadIndex);
                V value = payloadNode.valueAt(curPayloadIndex);

                lastKey = key;
                lastHash = payloadNode.hashAt(curPayloadIndex);
                curPayloadIndex++;

                return fetchValue(key, value);
            } else {
                throw new NoSuchElementException("Passed end of iteration");
            }
        }
        protected abstract T fetchValue(K key, V value);

        @Override
        public void remove() {
            if (lastKey == NOT_DELETABLE) {
                throw new IllegalStateException("The value has already been deleted or the iteration hasn't started");
            } else {
                @SuppressWarnings("unchecked")
                K key = (K) lastKey;
                lastKey = NOT_DELETABLE;
                remove(key, lastHash);
            }
        }
        protected void remove(K key, int keyHash) { throw new UnsupportedOperationException("remove is not supported"); }
    }


    /**
     * Use this to get the keys & values internally b/c it doesn't create new entry
     * objects for every element.
     */
    static class SelfIter<K,V> extends Iter<K,V, Void> {
        V lastValue = null;

        SelfIter(BitmapIndexNode<K, V> rootNode) {
            super(rootNode);
        }

        @Override
        protected Void fetchValue(K key, V value) {
            lastValue = value;
            return null;
        }
    }

    //endregion

    protected BitmapIndexNode<K,V> root;
    protected int size;

    public CowHashMap() {
        //noinspection unchecked
        this(EMPTY_NODE.generation + 1, (BitmapIndexNode<K, V>) EMPTY_NODE, 0);
    }

    private CowHashMap(long generation, BitmapIndexNode<K,V> root, int size) {
        this.generation = generation;
        this.root = root;
        this.size = size;
    }

    //region Map implementation

    @Override
    @SuppressWarnings("unchecked")
    public void forEach(BiConsumer<? super K, ? super V> action) {
        SelfIter<K,V> it = new SelfIter<>(root);

        while (it.hasNext()) {
            it.next();
            action.accept((K)it.lastKey, it.lastValue);
        }
    }

    @Override
    public String toString() {
        if (isEmpty()) return "{}";

        SelfIter<K,V> it = new SelfIter<>(root);
        StringBuilder b = new StringBuilder("{");
        while (true) {
            it.next();
            b.append(it.lastKey);
            b.append("=");
            b.append(it.lastValue);

            if (it.hasNext()) {
                b.append(", ");
            } else {
                return b.toString();
            }
        }
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) return true;
        if (!(other instanceof Map<?,?>)) return false;

        Map<?,?> map = (Map<?, ?>) other;
        if (map.size() != this.size()) return false;

        if (other instanceof CowHashMap<?,?>) {
            CowHashMap<?, ?> that = (CowHashMap<?, ?>) map;
            return this.root.equals(that.root);
        } else {
            return equalsMap(map);
        }
    }

    private boolean equalsMap(Map<?, ?> that) {
        SelfIter<K,V> it = new SelfIter<>(root);

        while (it.hasNext()) {
            it.next();

            @SuppressWarnings("unchecked")
            K key = (K) it.lastKey;
            V value = it.lastValue;
            if (value == null) {
                if (that.get(key) != null || !containsKey(key))
                    return false;
            } else {
                if (!value.equals(that.get(key)))
                    return false;
            }
        }
        return true;
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        m.forEach(this::put);
    }

    @Override
    public boolean containsKey(Object o) {
        @SuppressWarnings("unchecked")
        K key = (K) o;

        Search<V> search = root.findByKey(key, Objects.hashCode(key), 0);
        return search.isFound();
    }

    @Override
    public boolean containsValue(Object value) {
        if (value != null) {
            for (V cur : values()) {
                if (value.equals(cur))
                    return true;
            }
        } else {
            for (V cur : values()) {
                if (cur == null)
                    return true;
            }
        }
        return false;
    }

    @Override
    public V get(Object o) {
        @SuppressWarnings("unchecked")
        K key = (K) o;

        Search<V> search = root.findByKey(key, Objects.hashCode(key), 0);
        return search.value();
    }

    @Override
    public V remove(Object key) {
        return removeKey(key, Objects.hashCode(key)).getAndClear();
    }

    //endregion

    //region Views

    private transient Set<Entry<K, V>> entries;
    @Override
    public Set<Entry<K, V>> entrySet() {

        class EntrySet extends AbstractSet<Entry<K,V>> {
            @Override
            public Iterator<Entry<K, V>> iterator() {
                return new Iter<K, V, Entry<K, V>>(root) {
                    @Override protected Entry<K, V> fetchValue(K key, V value) { return new SimpleImmutableEntry<>(key, value); }
                    @Override protected void remove(K key, int keyHash) { removeKey(key, keyHash).isModifiedAndClear(); }
                };
            }

            @Override
            public int size() {
                return size;
            }
        }

        if (entries != null)
            return entries;

        return (entries = new EntrySet());
    }

    private transient Set<K> keys;
    @Override
    public Set<K> keySet() {
        class KeySet extends AbstractSet<K> {
            @Override public void clear() { CowHashMap.this.clear(); }
            @Override public boolean contains(Object o) { return containsKey(o); }
            @Override public int size() { return size; }
            @Override public boolean remove(Object o) { return removeKey(o, Objects.hashCode(o)).isModifiedAndClear(); }
            @Override public Iterator<K> iterator() {
                return new Iter<K,V, K>(root) {
                    @Override protected K fetchValue(K key, V value) { return key; }
                    @Override protected void remove(K key, int keyHash) { removeKey(key, keyHash).isModifiedAndClear(); }
                };
            }
        }
        return keys != null ? keys : (keys = new KeySet());
    }

    private transient Collection<V> values;
    @Override
    public Collection<V> values() {
        class ValueCollection extends AbstractCollection<V> {
            @Override public int size() { return size; }
            @Override public void clear() { super.clear(); }

            @Override
            public Iterator<V> iterator() {
                return new Iter<K,V, V>(root) {
                    @Override protected V fetchValue(K key, V value) { return value; }
                    @Override protected void remove(K key, int keyHash) { removeKey(key, keyHash).isModifiedAndClear(); }
                };
            }
        }

        return values != null ? values : (values = new ValueCollection());
    }

    //endregion

    //region Utility methods

    static int mask(final int keyHash, final int shift) {
        return (keyHash >>> shift) & BIT_PARTITION_MASK;
    }

    static int bitpos(final int mask) {
        return 1 << mask;
    }

    static int bitpos(final int keyhash, final int shift) {
        return bitpos(mask(keyhash, shift));
    }

    //endregion
    //region Migration from old style
    // TODO reorganize

    @Override
    public int hashCode() {
        int hash = size;

        SelfIter<?,?> it = new SelfIter<>(root);
        while(it.hasNext()) {
            it.next();
            hash += Objects.hashCode(it.lastKey) ^ Objects.hashCode(it.lastValue);
        }

        return hash;
    }

    private long generation;

    /**
     * Likely a lot of change going on so this is re-used.
     * Use the getAndClear and isModifiedAndClear methods.
     */
    private final Change<V> change = new Change<>();

    @Override
    public V put(K key, V value) {
        int keyHash = Objects.hashCode(key);
        root = root.put(generation, key, value, keyHash, 0, change);
        if (change.isModified() && !change.isReplaced()) {
            size++;
        }

        return change.getAndClear();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void clear() {
        this.size = 0;
        this.root = (BitmapIndexNode<K, V>) EMPTY_NODE;
    }

    protected Change<V> removeKey(Object key, int keyHash) {
        root = root.remove(generation, key, keyHash, 0, change);
        if (change.isModified()) {
            size--;
        }
        return change;
    }

    @Override
    public CowHashMap<K, V> fork() {
        return new CowHashMap<>(++generation, root, size);
    }

    //endregion
}
