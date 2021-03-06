package com.higherfrequencytrading.chronicle.datamodel;

import com.higherfrequencytrading.chronicle.CollectionListener;
import com.higherfrequencytrading.chronicle.Excerpt;

import java.util.*;

import static com.higherfrequencytrading.chronicle.datamodel.WrapperEvent.*;

/**
 * @author peter.lawrey
 */
public class SetWrapper<E> implements ObservableSet<E> {
    private final DataStore dataStore;
    private final String name;
    private final Class<E> eClass;
    private final Set<E> underlying;
    private final int maxMessageSize;
    private final List<CollectionListener<E>> listeners = new ArrayList<CollectionListener<E>>();
    private boolean notifyOff = false;

    public SetWrapper(DataStore dataStore, String name, Class<E> eClass, Set<E> underlying, int maxMessageSize) {
        this.dataStore = dataStore;
        this.name = name;
        this.eClass = eClass;
        this.underlying = underlying;
        this.maxMessageSize = maxMessageSize;
        dataStore.add(name, this);
    }

    public void addListener(CollectionListener<E> listener) {
        listeners.add(listener);
    }

    public void removeListener(CollectionListener<E> listener) {
        listeners.remove(listener);
    }

    @Override
    public boolean add(E e) {
        if (!underlying.add(e)) return false;
        writeAdd(e);
        return true;
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        List<E> added = new ArrayList<E>();
        for (E e : c)
            if (underlying.add(e))
                added.add(e);
        if (added.isEmpty())
            return false;
        if (added.size() == 1)
            writeAdd(added.get(0));
        else
            writeAddAll(added);
        return true;
    }

    @Override
    public boolean contains(Object o) {
        return underlying.contains(o);
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return underlying.containsAll(c);
    }

    @Override
    public void clear() {
        writeClear();
        underlying.clear();
    }

    @Override
    public boolean equals(Object o) {
        return underlying.equals(o);
    }

    @Override
    public int hashCode() {
        return underlying.hashCode();
    }

    @Override
    public boolean isEmpty() {
        return underlying.isEmpty();
    }

    @Override
    public Iterator<E> iterator() {
        return new Iterator<E>() {
            Iterator<E> iter = underlying.iterator();
            E last = null;

            @Override
            public boolean hasNext() {
                return iter.hasNext();
            }

            @Override
            public E next() {
                return last = iter.next();
            }

            @Override
            public void remove() {
                iter.remove();
                int maxSize = maxMessageSize;
                Excerpt excerpt = getExcerpt(maxSize, remove);
                excerpt.writeObject(last);
                excerpt.finish();
            }
        };
    }

    @Override
    public void notifyOff(boolean notifyOff) {
        this.notifyOff = notifyOff;
    }

