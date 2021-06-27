package de.vogella.algorithms.dijkstra.model;

public class Vertex<T> {
    final private String id;
    final private String name;
    private T object;


    public Vertex(String id, String name, T object) {
        this.id = id;
        this.name = name;
        this.object = object;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((id == null) ? 0 : id.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Vertex other = (Vertex) obj;
        if (id == null) {
            return other.id == null;
        } else return id.equals(other.id);
    }

    @Override
    public String toString() {
        return name;
    }

    public T getObject() {
        return object;
    }

} 