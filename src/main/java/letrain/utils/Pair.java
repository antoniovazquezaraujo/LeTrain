package letrain.utils;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
public class Pair<T1, T2> {
    private T1 first;
    private T2 second;

    public Pair() {}

    public Pair(T1 first, T2 second) {
        this.first = first;
        this.second = second;
    }

    public T1 getFirst() {
        return first;
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    public T1 getKey() {
        return first;
    }

    public T2 getSecond() {
        return second;
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    public T2 getValue() {
        return second;
    }

    public void setFirst(T1 first) {
        this.first = first;
    }

    public void setSecond(T2 second) {
        this.second = second;
    }

    // toString
    @Override
    public String toString() {
        return "(" + first + ", " + second + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Pair<?, ?> pair = (Pair<?, ?>) o;
        return java.util.Objects.equals(first, pair.first)
                && java.util.Objects.equals(second, pair.second);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(first, second);
    }
}
