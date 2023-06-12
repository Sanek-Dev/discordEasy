package net.towerester.deasy.gateway.events;

public interface EventListener {
    default void onHello(HelloEvent event) {

    }

    default void onReady(ReadyEvent event) {

    }
}
