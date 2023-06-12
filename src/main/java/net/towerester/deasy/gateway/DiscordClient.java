package net.towerester.deasy.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.towerester.deasy.Constants;
import net.towerester.deasy.gateway.entities.Activity;
import net.towerester.deasy.gateway.entities.DiscordIntent;
import net.towerester.deasy.gateway.entities.DiscordStatus;
import net.towerester.deasy.gateway.entities.Presence;
import net.towerester.deasy.gateway.events.EventListener;
import net.towerester.deasy.utils.ErrHandler;
import net.towerester.deasy.utils.SimpleHttpClient;
import org.java_websocket.client.WebSocketClient;

import java.net.URI;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class DiscordClient {
    private WebSocketClient socket;
    private final int recommendedShards;
    private final String gatewayUrl;
    private final List<DiscordIntent> intents;
    private final String token;
    private volatile int lastSeq;
    private volatile String sessionId;
    private volatile String resumeUrl;
    private final boolean shardingEnabled;
    private final int shardCount;
    private final boolean compress;
    private SimpleHttpClient httpClient;
    private final boolean debug;
    private final List<EventListener> listeners;
    private Thread keepAliveThread;
    private volatile ScheduledExecutorService heartbeatExecutor;
    private final List<Activity> activities;
    private final DiscordStatus status;
    private final AtomicInteger ping;
    private volatile boolean reconnecting;

    public static class Builder {
        private String token;
        private List<DiscordIntent> intents;
        private int shardCount;
        private boolean shardingEnabled;
        private boolean compress;
        private boolean allowInsecureConnections;
        private SimpleHttpClient httpClient;
        private boolean debug;
        private final List<EventListener> listeners;
        private List<Activity> activities;
        private DiscordStatus status;

        /**
         * @param token Discord bot token**/
        public Builder(String token) {
            this.token = token;
            this.intents = new ArrayList<>();
            this.shardCount = 0;
            this.shardingEnabled = false;
            this.compress = false;
            this.allowInsecureConnections = false;
            this.httpClient = null;
            this.debug = false;
            this.listeners = new ArrayList<>();
            this.status = DiscordStatus.ONLINE;
            this.activities = new ArrayList<>();
        }

        /**
         * @param token Discord bot token**/
        public Builder setToken(String token) {
            this.token = token;
            return this;
        }

        /**
         * @param intents List of discord intents**/
        public Builder setIntents(List<DiscordIntent> intents) {
            this.intents = intents;
            return this;
        }

        /**
         * @param intents Array of discord intents**/
        public Builder setIntents(DiscordIntent... intents) {
            this.intents = Arrays.asList(intents);
            return this;
        }

        /**
         * Add intents to the current intents list**/
        public Builder addIntents(List<DiscordIntent> intents) {
            this.intents.addAll(intents);
            return this;
        }

        /**
         * Add array of intents to the current intents list**/
        public Builder addIntents(DiscordIntent... intents) {
            this.intents.addAll(Arrays.asList(intents));
            return this;
        }

        /**
         * Add intent to the current intents list**/
        public Builder addIntent(DiscordIntent intent) {
            this.intents.add(intent);
            return this;
        }

        /**
         * @param shardingEnabled If false, totally shard count will be 1**/
        public Builder setShardingEnabled(boolean shardingEnabled) {
            this.shardingEnabled = shardingEnabled;
            return this;
        }

        /**
         * @param shardCount If value is 0, use recommended shard count(default)**/
        public Builder setShardCount(int shardCount) {
            this.shardCount = shardCount;
            return this;
        }

        /**
         * Use or not zlib-stream compression**/
        public Builder setCompress(boolean compress) {
            this.compress = compress;
            return this;
        }

        /**
         * @param allowInsecureConnections If true, certificate verification will be disabled**/
        public Builder setAllowInsecureConnections(boolean allowInsecureConnections) {
            this.allowInsecureConnections = allowInsecureConnections;
            return this;
        }

        /**
         * Set http client for client**/
        public Builder setHttpClient(SimpleHttpClient httpClient) {
            this.httpClient = httpClient;
            return this;
        }

        /**
         * Whether to print debug information to the console**/
        public Builder setDebug(boolean debug) {
            this.debug = debug;
            return this;
        }

        public Builder addEventListener(EventListener listener) {
            this.listeners.add(listener);
            return this;
        }

        public Builder addEventListeners(EventListener... listeners) {
            this.listeners.addAll(Arrays.asList(listeners));
            return this;
        }

        /**
         * Add activity to the start activities list**/
        public Builder addActivity(Activity activity) {
            this.activities.add(activity);
            return this;
        }

        /**
         * Set start online status for a bot**/
        public Builder setStatus(DiscordStatus status) {
            this.status = status;
            return this;
        }

        /**
         * @return Built DiscordClient**/
        public DiscordClient build() {
            if(httpClient == null) {
                httpClient = new SimpleHttpClient.Builder()
                        .setAllowInsecureConnections(allowInsecureConnections)
                        .addDefaultHeader("Authorization", "Bot " + token)
                        .addDefaultHeader("User-Agent", Constants.USER_AGENT)
                        .setBaseUrl(Constants.BASE_URL)
                        .build();
            }

            return new DiscordClient(token, intents, shardingEnabled, shardCount, compress, httpClient, debug, listeners, activities, status);
        }
    }

    /**
     * @param shardingEnabled Is sharding enabled
     * @param intents Discord bot intents
     * @param token Discord bot token
     * @param shardCount When sharding is enabled, the number of shards
     * @param status Bot online status
     * @param activities Bot activities
     * @param compress Use or not zlib-stream compression
     * @param debug Whether to print debug information to the console
     * @param httpClient Base HttpClient
     * @param listeners Event listeners list**/
    public DiscordClient(String token, List<DiscordIntent> intents, boolean shardingEnabled, int shardCount, boolean compress, SimpleHttpClient httpClient, boolean debug, List<EventListener> listeners, List<Activity> activities, DiscordStatus status) {
        this.token = token;
        this.intents = intents;
        this.shardCount = shardCount;
        this.shardingEnabled = shardingEnabled;
        this.compress = compress;
        this.httpClient = httpClient;
        this.debug = debug;
        this.listeners = listeners;
        this.lastSeq = 0;
        this.activities = activities;
        this.status = status;
        this.ping = new AtomicInteger(0);
        this.reconnecting = false;

        SimpleHttpClient.Request gatewayReq = new SimpleHttpClient.Request.Builder("/gateway/bot", "GET").build();
        HttpResponse<String> gatewayRes = httpClient.executeAndReturn(gatewayReq);
        JsonNode gatewayJson = null;

        try {
            gatewayJson = Constants.MAPPER.readTree(gatewayRes.body());
        } catch(Exception e) {
            e.printStackTrace();
        }
        ErrHandler.handle(httpClient, gatewayJson, gatewayRes, gatewayReq);

        this.gatewayUrl = gatewayJson.get("url").asText();
        this.recommendedShards = gatewayJson.get("shards").asInt();
    }

    /**
     * Connect to the Discord Gateway and start running the bot**/
    public final void start() {
        this.socket = new WebsocketHandler(URI.create(gatewayUrl + "?v=10&encoding=json" + (compress ? "&compress=zlib-stream" : "")), this);

        this.keepAliveThread = new Thread(() -> {
            while(true) {
                if(Thread.currentThread().isInterrupted()) {
                    break;
                }
            }
        });
        keepAliveThread.start();
        socket.connect();
    }

    /**
     * Update bot presence**/
    public final void updatePresence(Presence presence) {
        ObjectNode node = Constants.MAPPER.createObjectNode();
        node.put("op", 3);
        node.put("d", presence.toJson());
        String res = "";
        try {
            res = Constants.MAPPER.writeValueAsString(node);
        } catch(Exception e) {
            e.printStackTrace();
        }

        socket.send(res);
    }

    /**
     * @return Unmodifiable list of event listeners**/
    public final List<EventListener> getEventListeners() {
        return Collections.unmodifiableList(listeners);
    }

    public final DiscordStatus getStatus() {
        return status;
    }

    /**
     * @return Unmodifiable list of activities**/
    public final List<Activity> getActivities() {
        return Collections.unmodifiableList(activities);
    }

    /**
     * @return Unmodifiable list of intents**/
    public final List<DiscordIntent> getIntents() {
        return Collections.unmodifiableList(intents);
    }

    public final int getShardCount() {
        return shardCount;
    }

    public final int getRecommendedShardCount() {
        return recommendedShards;
    }

    public final boolean isShardingEnabled() {
        return shardingEnabled;
    }

    public final boolean isCompress() {
        return compress;
    }

    /**
     * @return Websocket client used to connect Discord Gateway**/
    public final WebSocketClient getSocket() {
        return socket;
    }

    public final int getLastSequence() {
        return lastSeq;
    }

    public final String getSessionId() {
        return sessionId;
    }

    public final String getToken() {
        return token;
    }

    public final boolean isReconnecting() {
        return reconnecting;
    }

    public final boolean isDebug() {
        return debug;
    }

    /**
     * @return Current delay between sending ping and receiving pong (in milliseconds)**/
    public final int getPing() {
        return ping.get();
    }

    /**
     * @param failed If true, client will be restarted otherwise reconnect to the Discord gateway by resume url and send Resume event**/
    public void reconnect(boolean failed) {
        if(!failed) {
            try {
                this.socket.close();
            } catch(Exception e) {
                e.printStackTrace();
            }

            reconnecting = true;
            this.socket = new WebsocketHandler(URI.create(resumeUrl), this);
            socket.connect();
        } else {
            this.stop();
            this.start();
        }
    }

    /**
     * Shutdown heartbeat thread executor, interrupt keep alive thread and close Discord gateway connection with exit code 1001**/
    public final void stop() {

        if(!socket.isClosed()) {
            socket.close(1001, "Close");
        }

        this.keepAliveThread.interrupt();
        heartbeatExecutor.shutdown();
    }
}
