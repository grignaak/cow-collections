package ggnk.cow.impl;

@Beta
public class MoreArrays {
    private MoreArrays() {/* utility */}

    public static Object[] arrayCopyAndAppend(Object[] src, Object appended) {
        Object[] dst = copyToLength(src, src.length + 1);
        dst[src.length] = appended;

        return dst;
    }

    public static Object[] arrayCopyAndAppend(Object[] src, Object first, Object second) {
        Object[] dst = copyToLength(src, src.length + 2);
        dst[src.length] = first;
        dst[src.length+1] = second;

        return dst;
    }

    public static Object[] arrayCopyAndInsert(Object[] src, int index, Object value) {
        Object[] dst = new Object[src.length + 1];

        System.arraycopy(src, 0, dst, 0, index);
        dst[index] = value;
        System.arraycopy(src, index, dst, index+1, src.length - index);

        return dst;
    }

    public static Object[] arrayCopyAndReplace(Object[] src, int index, Object value) {
        Object[] dst = new Object[src.length];
        System.arraycopy(src, 0, dst, 0, src.length);
        dst[index] = value;
        return dst;
    }

    public static Object[] arrayCopyAndReplacePair(Object[] src, int index, Object first, Object second) {
        Object[] dst = new Object[src.length];
        System.arraycopy(src, 0, dst, 0, src.length);
        dst[index] = first;
        dst[index + 1] = second;
        return dst;
    }

    public static Object[] arrayCopyAndInsert(Object[] src, int index, Object key, Object value) {
        Object[] dst = new Object[src.length + 2];

        System.arraycopy(src, 0, dst, 0, index);
        dst[index] = key;
        dst[index + 1] = value;
        System.arraycopy(src, index, dst, index+2, src.length - index);

        return dst;
    }

    public static int[] arrayCopyAndInsert(int[] src, int index, int value) {
        int[] dst = new int[src.length + 1];

        System.arraycopy(src, 0, dst, 0, index);
        dst[index] = value;
        System.arraycopy(src, index, dst, index+1, src.length - index);

        return dst;
    }

    public static Object[] arrayCopyAndRemove(Object[] src, int index) {
        Object[] dst = new Object[src.length - 1];

        int indexAfter = index + 1;
        System.arraycopy(src, 0, dst, 0, index);
        System.arraycopy(src, indexAfter, dst, index, src.length - (indexAfter));

        return dst;
    }

    public static int[] arrayCopyAndRemove(int[] src, int index) {
        int[] dst = new int[src.length - 1];

        int indexAfter = index + 1;
        System.arraycopy(src, 0, dst, 0, index);
        System.arraycopy(src, indexAfter, dst, index, src.length - (indexAfter));

        return dst;
    }

    public static Object[] arrayCopyAndRemovePair(Object[] src, int index) {
        Object[] dst = new Object[src.length - 2];

        int indexAfter = index + 2;
        System.arraycopy(src, 0, dst, 0, index);
        System.arraycopy(src, indexAfter, dst, index, src.length - (indexAfter));

        return dst;
    }

    /**
     * A strangely specific array copy. Remove two elements at {@code removeIndex}
     * and {@code removeIndex+1}; and insert the given value at {@code addIndex}.
     * All indices are in terms of the <em>source</em> array.
     *
     * <p>The added index may not be within the removed indices.</p>
     */
    public static Object[] arrayCopyAndRemovePairAndInsert(Object[] src, int removeIndex, int addIndex, Object insertion) {
        // The copy is made in three chunks (the pipes point at the indices involved):
        //
        //           remove         add
        //             V             V
        //         | 1 |---|    2    |    3    |
        // src = [ a b * * c d e f g i j k l m ]
        // dst = [ a b c d e f g + i j k l m ]
        //         | 1 |    2    |-|    3    |
        int srcIndexAfterRemove = removeIndex + 2;
        assert srcIndexAfterRemove <= addIndex;

        // removing two, adding one
        Object[] dst = new Object[src.length - 1];

        System.arraycopy(src, 0, dst, 0, removeIndex);

        System.arraycopy(src, srcIndexAfterRemove, dst, removeIndex, (addIndex-srcIndexAfterRemove));
        dst[addIndex - 2] = insertion;
        System.arraycopy(src, addIndex, dst, (addIndex-1), (src.length-addIndex));

        return dst;
    }

