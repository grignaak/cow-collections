package com.github.grignaak.collections;

/**
 * This gets passed to nodes during an update so the inner leaves can return modification
 * state. It is common for builders to keep one of these and use it on every mutate.
 *
 * <h3>Invariants</h3>
 * <ul>
 *     <li>isReplaced implies isModified</li>
 *     <li>replacedValue != null implies isReplaced</li>
 * </ul>
 */
class Change<V> {
    private V replacedValue;
    private boolean isModified;
    private boolean isReplaced;

    public boolean isModified() { return isModified; }
    public boolean isReplaced() { return isReplaced; }

    public void modified() { this.isModified = true; }
    public void updated(V replacement) {
        this.replacedValue = replacement;
        this.isReplaced = true;
        this.isModified = true;
    }

    public V getAndClear() {
        V ret = replacedValue;
        replacedValue = null;
        isModified = false;
        isReplaced = false;
        return ret;
    }

    public boolean isModifiedAndClear() {
        boolean wasModified = isModified;
        replacedValue = null;
        isModified = false;
        isReplaced = false;
        return wasModified;
    }

    @Override
    public String toString() {
        if (isReplaced) return String.format("Replaced{%s}", replacedValue);
        else if (isModified) return "Modified";
        else return "Unchanged";
    }
}
