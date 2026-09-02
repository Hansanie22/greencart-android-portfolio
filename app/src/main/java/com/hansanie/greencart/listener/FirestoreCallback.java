package com.hansanie.greencart.listener;


public interface FirestoreCallback<T> {
    void onCallback(T data);
}
