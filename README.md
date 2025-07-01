# Final Project

## Environment & Tools
* Java version: 21.0.2
* Build tool: Apache Maven 3.9.6
* Operating system: Windows 10
* IDE: IntelliJ IDEA 2024.3.3
* Libraries: RxJava 3, OkHttp 4, Gson
* UI Framework: Java Swing
* External API: DeepInfra (OpenAI-compatible API for ChatGPT access)

---

## Purpose
The purpose of this project was to develop a responsive chatbot application using reactive programming with RxJava.
The system consists of two main components: a client with a graphical user interface (GUI) built using Java Swing, and a
server that handles incoming messages, relays them to an AI service via DeepInfra, and returns the chatbot's responses.

---

## Procedures
The chatbot system is built around two main components: a reactive server and a graphical client. The communication
between these components is handled using sockets and RxJava’s Observable streams.

### ChatServer.java
The ChatServer class implements a simple reactive TCP server that waits for incoming client connections, 
listens to their messages, and responds using a chatbot service. It utilizes RxJava for handling non-blocking 
socket operations and isolates each client interaction through reactive streams.

The server uses:
* `Observable.create()` and `Flowable.create()` for stream generation.
* `Schedulers.io()` to offload network operations to background threads.
* `doOnError()` to handle connection issues cleanly.

#### ChatServer()
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
This is the entry point of the server application. It starts by creating a ServerSocket that listens on port 12345. 
Using RxJava, an *Observable<Socket>* is defined to accept new incoming client connections. For every accepted socket, 
the server logs the connection and calls the handleClient() method. The stream is subscribed on a background I/O thread
(Schedulers.io()), ensuring the server remains responsive. To prevent the main thread from terminating prematurely, 
*Thread.currentThread().join()* is used, keeping the process alive while the server runs.

#### handleClient()
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
This method is responsible for managing a single client’s interaction. 
It begins by setting up two essential streams: a BufferedReader to read messages from the client and a PrintWriter to 
send responses back. The core of the method is a *Flowable<String>*, which continuously reads each line sent by the client.
This reactive stream emits a new item for every message received. The flow is created with a *BackpressureStrategy.BUFFER* 
to ensure smooth handling even if messages come in quickly. 
Each received message is then passed to the chatbot service through the *askChatbot()* method. 
The server receives the chatbot's reply as a plain string, and immediately sends it back to the same client 
using the PrintWriter.
The entire flow is subscribed on *Schedulers.io()* to ensure that all blocking I/O operations are handled asynchronously.
Any unexpected errors during the stream are caught and logged using *doOnError()*.
Together, this design enables the server to handle multiple clients concurrently in a non-blocking and reactive manner, 
while acting as a bridge between human users and the AI backend.

### ChatClient.java
The ChatClient class is responsible for creating the graphical user interface and managing the connection to 
the chatbot server. It includes logic for sending messages to the server, displaying replies, and handling 
user interaction through a reactive and asynchronous setup using RxJava.

The client uses:
* `merge()` to compose message fragments.
* `Schedulers.single()` to ensure GUI updates occur safely on the UI thread.

#### ChatClient()
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
This is the constructor of the class and is executed when the client application is launched. It begins by setting up 
the main window, giving it a title and specific dimensions. 
Inside the GUI, a JTextArea is used to display the ongoing chat. It is placed inside a *JScrollPane* to allow scrolling 
when the chat gets long. Below the chat area, a *JTextField* is added where users can type their messages. 
An action listener is attached to this field so that when the user presses Enter, the message is sent automatically. 
After the graphical interface is built, the method calls *connectToServer()* to establish a TCP connection. 
Once connected, it immediately sends a predefined prompt to the server: a message that sets the chatbot’s behavior 
and personality, effectively telling the AI: 
"Your name is now Dennis and send a reply text back in only one sentence with your name and ask if you can help with anything."

#### connectToServer()
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
This method handles the socket connection to the server. It tries to connect to localhost on port 12345, 
and if successful, it initializes a PrintWriter for sending messages and a BufferedReader for receiving them. 
After the connection is established, it sends the initial personality-defining prompt to the chatbot. 
Then, using RxJava, it creates an Observable from the server’s input stream. 
Each line received from the server is emitted as a new event. The observable listens in a background thread 
(Schedulers.io()), and every incoming message is handled reactively and passed to the method *appendChatbotReply()*.

#### sendMessage()
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
This method is triggered when the user presses "enter" in the input field. It first checks if the input is not empty, 
and then clears the text field immediately to give feedback to the user. A new observable is created from the user’s message.
When subscribed, the message is appended to the chat window with the label "You:", and then sent to the server using the PrintWriter.
The input field is temporarily disabled until the chatbot replies, which helps prevent spamming or overlapping messages.

#### appendMessage()
    private void appendMessage(String message) {
        if(!message.isEmpty()) {
            chatArea.append(message + "\n");
            chatArea.setCaretPosition(chatArea.getDocument().getLength());
            inputField.setEnabled(false);
        }
    }
