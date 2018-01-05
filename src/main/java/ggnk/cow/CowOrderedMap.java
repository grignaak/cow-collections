package ggnk.cow;

public interface CowOrderedMap<K,V> extends CowMap<K,V>, OrderedMap<K,V> {
    @Override CowOrderedMap<K,V> fork();
}
