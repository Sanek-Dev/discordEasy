package net.towerester.deasy.gateway.entities;

import com.fasterxml.jackson.databind.node.ObjectNode;
import net.towerester.deasy.Constants;

public class Activity {
    private String name;
    private Type type;

    /**
     * @param name Name of activity
     * @param type Type of activity (Playing, Watching, etc...)**/
    public Activity(Type type, String name) {
        this.type = type;
        this.name = name;
    }

    /**
     * @return Activity name**/
    public String getName() {
        return name;
    }

    /**
     * @return Activity type**/
    public Type getType() {
        return type;
    }

    /**
     * @return Converted Activity object**/
    public ObjectNode toJson() {
        ObjectNode node = Constants.MAPPER.createObjectNode();
        node.put("name", name);
        node.put("type", (type == Type.PLAYING ? 0 : (type == Type.STREAMING ? 1 : (type == Type.LISTENING ? 2 : (type == Type.WATCHING ? 3 : 5)))));

        return node;
    }

    public static enum Type {
        PLAYING, STREAMING, LISTENING, WATCHING, COMPETING;
    }
}
