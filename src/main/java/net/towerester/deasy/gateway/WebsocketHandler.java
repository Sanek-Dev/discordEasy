package net.towerester.deasy.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.towerester.deasy.Constants;
import net.towerester.deasy.gateway.entities.Activity;
import net.towerester.deasy.gateway.entities.DiscordIntent;
import net.towerester.deasy.gateway.entities.DiscordStatus;
import net.towerester.deasy.gateway.events.EventListener;
import net.towerester.deasy.gateway.events.HelloEvent;
import net.towerester.deasy.gateway.events.ReadyEvent;
import org.java_websocket.WebSocket;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.framing.Framedata;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.net.URI;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class WebsocketHandler extends WebSocketClient {
    private final DiscordClient client;
    private volatile long currentTime;
    private static final Logger logger = LoggerFactory.getLogger(WebsocketHandler.class);

    public WebsocketHandler(URI serverUri, DiscordClient client) {
        super(serverUri);
        this.client = client;
    }

    private void identify() {
        ObjectNode node = Constants.MAPPER.createObjectNode();
        node.put("op", 2);

        ObjectNode identify = Constants.MAPPER.createObjectNode();
        identify.put("token", client.getToken());

        ObjectNode properties = Constants.MAPPER.createObjectNode();
        properties.put("os", System.getProperty("os.name"));
        properties.put("browser", "Discordium");
        properties.put("device", "Discordium");

        identify.put("properties", properties);
        identify.put("compress", client.isCompress());

        ArrayNode arr = Constants.MAPPER.createArrayNode();
        arr.add(0);
        if(!client.isShardingEnabled()) {
            arr.add(1);
        } else {
            if(client.getShardCount() == 0) {
                arr.add(client.getRecommendedShardCount());
            } else {
                arr.add(client.getShardCount());
            }
        }

        identify.put("shard", arr);

        int code = 0;
        for(DiscordIntent intent: client.getIntents()) {
            code += intent.getCode();
        }

        identify.put("intents", code);

        ObjectNode presence = Constants.MAPPER.createObjectNode();
        ArrayNode a = Constants.MAPPER.createArrayNode();
        for(Activity el: client.getActivities()) {
            a.add(el.toJson());
        }
        presence.put("activities", a);
        presence.put("status", (client.getStatus() == DiscordStatus.ONLINE ? "online" : (client.getStatus() == DiscordStatus.OFFLINE ? "offline" : (client.getStatus() == DiscordStatus.INVISIBLE ? "invisible" : (client.getStatus() == DiscordStatus.IDLE ? "idle" : "dnd")))));
        presence.putNull("since");
        presence.put("afk", false);

        identify.put("presence", presence);
        node.put("d", identify);

        String res = "";
        try {
            res = Constants.MAPPER.writeValueAsString(node);
        } catch(Exception e) {
           logger.error("Can't write json!", e);
        }

        client.getSocket().send(res);

        if(client.isDebug()) {
            logger.debug("Sent identify packet!\nCalculated intents: {}", code);
        }
    }

    @Override
    public void onOpen(ServerHandshake handshakeData) {
        if(client.isDebug()) {
            logger.debug("Successfully opened websocket connection!");
        }

        if(client.isReconnecting()) {
            ObjectNode node = Constants.MAPPER.createObjectNode();
            node.put("op", 6);

            ObjectNode body = Constants.MAPPER.createObjectNode();
            body.put("token", client.getToken());
            body.put("session_id", client.getSessionId());
            body.put("seq", client.getLastSequence());

            node.put("d", body);
            String res = "";
            try {
                res = Constants.MAPPER.writeValueAsString(node);
            } catch(Exception e) {
                logger.error("Can't write json!", e);
            }

            client.getSocket().send(res);
        }
    }

    @Override
    public void onMessage(String message) {
        currentTime = System.currentTimeMillis();
        client.getSocket().sendPing();

        if(client.isDebug()) {
            logger.debug("Received websocket message: \n\t{}", message);
        }

        JsonNode json = null;
        try {
            json = Constants.MAPPER.readTree(message);
        } catch(Exception e) {
            logger.error("Can't read json!", e);
        }

        if(json == null) {
            logger.warn("Event json is null");
            return;
        }

        int op = json.get("op").asInt();
        String eventName = "";
        if(!json.get("t").isNull()) {
            eventName = json.get("t").asText();
        }

        switch (op) {
            case 0 -> {
                if(!json.get("s").isNull()) {
                    try {
                        Field f = client.getClass().getDeclaredField("lastSeq");
                        f.setAccessible(true);
                        f.set(client, json.get("s").asInt());
                    } catch(Exception e) {
                        logger.error("Can't access lastSeq variable!", e);
                    }
                }
            }
            case 10 -> {
                // Hello event
                int heartbeatInterval = json.get("d").get("heartbeat_interval").asInt();

                try {
                    Field f = client.getClass().getDeclaredField("heartbeatExecutor");
                    f.setAccessible(true);
                    f.set(client, Executors.newScheduledThreadPool(1));

                    ((ScheduledExecutorService) f.get(client)).scheduleAtFixedRate(() -> {
                        ObjectNode node = Constants.MAPPER.createObjectNode();
                        node.put("op", 1);

                        if(client.getLastSequence() == 0) {
                            node.putNull("d");
                        } else {
                            node.put("d", client.getLastSequence());
                        }

                        String res = "";
                        try {
                            res = Constants.MAPPER.writeValueAsString(node);
                        } catch(Exception e) {
                            logger.error("Can't write json!", e);
                        }

                        client.getSocket().send(res);

                        if(client.isDebug()) {
                            logger.debug("Sent heartbeat");
                        }
                    }, 0, (heartbeatInterval - 2000), TimeUnit.MILLISECONDS);
                } catch(Exception e) {
                    logger.error("Can't access heartbeat executor variable!", e);
                }

                for(EventListener listener: client.getEventListeners()) {
                    listener.onHello(new HelloEvent(client, heartbeatInterval));
                }

                if(!client.isReconnecting()) {
                    identify();
                }
            }
            case 7 -> {
                // Reconnect event

                if(client.isDebug()) {
                    logger.debug("Received reconnect event!Reconnecting...");
                }

                reconnect();
            }
            case 9 -> {
                // Invalid session event
                boolean canReconnect = json.get("d").asBoolean();

                logger.warn("Received invalid session event!");
                if(canReconnect) {
                    logger.debug("Trying to reconnect...");
                    client.reconnect(false);
                } else {
                    if(client.isReconnecting()) {
                        logger.debug("Reconnect was unsuccessfully");
                        logger.debug("Restarting client...");
                        client.reconnect(true);
                    } else {
                        logger.error("Stopping currently active client...");
                        client.stop();
                    }
                }
            }
        }

        JsonNode body = json.get("d");
        switch (eventName) {
            case "READY" -> {
                int apiVersion = body.get("v").asInt();
                String sessionId = body.get("session_id").asText();
                String resumeUrl = body.get("resume_gateway_url").asText();

                try {
                    Field sessionIdF = client.getClass().getDeclaredField("sessionId");
                    sessionIdF.setAccessible(true);
                    sessionIdF.set(client, sessionId);

                    Field resumeUrlF = client.getClass().getDeclaredField("resumeUrl");
                    resumeUrlF.setAccessible(true);
                    resumeUrlF.set(client, resumeUrl);
                } catch(Exception e) {
                    logger.error("Can't access sessionId and resumeUrl variables!", e);
                }

                for(EventListener listener: client.getEventListeners()) {
                    listener.onReady(new ReadyEvent(client, apiVersion, sessionId, resumeUrl));
                }
            }
            case "RESUMED" -> {
                if(client.isReconnecting()) {
                    if(client.isDebug()) {
                        logger.debug("Successfully reconnected!");
                    }

                    try {
                        Field f = client.getClass().getDeclaredField("reconnecting");
                        f.setAccessible(true);
                        f.set(client, false);
                    } catch(Exception e) {
                        logger.error("Can't access reconnecting variable!", e);
                    }
                }
            }
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        if(!reason.equals("Close")) {
            logger.error("Closed websocket connection!\nErr code: {}\nReason: {}", code, reason);
            client.stop();
        }
    }

    @Override
    public void onError(Exception ex) {

    }

    @Override
    public void onWebsocketPong(WebSocket conn, Framedata f) {
        long curr = System.currentTimeMillis();

        try {
            Field pingF = client.getClass().getDeclaredField("ping");
            pingF.setAccessible(true);
            ((AtomicInteger) pingF.get(client)).set((int) (curr - currentTime));
        } catch(Exception e) {
            logger.error("Can't access ping variable!", e);
        }
    }
}