    @Override
    public void onExcerpt(Excerpt excerpt) {
        WrapperEvent event = excerpt.readEnum(WrapperEvent.class);
        if (!notifyOff) {
            for (int i = 0; i < listeners.size(); i++)
                listeners.get(i).eventStart(excerpt.index(), name);
        }
        try {
            switch (event) {
                case add: {
                    @SuppressWarnings("unchecked")
                    E e = (E) excerpt.readObject();
                    underlying.add(e);
                    if (!notifyOff)
                        for (int i = 0; i < listeners.size(); i++)
                            listeners.get(i).add(e);

                    break;

                }
                case addAll: {
                    List<E> eList = new ArrayList<E>();
                    excerpt.readList(eList);
                    underlying.addAll(eList);
                    if (!notifyOff)
                        for (int i = 0; i < listeners.size(); i++)
                            listeners.get(i).addAll(eList);

                    break;
                }

                case remove: {
                    @SuppressWarnings("unchecked")
                    E e = (E) excerpt.readObject();
                    underlying.remove(e);
                    if (!notifyOff)
                        for (int i = 0; i < listeners.size(); i++)
                            listeners.get(i).remove(e);

                    break;
                }
                case removeAll: {
                    List<E> eList = new ArrayList<E>();
                    excerpt.readList(eList);
                    underlying.removeAll(eList);
                    if (!notifyOff)
                        for (int i = 0; i < listeners.size(); i++)
                            listeners.get(i).removeAll(eList);
                    break;
                }
                case clear: {
                    if (!notifyOff)
                        for (int i = 0; i < listeners.size(); i++)
                            listeners.get(i).removeAll(underlying);
                    underlying.clear();
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (!notifyOff) {
            boolean lastEvent = !excerpt.hasNextIndex();

            for (int i = 0; i < listeners.size(); i++) {
                listeners.get(i).eventEnd(lastEvent);
            }
        }
    }

    @Override
    public boolean remove(Object o) {
        if (!underlying.remove(o)) return false;
        writeRemove(o);
        return true;
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        List<E> removed = new ArrayList<E>();
        for (Object o : c)
            if (underlying.remove(o))
                removed.add((E) o);
        if (removed.isEmpty())
            return false;
        if (removed.size() == 0)
            writeRemove(removed.get(0));
        else
            writeRemoveAll(removed);
        return true;
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        List<Object> toremove = new ArrayList<Object>(size());
        for (E e : this) {
            if (!c.contains(e))
                toremove.add(e);
        }
        if (toremove.isEmpty())
            return false;
        return removeAll(toremove);
    }

    @Override
    public int size() {
        return underlying.size();
    }

    @Override
    public Object[] toArray() {
        return underlying.toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return underlying.toArray(a);
    }

    @Override
    public String toString() {
        return underlying.toString();
    }

    private Excerpt getExcerpt(int maxSize, WrapperEvent event) {
        Excerpt excerpt = dataStore.startExcerpt(maxSize + 2 + event.name().length(), name);
        excerpt.writeEnum(event);
        return excerpt;
    }

    private void writeAdd(E element) {
        Excerpt excerpt = getExcerpt(maxMessageSize, add);
        long eventId = excerpt.index();
        excerpt.writeObject(element);
        excerpt.finish();
        if (!notifyOff && !listeners.isEmpty()) {
            for (int i = 0; i < listeners.size(); i++) {
                CollectionListener<E> listener = listeners.get(i);
                listener.eventStart(eventId, name);
                listener.add(element);
                listener.eventEnd(true);
            }
        }
    }

    private void writeAddAll(Collection<E> added) {
        Excerpt excerpt = getExcerpt(maxMessageSize * added.size(), addAll);
        long eventId = excerpt.index();
        excerpt.writeList(added);
        excerpt.finish();
        if (!notifyOff && !listeners.isEmpty()) {
            for (int i = 0; i < listeners.size(); i++) {
                CollectionListener<E> listener = listeners.get(i);
                listener.eventStart(eventId, name);
                listener.addAll(added);
                listener.eventEnd(true);
            }
        }
    }

    private void writeClear() {
        Excerpt excerpt = dataStore.startExcerpt(10, name);
        long eventId = excerpt.index();
        excerpt.writeEnum("clear");
        excerpt.finish();
        if (!notifyOff && !listeners.isEmpty()) {
            for (int i = 0; i < listeners.size(); i++) {
                CollectionListener<E> listener = listeners.get(i);
                listener.eventStart(eventId, name);
                listener.removeAll(underlying);
                listener.eventEnd(true);
            }
        }
    }

    private void writeRemove(Object o) {
        Excerpt excerpt = getExcerpt(maxMessageSize, remove);
        long eventId = excerpt.index();
        excerpt.writeObject(o);
        excerpt.finish();
        if (!notifyOff && !listeners.isEmpty()) {
            for (int i = 0; i < listeners.size(); i++) {
                CollectionListener<E> listener = listeners.get(i);
                listener.eventStart(eventId, name);
                listener.remove((E) o);
                listener.eventEnd(true);
            }
        }
    }

    private void writeRemoveAll(List<E> removed) {
        Excerpt excerpt = getExcerpt(maxMessageSize * removed.size(), removeAll);
        long eventId = excerpt.index();
        excerpt.writeList(removed);
        excerpt.finish();
        if (!notifyOff && !listeners.isEmpty()) {
            for (int i = 0; i < listeners.size(); i++) {
                CollectionListener<E> listener = listeners.get(i);
                listener.eventStart(eventId, name);
                listener.removeAll(removed);
                listener.eventEnd(true);
            }
        }
    }
}
