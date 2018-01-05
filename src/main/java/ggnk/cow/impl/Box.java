package ggnk.cow.impl;

public class Box<T> {
    private T boxed;

    public void box(T boxed) {
        this.boxed = boxed;
    }

    public T unbox() {
        return boxed;
    }
}
