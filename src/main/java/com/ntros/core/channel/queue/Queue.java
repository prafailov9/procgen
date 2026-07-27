package com.ntros.core.channel.queue;

public interface Queue<T> {

    void add(T value);
    T remove();
    T peek();
    boolean isEmpty();
    int size();


}
