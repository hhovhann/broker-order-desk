package dev.brokerdesk.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Server-sent events: tells browsers "something changed" so they refetch immediately instead of waiting for the next poll. Payloads are tiny on purpose. */
@Component
public class StreamHub {
    private record Client(String user, SseEmitter emitter) {}
    private final List<Client> clients = new CopyOnWriteArrayList<>();

    public SseEmitter register(String user) {
        SseEmitter e = new SseEmitter(0L);
        Client c = new Client(user, e);
        clients.add(c);
        e.onCompletion(() -> clients.remove(c));
        e.onTimeout(() -> clients.remove(c));
        e.onError(x -> clients.remove(c));
        send(c, "hello", user);
        return e;
    }

    public void broadcast(String event, String data) { clients.forEach(c -> send(c, event, data)); }
    public void sendTo(String user, String event, String data) { clients.stream().filter(c -> c.user.equals(user)).forEach(c -> send(c, event, data)); }
    public int connected() { return clients.size(); }

    @Scheduled(fixedRate = 15_000)
    void heartbeat() { broadcast("ping", "1"); }

    private void send(Client c, String event, String data) {
        try { c.emitter.send(SseEmitter.event().name(event).data(data)); }
        catch (IOException | IllegalStateException gone) { clients.remove(c); }
    }
}
