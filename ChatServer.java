package com.dt176g.project;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.BehaviorSubject;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.TimeUnit;

/**
 * Server that listens for incoming chat clients and routes messages to the chatbot.
 *
 * @author Roka1901
 */
public class ChatServer {

    private static final int PORT = 12345;
    private static final ChatbotService chatbotService = new ChatbotService();

    public static final BehaviorSubject<Boolean> serverConnected = BehaviorSubject.createDefault(false);

    /**
     * Initializes the server, opens port, and listens for clients.
     * Starts a new reactive stream per client.
     */
    public ChatServer() throws IOException {
        ServerSocket serverSocket = new ServerSocket(PORT);
        System.out.println("ChatServer is running on port " + PORT);
        serverConnected.onNext(true);
        Observable.<Socket>create(emitter -> {
                    while (!serverSocket.isClosed()) {
                        Socket client = serverSocket.accept();
                        System.out.println("Client connected: " + client.getRemoteSocketAddress());
                        emitter.onNext(client);
                    }
                })
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io())
                .subscribe(ChatServer::handleClient);

        try {
            Thread.currentThread().join();

        } catch (InterruptedException e) {
            System.out.println("Server shutting down.");
        }
    }

    /**
     * Handles an individual client connection.
     * For each message, sends it to the AI and returns the reply.
     *
     * @param clientSocket The socket for the connected client.
     */
    private static void handleClient(Socket clientSocket) {
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);

            Flowable<String> messageFlow = Flowable.create(emitter -> {
                String line;
                while ((line = in.readLine()) != null) {
                    emitter.onNext(line);
                }
                emitter.onComplete();
            }, BackpressureStrategy.BUFFER);

            messageFlow
                    .delay(1, TimeUnit.SECONDS)
                    .doOnNext(message -> {
                                String response = chatbotService.askChatbot(message);
                                out.println(response);
                            }
                    )
                    .subscribeOn(Schedulers.io())
                    .observeOn(Schedulers.io())
                    .doOnError(error -> System.err.println("Error: " + error.getMessage()))
                    .subscribe();

        } catch (IOException e) {
            System.err.println("Client handling error: " + e.getMessage());
        }
    }
}
