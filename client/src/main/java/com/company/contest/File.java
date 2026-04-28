package com.company.contest;

public class File {

    private boolean isOpen = false;

    void create() {
        isOpen = true;
    }

    void open() {
        isOpen = true;
    }

    String read() {
        return "read";
    }

    void close() {
        isOpen = false;
    }
}
