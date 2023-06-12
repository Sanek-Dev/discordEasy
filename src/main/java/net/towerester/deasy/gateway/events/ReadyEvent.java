package net.towerester.deasy.gateway.events;

import net.towerester.deasy.gateway.DiscordClient;

public class ReadyEvent extends BaseEvent {
    private final int apiVersion;
    private final String sessionId;
    private final String resumeUrl;

    public ReadyEvent(DiscordClient client, int apiVersion, String sessionId, String resumeUrl) {
        super(client);
        this.apiVersion = apiVersion;
        this.sessionId = sessionId;
        this.resumeUrl = resumeUrl;
    }

    /**
     * @return Discord API version**/
    public int getApiVersion() {
        return apiVersion;
    }

    /**
     * Used for resuming connections**/
    public String getSessionId() {
        return sessionId;
    }

    /**
     * @return Gateway URL for resuming connections**/
    public String getResumeUrl() {
        return resumeUrl;
    }
}
