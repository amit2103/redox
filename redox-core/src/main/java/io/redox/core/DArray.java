package io.redox.core;

import java.util.Iterator;
import java.util.NoSuchElementException;

/** Read-only view over an array token and its children. */
public final class DArray implements Iterable<DElement> {

    private final Document doc;
    private final int      id;
    private final int      version;

    DArray(Document doc, int id, int version) {
        this.doc     = doc;
        this.id      = id;
        this.version = version;
    }

    public int size() {
        return DToken.containerCount(doc.getToken(id));
    }

    /** O(n) indexed access — walks the tape from the first child. */
    public DElement get(int index) {
        if (index < 0 || index >= size())
            throw new IndexOutOfBoundsException("index " + index + ", size " + size());
        int cur = id + 1; // first child is immediately after array token
        for (int i = 0; i < index; i++) cur = doc.nextToken(cur);
        return new DElement(doc, cur, version);
    }

    @Override
    public Iterator<DElement> iterator() {
        return new Iter();
    }

    private final class Iter implements Iterator<DElement> {
        private int cur     = id + 1; // first child
        private int remain  = size();

        @Override public boolean hasNext() { return remain > 0; }

        @Override public DElement next() {
            if (remain-- == 0) throw new NoSuchElementException();
            DElement e = new DElement(doc, cur, version);
            cur = doc.nextToken(cur);
            return e;
        }
    }
}
