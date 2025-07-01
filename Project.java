package com.dt176g.project;

import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Entry point for the chatbot application.
 * Starts the server in a background thread and then launches the GUI client.
 *
 * @author Roka1901
 */
public final class Project {

    /**
     * Private constructor to prevent instantiation of this utility class.
     * Throws IllegalStateException if called.
     */
    private Project() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Starts ChatServer in its own thread, then launches ChatClient
     * when the server signals it's ready using a BehaviorSubject.
     */
    public static void main(final String... args) {
        new Thread(() -> {
            try {
                new ChatServer();
            } catch (Exception e) {
                System.err.println("Server failed to start: " + e.getMessage());
            }
        }, "Server-Thread").start();

        ChatServer.serverConnected
                .observeOn(Schedulers.io())
                .subscribe(connected -> {
                    if(connected){
                        new ChatClient();
                    }
                });
    }
}
