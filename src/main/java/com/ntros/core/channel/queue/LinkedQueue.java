package com.ntros.core.channel.queue;

public class LinkedQueue<T> implements Queue<T> {

  private Node<T> head; // next element out
  private Node<T> tail; // last element in
  private int size;

  @Override
  public void add(T value) {
    var node = new Node<T>(value);

    if (isEmpty()) {
      head = tail = node;
    } else {
      tail.next = node;
      tail = node;
    }
    size++;
  }

  @Override
  public T remove() {
    if (isEmpty()) {
      return null;
    }
    T v = head.value;
    head = head.next;

    if (head == null) {
      tail = null;
    }
    size--;
    return v;
  }

  @Override
  public T peek() {
    return isEmpty() ? null : head.value;
  }

  @Override
  public boolean isEmpty() {
    return size == 0;
  }

  @Override
  public int size() {
    return size;
  }

  private static class Node<T> {
    Node<T> next;
    T value;

    Node(T v) {
      value = v;
    }
  }
}
