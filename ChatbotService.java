package com.dt176g.project;

import okhttp3.*;
import com.google.gson.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Service class that handles communication with the DeepInfra AI API.
 *
 * @author Roka1901
 */
public class ChatbotService {

    private static final String API_KEY;
    private static final String MODEL;
    private static final String ENDPOINT = "https://api.deepinfra.com/v1/openai/chat/completions";

    private static final OkHttpClient client = new OkHttpClient();
    private static final Gson gson = new Gson();

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

    /**
     * Sends a user message to the AI chatbot and returns the response as a string.
     *
     * @param userMessage The message from the user.
     * @return Chatbot's response text, or error message if failed.
     */
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
    }
}