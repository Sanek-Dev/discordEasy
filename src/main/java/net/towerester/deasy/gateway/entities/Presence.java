package net.towerester.deasy.gateway.entities;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.towerester.deasy.Constants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Presence {
    private final List<Activity> activities;
    private DiscordStatus status;

    public Presence() {
        this.status = DiscordStatus.ONLINE;
        this.activities = new ArrayList<>();
    }

    /**
     * @return Unmodifiable array list of presence activities**/
    public final List<Activity> getActivities() {
        return Collections.unmodifiableList(activities);
    }

    /**
     * @return Presence online status**/
    public final DiscordStatus getStatus() {
        return status;
    }

    /**
     * Add activity to list**/
    public final void addActivity(Activity activity) {
        this.activities.add(activity);
    }

    /**
     * Set Online Status for Presence**/
    public final void setStatus(DiscordStatus status) {
        this.status = status;
    }

    /**
     * @return Converted Presence object**/
    public final ObjectNode toJson() {
        ObjectNode node = Constants.MAPPER.createObjectNode();
        node.putNull("since");

        ArrayNode arr = Constants.MAPPER.createArrayNode();
        for(Activity activity: activities) {
            arr.add(activity.toJson());
        }

        node.put("activities", arr);
        node.put("status", (status == DiscordStatus.ONLINE ? "online" : (status == DiscordStatus.OFFLINE ? "offline" : (status == DiscordStatus.INVISIBLE ? "invisible" : (status == DiscordStatus.IDLE ? "idle" : "dnd")))));
        node.put("afk", false);

        return node;
    }
}
