/*
 * Copyright (c) 2017-2021 DarkCompet. All rights reserved.
 */

package tool.compet.livedata;

import androidx.annotation.NonNull;

import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * LinkedList, which pretends to be a map and supports modifications during iterations.
 * It is NOT thread safe.
 *
 * @param <K> Key type
 * @param <V> Value type
 */
class MySafeIterableMap<K, V> implements Iterable<Map.Entry<K, V>> {
	Entry<K, V> start;
	Entry<K, V> end;
	// Using WeakHashMap over List<WeakReference>, so we don't have to manually remove
	// WeakReferences that have null in them.
	WeakHashMap<SupportRemove<K, V>, Boolean> iterators = new WeakHashMap<>();
	int size = 0;

	public Entry<K, V> get(K k) {
		Entry<K, V> currentNode = start;
		while (currentNode != null) {
			if (currentNode.key.equals(k)) {
				break;
			}
			currentNode = currentNode.next;
		}
		return currentNode;
	}

	/**
	 * If the specified key is not already associated
	 * with a value, associates it with the given value.
	 *
	 * @param key key with which the specified value is to be associated
	 * @param v   value to be associated with the specified key
	 * @return the previous value associated with the specified key,
	 * or {@code null} if there was no mapping for the key
	 */
	public V putIfAbsent(@NonNull K key, @NonNull V v) {
		Entry<K, V> entry = get(key);
		if (entry != null) {
			return entry.value;
		}
		put(key, v);
		return null;
	}

	public Entry<K, V> put(@NonNull K key, @NonNull V v) {
		Entry<K, V> newEntry = new Entry<>(key, v);
		size++;
		if (end == null) {
			start = newEntry;
			end = start;
			return newEntry;
		}
		end.next = newEntry;
		newEntry.prev = end;
		end = newEntry;
		return newEntry;
	}

	/**
	 * Removes the mapping for a key from this map if it is present.
	 *
	 * @param key key whose mapping is to be removed from the map
	 * @return the previous value associated with the specified key,
	 * or {@code null} if there was no mapping for the key
	 */
	public V remove(@NonNull K key) {
		Entry<K, V> toRemove = get(key);
		if (toRemove == null) {
			return null;
		}
		size--;
		if (!iterators.isEmpty()) {
			for (SupportRemove<K, V> iter : iterators.keySet()) {
				iter.supportRemove(toRemove);
			}
		}

		if (toRemove.prev != null) {
			toRemove.prev.next = toRemove.next;
		}
		else {
			start = toRemove.next;
		}

		if (toRemove.next != null) {
			toRemove.next.prev = toRemove.prev;
		}
		else {
			end = toRemove.prev;
		}

		toRemove.next = null;
		toRemove.prev = null;
		return toRemove.value;
	}

	/**
	 * @return the number of elements in this map
	 */
	public int size() {
		return size;
	}

	/**
	 * @return an ascending iterator, which doesn't include new elements added during an
	 * iteration.
	 */
	@NonNull
	@Override
	public Iterator<Map.Entry<K, V>> iterator() {
		ListIterator<K, V> iterator = new AscendingIterator<>(start, end);
		iterators.put(iterator, false);
		return iterator;
	}

	/**
	 * @return an descending iterator, which doesn't include new elements added during an
	 * iteration.
	 */
	public Iterator<Map.Entry<K, V>> descendingIterator() {
		DescendingIterator<K, V> iterator = new DescendingIterator<>(end, start);
		iterators.put(iterator, false);
		return iterator;
	}

	/**
	 * return an iterator with additions.
	 */
	public IteratorWithAdditions iteratorWithAdditions() {
		IteratorWithAdditions iterator = new IteratorWithAdditions();
		iterators.put(iterator, false);
		return iterator;
	}

	/**
	 * @return eldest added entry or null
	 */
	public Map.Entry<K, V> eldest() {
		return start;
	}

