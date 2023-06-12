package net.towerester.deasy;

import com.fasterxml.jackson.databind.ObjectMapper;

public class Constants {
    public static final String BASE_URL = "https://discord.com/api/v10";
    public static final String LIB_VERSION = "v1.0.0-alpha";
    public static final String USER_AGENT = "DiscordBot (DISCORDIUM, " + LIB_VERSION + ")";
    public static final ObjectMapper MAPPER = new ObjectMapper();
}
