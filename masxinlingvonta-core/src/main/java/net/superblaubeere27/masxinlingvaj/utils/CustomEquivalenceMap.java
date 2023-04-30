package net.superblaubeere27.masxinlingvaj.utils;

import java.util.*;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

public class CustomEquivalenceMap<K, V> implements Map<K, V> {
    private final ArrayList<Entry<K, V>> values;
    private final BiPredicate<K, Object> equivalenceCriteria;

    public CustomEquivalenceMap(BiPredicate<K, Object> equivalenceCriteria) {
        this.values = new ArrayList<>();
        this.equivalenceCriteria = equivalenceCriteria;
    }

    @Override
    public int size() {
        return this.values.size();
    }

    @Override
    public boolean isEmpty() {
        return this.values.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return this.get(key) != null;
    }

    @Override
    public boolean containsValue(Object value) {
        return value == null ?
                this.values.stream().anyMatch(Objects::isNull) :
                this.values.stream().anyMatch(x -> x.equals(value));
    }

    @Override
    public V get(Object key) {
        return (key == null ?
                this.values.stream().filter(Objects::isNull) :
                this.values.stream().filter(x -> this.equivalenceCriteria.test(x.getKey(), key))).map(Entry::getValue).findFirst().orElse(null);
    }

    @Override
    public V put(K key, V value) {
        var entry = values.stream()
                .filter(x -> this.equivalenceCriteria.test(x.getKey(), key))
                .findFirst();

        if (entry.isPresent()) {
            return entry.get().setValue(value);
        }

        this.values.add(new CustomEntry(key, value));

        return null;
    }

    @Override
    public V remove(Object key) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        m.forEach(this::put);
    }

    @Override
    public void clear() {
        this.values.clear();
    }

    @Override
    public Set<K> keySet() {
        return this.values.stream().map(Entry::getKey).collect(Collectors.toSet());
    }

    @Override
    public Collection<V> values() {
        return this.values.stream().map(Entry::getValue).collect(Collectors.toList());
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        return new EntrySet();
    }

    private final class EntrySet extends AbstractSet<Entry<K, V>> {

        @Override
        public Iterator<Entry<K, V>> iterator() {
            return CustomEquivalenceMap.this.values.iterator();
        }

        @Override
        public int size() {
            return CustomEquivalenceMap.this.values.size();
        }
    }

    private class CustomEntry implements java.util.Map.Entry<K, V> {
        private final K key;
        private V value;

        private CustomEntry(K key, V value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public K getKey() {
            return this.key;
        }

        @Override
        public V getValue() {
            return this.value;
        }

        @Override
        public V setValue(V value) {
            var oldVal = this.value;

            this.value = value;

            return oldVal;
        }
    }
}
