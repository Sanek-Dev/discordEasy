package net.towerester.deasy.gateway.events;

import net.towerester.deasy.gateway.DiscordClient;

public abstract class BaseEvent {
    private final DiscordClient client;

    public BaseEvent(DiscordClient client) {
        this.client = client;
    }

    public DiscordClient getClient() {
        return client;
    }
}
