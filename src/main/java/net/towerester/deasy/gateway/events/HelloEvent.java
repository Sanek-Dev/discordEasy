package net.towerester.deasy.gateway.events;

import net.towerester.deasy.gateway.DiscordClient;

public class HelloEvent extends BaseEvent {
    private final int heartbeatInterval;

    public HelloEvent(DiscordClient client, int heartbeatInterval) {
        super(client);
        this.heartbeatInterval = heartbeatInterval;
    }

    /**
     * @return Interval (in milliseconds) an app should heartbeat with**/
    public int getHeartbeatInterval() {
        return heartbeatInterval;
    }
}