	/**
	 * @return newest added entry or null
	 */
	public Map.Entry<K, V> newest() {
		return end;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this) {
			return true;
		}
		if (! (obj instanceof MySafeIterableMap)) {
			return false;
		}
		MySafeIterableMap map = (MySafeIterableMap) obj;
		if (this.size() != map.size()) {
			return false;
		}
		Iterator<Map.Entry<K, V>> iterator1 = iterator();
		Iterator iterator2 = map.iterator();
		while (iterator1.hasNext() && iterator2.hasNext()) {
			Map.Entry<K, V> next1 = iterator1.next();
			Object next2 = iterator2.next();
			if ((next1 == null && next2 != null) || (next1 != null && !next1.equals(next2))) {
				return false;
			}
		}
		return !iterator1.hasNext() && !iterator2.hasNext();
	}

	@Override
	public int hashCode() {
		int h = 0;
		for (Map.Entry<K, V> kvEntry : this) {
			h += kvEntry.hashCode();
		}
		return h;
	}

	@NonNull
	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("[");
		Iterator<Map.Entry<K, V>> iterator = iterator();
		while (iterator.hasNext()) {
			builder.append(iterator.next().toString());
			if (iterator.hasNext()) {
				builder.append(", ");
			}
		}
		builder.append("]");
		return builder.toString();
	}

	private abstract static class ListIterator<K, V> implements Iterator<Map.Entry<K, V>>,
		SupportRemove<K, V> {
		Entry<K, V> expectedEnd;
		Entry<K, V> next;

		ListIterator(Entry<K, V> start, Entry<K, V> expectedEnd) {
			this.expectedEnd = expectedEnd;
			this.next = start;
		}

		@Override
		public boolean hasNext() {
			return next != null;
		}

		@Override
		public void supportRemove(@NonNull Entry<K, V> entry) {
			if (expectedEnd == entry && entry == next) {
				next = null;
				expectedEnd = null;
			}
			if (expectedEnd == entry) {
				expectedEnd = backward(expectedEnd);
			}
			if (next == entry) {
				next = nextNode();
			}
		}

		private Entry<K, V> nextNode() {
			if (next == expectedEnd || expectedEnd == null) {
				return null;
			}
			return forward(next);
		}

		@Override
		public Map.Entry<K, V> next() {
			Map.Entry<K, V> result = next;
			next = nextNode();
			return result;
		}

		abstract Entry<K, V> forward(Entry<K, V> entry);

		abstract Entry<K, V> backward(Entry<K, V> entry);
	}

	static class AscendingIterator<K, V> extends ListIterator<K, V> {
		AscendingIterator(Entry<K, V> start, Entry<K, V> expectedEnd) {
			super(start, expectedEnd);
		}

		@Override
		Entry<K, V> forward(Entry<K, V> entry) {
			return entry.next;
		}

		@Override
		Entry<K, V> backward(Entry<K, V> entry) {
			return entry.prev;
		}
	}

	private static class DescendingIterator<K, V> extends ListIterator<K, V> {
		DescendingIterator(Entry<K, V> start, Entry<K, V> expectedEnd) {
			super(start, expectedEnd);
		}

		@Override
		Entry<K, V> forward(Entry<K, V> entry) {
			return entry.prev;
		}

		@Override
		Entry<K, V> backward(Entry<K, V> entry) {
			return entry.next;
		}
	}

	private class IteratorWithAdditions implements Iterator<Map.Entry<K, V>>, SupportRemove<K, V> {
		private Entry<K, V> current;
		private boolean beforeStart = true;

		IteratorWithAdditions() {
		}

		@Override
		public void supportRemove(@NonNull Entry<K, V> entry) {
			if (entry == current) {
				current = current.prev;
				beforeStart = (current == null);
			}
		}

		@Override
		public boolean hasNext() {
			if (beforeStart) {
				return start != null;
			}
			return current != null && current.next != null;
		}

		@Override
		public Map.Entry<K, V> next() {
			if (beforeStart) {
				beforeStart = false;
				current = start;
			}
			else {
				current = current != null ? current.next : null;
			}
			return current;
		}
	}

	interface SupportRemove<K, V> {
		void supportRemove(@NonNull Entry<K, V> entry);
	}

	static class Entry<K, V> implements Map.Entry<K, V> {
		@NonNull final K key;
		@NonNull final V value;
		Entry<K, V> next;
		Entry<K, V> prev;

		Entry(@NonNull K key, @NonNull V value) {
			this.key = key;
			this.value = value;
		}

		@NonNull
		@Override
		public K getKey() {
			return key;
		}

		@NonNull
		@Override
		public V getValue() {
			return value;
		}

		@Override
		public V setValue(V value) {
			throw new UnsupportedOperationException("An entry modification is not supported");
		}

		@NonNull
		@Override
		public String toString() {
			return key + "=" + value;
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this) return true;
			if (! (obj instanceof Entry)) return false;

			Entry entry = (Entry) obj;

			return key.equals(entry.key) && value.equals(entry.value);
		}

		@Override
		public int hashCode() {
			return key.hashCode() ^ value.hashCode();
		}
	}
}