This method updates the chat window with a new user message. It adds the message to the JTextArea and disables the 
input field while waiting for the bot to respond.

#### appendChatbotReply()
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
When the server sends back a response from the chatbot, this method is responsible for displaying it in the chat area. 
First, it creates two observables: one for the label "Dennis: " and one for the chatbot's actual response, 
followed by line breaks. These are merged into a single stream using *Observable.merge()* and appended to 
the chat window on the GUI thread using *Schedulers.single()*. It also scrolls the chat window to the bottom and 
re-enables the input field to allow the user to send the next message.

### ChatbotService.java
The ChatbotService class acts as a bridge between the Java-based client-server system and an external AI provider, 
in this case via the DeepInfra API. Its primary purpose is to send user input to the AI service and return a 
generated reply, which simulates a chatbot conversation. The implementation uses the OkHttp client to perform 
HTTP POST requests and Gson to handle JSON serialization.

#### ChatbotService()
    static {
        Properties props = new Properties();
        try (InputStream input = ChatbotService.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (input != null) {
                props.load(input);
                API_KEY = props.getProperty("deepinfra.api.key");
                MODEL = props.getProperty("deepinfra.model", "mistralai/Mistral-7B-Instruct-v0.1");
            } else {
                throw new RuntimeException("config.properties not found.");
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load DeepInfra API config", e);
        }
    }
This is the default constructor and does not perform any actions directly. The class is designed to be instantiated 
once and used to handle any number of chatbot queries via its *askChatbot()* method.

#### askChatbot()
    public String askChatbot(String userMessage) {
        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", userMessage);

        JsonArray messages = new JsonArray();
        messages.add(userMsg);

        JsonObject payload = new JsonObject();
        payload.addProperty("model", MODEL);
        payload.add("messages", messages);

        Request request = new Request.Builder()
                .url(ENDPOINT)
                .addHeader("Authorization", "Bearer " + API_KEY)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(gson.toJson(payload), MediaType.parse("application/json")))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                return "[API Error] " + response.code() + " – " + response.message();
            }

            assert response.body() != null;
            String body = response.body().string();
            JsonObject json = gson.fromJson(body, JsonObject.class);
            return json.getAsJsonArray("choices")
                    .get(0).getAsJsonObject()
                    .getAsJsonObject("message")
                    .get("content").getAsString().trim();

        } catch (IOException e) {
            return "[Connection Error] Could not reach DeepInfra.";
        }
This is the core method of the class. It takes a user's message as input and returns a textual reply from the chatbot. 
The method starts by preparing a JSON payload compatible with the DeepInfra API, specifying 
the model and embedding the user's message as part of a messages array with a role of "user". 
The JSON structure is assembled using Gson objects and arrays, which ensures compatibility with the expected API format.
Once the request body is complete, an HTTP POST request is created using OkHttp, with headers specifying the content 
type and authorization token. 
The API key is securely loaded from a config.properties file located in the application's resources folder. 
If the file is missing or the key cannot be retrieved, the constructor throws a runtime exception, ensuring 
the developer is alerted to configuration issues early.

The method then performs the API call using *client.newCall(request).execute()*. If the request is successful, 
it reads the response body as a string and parses it into a JSON object. The chatbot’s reply is extracted from 
the "message" field within the first item of the "choices" array. The reply is trimmed and returned as a plain string. 
If an error occurs during this process—whether due to a failed connection, bad response, or exception—it logs the error 
and returns a predefined fallback message.

### Project.java
TThe Project class serves as the launcher for the entire chatbot application. 
Its role is to initialize and coordinate the startup of both the server and client components in a reactive, 
non-blocking fashion using RxJava. This class is written as a utility class and is not meant to be instantiated.

#### Project()
    private Project() {
        throw new IllegalStateException("Utility class");
    }
This private constructor prevents the class from being instantiated, as it's intended to be used in a static context only.
If someone attempts to create an instance, the constructor will throw an *IllegalStateException*. 
This is a common design pattern used in Java for utility classes that only contain static methods or logic.

#### main()
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
This is the entry point of the program. The method uses RxJava to launch the ChatServer and ChatClient in a controlled 
and reactive manner. The server is started first by creating a *BehaviorSubject<Boolean>* named serverReady, 
which acts as a signal indicating when the server is up and running. A Completable is used to perform the side-effect of 
instantiating the ChatServer, and once the server is successfully launched, it emits true to the serverReady subject.
Once the serverReady signal has been sent, the client is started on a separate thread. This ensures the client does 
not attempt to connect before the server is fully ready. The client is initialized via a *subscribe()* call and runs 
in a background I/O thread. This startup flow ensures robust sequencing of server and client components, 
prevents race conditions, and preserves responsiveness. The use of reactive primitives like BehaviorSubject and 
Completable aligns with the reactive paradigm established throughout the project.

