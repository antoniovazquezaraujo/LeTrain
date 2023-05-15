package letrain.utils;

import java.io.Serializable;

public class Pair<T1, T2> implements Serializable {

    private T1 first;
    private T2 second;

    public Pair(T1 first, T2 second) {
        this.first = first;
        this.second = second;
    }

    public T1 getFirst() {
        return first;
    }

    public T1 getKey() {
        return first;
    }

    public T2 getSecond() {
        return second;
    }

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

}
