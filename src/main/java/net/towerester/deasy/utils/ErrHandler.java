package net.towerester.deasy.utils;

import com.fasterxml.jackson.databind.JsonNode;
import net.towerester.deasy.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpResponse;
import java.util.concurrent.TimeUnit;

public class ErrHandler {
    private static final Logger logger = LoggerFactory.getLogger(ErrHandler.class);

    /**
     * Check json and rate limits for errors. If found, an error is thrown or the exhausted status is set for the http client
     * @param client Http client
     * @param json Json for error checking
     * @param request Sent request
     * @param response Response from request execution**/
    public static void handle(SimpleHttpClient client, JsonNode json, HttpResponse<String> response, SimpleHttpClient.Request request) {
        if(response.headers().map().containsKey("X-RateLimit-Limit")) {
            int remaining = Integer.parseInt(response.headers().map().get("X-RateLimit-Remaining").get(0));
            double resetAfter = Double.parseDouble(response.headers().map().get("X-RateLimit-Reset-After").get(0));
            String bucket = response.headers().map().get("X-RateLimit-Bucket").get(0);

            if(remaining <= 0) {
                logger.warn("Rate limit bucket {} is exhausted!Reset after: {}s", bucket, resetAfter);
                client.setExhausted(true, (int) Math.floor(resetAfter));
                return;
            }
        }

        if(response.statusCode() == 429) {
            if(json.has("retry_after")) {
                String msg = json.get("message").asText();
                double retryAfter = json.get("retry_after").asDouble();
                boolean global = json.get("global").asBoolean();

                StringBuilder res = new StringBuilder();
                res.append("Exceeded a rate limit!").append("\n");
                res.append("Message: ").append(msg).append("\n");
                res.append("Is global: ").append(global).append("\n");
                res.append("The request will be completed in: ").append(retryAfter).append("s").append("\n");
                logger.warn(res.toString());

                new Thread(() -> {
                    try {
                        TimeUnit.SECONDS.sleep((long) retryAfter);
                    } catch(Exception e) {
                        logger.error("", e);
                    }

                    HttpResponse<String> r = client.executeAndReturn(request);

                    try {
                        ErrHandler.handle(client, Constants.MAPPER.readTree(r.body()), r, request);
                    } catch(Exception e) {
                        logger.error("", e);
                    }
                }).start();
            }
        }

        if(json.has("code") && json.has("message")) {
            int code = json.get("code").asInt();
            String msg = json.get("message").asText();
            String content = "";

            try {
                content = Constants.MAPPER.writeValueAsString(json);
            } catch(Exception e) {
                logger.error("", e);
            }

            logger.error("Discord API error!\nCode: {}\nMessage: {}\nContent: {}", code, msg, content);
        }
    }
}