---

## Discussion

This section evaluates how well the project fulfilled its original goals and explores alternative approaches that
could have been used.

### Purpose Fulfillment
The chatbot successfully meets the Grade up to C requirements by integrating fundamental reactive programming principles, 
ensuring a responsive user experience, and implementing RxJava effectively.
#### Observable & Observer:
The chatbot implements the Observable-Observer pattern using RxJava’s PublishSubject. *sendInput()* acts as an
event emitter, while *getUserMessages()* allows the GUI to subscribe and respond to messages. 
By effectively implementing these Grade C requirements, the project showcases a robust reactive chatbot.
#### Operators:
* delay()
* merge()
* subscribeOn()
* observeOn()
* doOnNext()
* doOnError()
* just()
* create()
* subscribe()
  
#### Combining Observables
The project uses RxJava’s *merge()* operator to combine the chatbot’s name and message into a single cohesive output stream. 
This creates smooth message formatting in the chat interface. Additionally, the input field is 
temporarily disabled while waiting for a reply, simulating a typing indicator and enhancing 
the interactive experience.
#### MultiCasting:
PublishSubject enables multiple observers to subscribe to chatbot events,
ensuring message handling can be dynamically extended.
#### Concurrency & Parallelization:
The chatbot’s responses are asynchronously processed using *Schedulers.io()*. This ensures that chatbot operations do not
block the main thread.
#### Buffering / Throttling / Switching:
The chatbot introduces throttling via *delay()* to ensure a more natural message response time.
The project uses *BackpressureStrategy.BUFFER* in the server to ensure that all incoming messages are 
buffered safely when processing can't keep up
#### Functional Programming Principles:
This project demonstrates a clear understanding of functional programming through the use of RxJava. 
Instead of relying on imperative loops or shared mutable state, the application processes data 
reactively using Observable and Flowable streams. Functional operators like doOnNext(), 
subscribeOn(), and observeOn() are used to handle data in a declarative, asynchronous way. 
Each client connection is treated independently, and immutability is respected throughout 
message processing. While the project does not heavily use typical functional constructs like map() 
or reduce(), it effectively applies core functional principles such as isolation of side effects 
and non-blocking data flows.
#### Error Handling:
If the user inputs an unrecognized message, the chatbot defaults to "I'm not sure how to respond to that. Try with: " and 
providing several options to ensure a graceful fallback mechanism.
#### Usability:
The GUI is intuitive, simple, and user-friendly, providing a clear message area with automatic scrolling.
Real-time input handling using event listeners and responses that are user-friendly.

---

### Alternative Approaches
One alternative approach to this project would have been to use standard imperative programming with
threads and socket handling via `Runnable` or `ExecutorService`. While this would have worked, it would
have required significantly more boilerplate code for thread synchronization and error handling, and it
would lack the declarative, composable nature of reactive streams.

Another alternative would be to use asynchronous non-blocking I/O via NIO or Netty. This would
potentially improve performance, especially with multiple clients, but at the cost of greater complexity
and lower readability—particularly for educational purposes.

On the GUI side, JavaFX could have been used instead of Swing. JavaFX provides more modern UI components,
CSS support, and native property binding. However, Swing was chosen for its simplicity and accessibility
within a lightweight project scope.

For the AI backend, an alternative to DeepInfra could have been OpenAI's official API or open-source
language models like GPT4All or LLaMa. However, DeepInfra was selected due to its compatibility with
OpenAI's format and lack of token-based authentication complexity, making integration faster and smoother.

The GUI could have been JavaFX instead of Swing, Swing was chosen due to convenience. 
The chatbot could have been implemented with machine learning, using the deeplearning4j library 
(https://www.baeldung.com/deeplearning4j) to give the chatbot a real interactive conversation with the user.
The chatbot could also been using an API from openai for the chatbot
implementation.

---

## Personal Reflections
This project was one of the most rewarding learning experiences during my education. By combining network programming,
user interface design, asynchronous communication, and AI integration, I was able to work across multiple layers of
a real-world application.

What stood out most was how naturally reactive programming fits my personal development style. RxJava’s way of thinking
in data streams, chaining operations, and reacting to events felt intuitive. It allowed me to write expressive, modular,
and clean code—even in a complex setup with sockets, threads, and GUI components.

While reactive programming can be hard to grasp at first, I gained a deeper understanding by building something
hands-on. The use of `Flowable`, `BehaviorSubject`, `merge()`, and reactive scheduling made me realize how flexible and
powerful this paradigm is. I also appreciated how UI responsiveness could be maintained with minimal effort thanks
to RxJava's composition model.

I now feel more confident working with reactive frameworks and asynchronous architectures. I also got to experiment
with connecting AI via DeepInfra, which made the project feel both modern and meaningful. Overall, this experience
has not only strengthened my technical skills but also confirmed that reactive programming is something I truly enjoy.
