package com.dt176g.project;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.Socket;

/**
 * GUI-based chat client using Swing.
 * Connects to a local ChatServer and communicates with an AI chatbot.
 *
 * @author Roka1901
 */
public class ChatClient extends JFrame {

    private final JTextArea chatArea;
    private final JTextField inputField;
    private PrintWriter out;
    private final String prompt = "Your name is Dennis. Answer in one short sentence and ask if you can help.";

    /**
     * Sets up the chat window GUI and connects to the server.
     */
    public ChatClient() {
        setTitle("Dennis the AI Client");
        setSize(600, 800);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setFocusable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        inputField = new JTextField();

        JScrollPane scrollPane = new JScrollPane(chatArea);
        scrollPane.createVerticalScrollBar();
        add(scrollPane, BorderLayout.CENTER);
        add(inputField, BorderLayout.SOUTH);

        inputField.addActionListener(e -> sendMessage());

        setVisible(true);
        connectToServer();
    }

    /**
     * Establishes a TCP connection to the chat server and listens for messages.
     * Sends initial AI prompt as the first message.
     */
    private void connectToServer() {
        try {
            Socket socket = new Socket("localhost", 12345);
            out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            out.println(prompt);

            Observable.<String>create(emitter -> {
                        String line;
                        while ((line = in.readLine()) != null || !in.readLine().trim().isEmpty()) {
                            emitter.onNext(line);
                        }
                    })
                    .subscribeOn(Schedulers.io())
                    .subscribe(this::appendChatbotReply, Throwable::printStackTrace);

        } catch (IOException e) {
            appendChatbotReply("[Error] Could not connect to server.");
        }
    }

    /**
     * Sends user input to the server when Enter is pressed.
     */
    private void sendMessage() {
        String message = inputField.getText().trim();
        if (!message.trim().isEmpty()) {
            inputField.setText("");

            Observable.just(message)
                    .subscribeOn(Schedulers.io())
                    .subscribe(msg -> {
                        appendMessage("\n" + "You: " + msg + "\n");
                        out.println(msg);
                    });
        }
    }

    /**
     * Appends the user's message to the chat area and disable input.
     */
    private void appendMessage(String message) {
        if(!message.isEmpty()) {
            chatArea.append(message + "\n");
            chatArea.setCaretPosition(chatArea.getDocument().getLength());
            inputField.setEnabled(false);
        }
    }

    /**
     * Appends chatbot's reply to the chat area and enables input again.
     * <p>This method combines the chatbot's name ("Dennis: ") with the actual response message
     * using RxJava's merge() operator to demonstrate stream composition.
     */
    private void appendChatbotReply(String message) {
        if(!message.trim().isEmpty()) {
            Observable<String> name = Observable.just("Dennis: ");
            Observable<String> content = Observable.just(message + "\n\n");

            Observable
                    .merge(name, content)
                    .observeOn(Schedulers.single())
                    .subscribe(chatArea::append);
        }
        chatArea.setCaretPosition(chatArea.getDocument().getLength());
        inputField.setEnabled(true);
    }
}