    public static Object[] arrayCopyAndRemovePairAndElement(Object[] src, int pairIndex, int laterIndex) {
        // The copy is made in three chunks (the pipes point at the indices involved):
        //
        //            pair        element
        //             V             V
        //         | 1 |---|    2    |-|   3   |
        // src = [ a b * * c d e f g * h i j k ]
        // dst = [ a b c d e f g h i j k ]
        //         | 1 |    2    |   3   |
        int srcIndexAfterPair = pairIndex + 2;
        assert srcIndexAfterPair <= laterIndex;

        Object[] dst = new Object[src.length - 3];

        System.arraycopy(src, 0, dst, 0, pairIndex);

        System.arraycopy(src, srcIndexAfterPair, dst, pairIndex, (laterIndex-srcIndexAfterPair));
        System.arraycopy(src, laterIndex+1, dst, laterIndex-2, (src.length-laterIndex-1));

        return dst;
    }


    /**
     * A strangely specific array copy. Add the two elements at {@code addIndex}
     * and {@code addIndex+1}; and remove an element at {@code removeIndex};
     * All indices are in terms of the <em>source</em> array.
     */
    public static Object[] arrayCopyAndInsertPairAndRemove(Object[] src, int addIndex, Object first, Object second, int removeIndex) {
        // The copy is made in three chunks (the pipes point at the indices involved):
        //
        //            add     remove
        //             V         V
        //         | 1 |    2    |-|   3   |
        // src = [ a b e f g h i J k l m n ]
        // dst = [ a b C D e f g h i k l m n ]
        //         | 1 |---|    2    |   3   |
        assert addIndex <= removeIndex;

        // adding two, removing one
        Object[] dst = new Object[src.length + 1];

        System.arraycopy(src, 0, dst, 0, addIndex);
        dst[addIndex] = first;
        dst[addIndex + 1] = second;

        System.arraycopy(src, addIndex, dst, (addIndex+2), (removeIndex-addIndex));

        int srcIndexAfterRemoval = removeIndex + 1;
        System.arraycopy(src, srcIndexAfterRemoval, dst, (removeIndex+2), (src.length-srcIndexAfterRemoval));

        return dst;
    }


    public static <K, V> Object[] arrayCopyAndInsertPairAndElement(Object[] src, int pairIndex, Object first, Object second, int elementIndex, Object element) {
        // The copy is made in three chunks (the pipes point at the indices involved):
        //
        //            pair     element
        //             V         V
        //         | 1 |    2    |   3   |
        // src = [ a b e f g h i k l m n ]
        // dst = [ a b + + e f g h i + k l m n ]
        //         | 1 |---|    2    |-|   3   |
        assert pairIndex <= elementIndex;

        Object[] dst = new Object[src.length + 3];

        System.arraycopy(src, 0, dst, 0, pairIndex);
        dst[pairIndex] = first;
        dst[pairIndex + 1] = second;

        System.arraycopy(src, pairIndex, dst, (pairIndex+2), (elementIndex-pairIndex));
        dst[elementIndex + 2] = element;
        System.arraycopy(src, elementIndex, dst, (elementIndex+3), (src.length-elementIndex));

        return dst;
    }

    public static Object[] copyToLength(Object[] src, int newLength) {
        Object[] dst = new Object[newLength];
        System.arraycopy(src, 0, dst, 0, Math.min(src.length, newLength));
        return dst;
    }

    public static <T> T swapOut(Object[] array, int index, T value) {
        @SuppressWarnings("unchecked")
        T old = (T) array[index];
        array[index] = value;
        return old;
    }

    public static Object[] appendRanges(Object[] src1, int start1, int len1,
                                        Object[] src2, int start2, int len2) {
        Object[] dst = new Object[len1 + len2];
        System.arraycopy(src1, start1, dst, 0, len1);
        System.arraycopy(src2, start2, dst, len1, len2);

        return dst;
    }

    public static <K> Object[] appendRanges(Object[] src1, int start1, int len1,
                                            Object[] src2, int start2, int len2,
                                            Object[] src3, int start3, int len3) {
        Object[] dst = new Object[len1 + len2 + len3];
        System.arraycopy(src1, start1, dst, 0, len1);
        System.arraycopy(src2, start2, dst, len1, len2);
        System.arraycopy(src3, start3, dst, (len1+len2), len3);

        return dst;
    }

    public static Object[] appendRanges(Object[] src1, int start1, int len1,
                                        Object[] src2, int start2, int len2,
                                        Object[] src3, int start3, int len3,
                                        Object[] src4, int start4, int len4,
                                        Object[] src5, int start5, int len5) {
        Object[] dst = new Object[len1 + len2 + len3 + len4 + len5];

        int start = 0;
        System.arraycopy(src1, start1, dst, start, len1);
        System.arraycopy(src2, start2, dst, (start += len1), len2);
        System.arraycopy(src3, start3, dst, (start += len2), len3);
        System.arraycopy(src4, start4, dst, (start += len3), len4);
        System.arraycopy(src5, start5, dst, (start + len4), len5);

        return dst;
    }
}
