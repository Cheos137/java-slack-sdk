package com.slack.api.bolt.middleware.builtin;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.slack.api.SlackConfig;
import com.slack.api.bolt.middleware.Middleware;
import com.slack.api.bolt.middleware.MiddlewareChain;
import com.slack.api.bolt.request.Request;
import com.slack.api.bolt.request.RequestType;
import com.slack.api.bolt.request.builtin.EventRequest;
import com.slack.api.bolt.response.Response;
import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.response.bots.BotsInfoResponse;
import com.slack.api.model.event.MemberJoinedChannelEvent;
import com.slack.api.model.event.MemberLeftChannelEvent;
import com.slack.api.util.json.GsonFactory;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Filters some events that may be generated by this app for Events API.
 */
@Slf4j
public class IgnoringSelfEvents implements Middleware {

    private final Gson gson;

    public IgnoringSelfEvents(SlackConfig config) {
        this.gson = GsonFactory.createSnakeCase(config);
    }

    // cached bot_id <> bot_user_id mapping
    private final ConcurrentMap<String, String> botIdToBotUserId = new ConcurrentHashMap<>();

    protected ConcurrentMap<String, String> getBotIdToBotUserId() {
        return this.botIdToBotUserId;
    }

    private List<String> eventTypesNotToMiss = new ArrayList<>();

    {
        eventTypesNotToMiss.add(MemberJoinedChannelEvent.TYPE_NAME);
        eventTypesNotToMiss.add(MemberLeftChannelEvent.TYPE_NAME);
    }

    public List<String> getEventTypesNotToMiss() {
        return eventTypesNotToMiss;
    }

    public void setEventTypesNotToMiss(List<String> eventTypesNotToMiss) {
        this.eventTypesNotToMiss = eventTypesNotToMiss;
    }

    @Override
    public Response apply(Request req, Response resp, MiddlewareChain chain) throws Exception {
        if (req.getRequestType() == RequestType.Event) {
            String appBotUserId = req.getContext().getBotUserId();
            if (appBotUserId == null) {
                return chain.next(req);
            }

            EventRequest eventRequest = (EventRequest) req;
            String eventType = eventRequest.getEventType();
            if (eventType == null || eventTypesNotToMiss.contains(eventType)) {
                return chain.next(req);
            }

            JsonObject eventElem = extractEventElem(eventRequest.getRequestBodyAsString());
            if (eventElem != null) {
                JsonElement eventUserIdElem = eventElem.get("user");
                String eventBotUserId = eventUserIdElem != null ? eventUserIdElem.getAsString() : null;
                JsonElement botIdElem = eventElem.get("bot_id");
                if (eventBotUserId == null && botIdElem != null) {
                    String botId = botIdElem.getAsString();
                    eventBotUserId = findAndSaveBotUserId(req.getContext().client(), botId);
                }
                if (eventBotUserId != null && eventBotUserId.equals(appBotUserId)) {
                    log.debug("Skipped the event (type: {}) as it was generated by this app's bot user", eventType);
                    return resp;
                }
            }
        }
        return chain.next(req);
    }

    private JsonObject extractEventElem(String requestBody) {
        JsonElement payload = gson.fromJson(requestBody, JsonElement.class);
        return payload.getAsJsonObject().getAsJsonObject("event");
    }

    public String findAndSaveBotUserId(MethodsClient client, String botId) throws IOException, SlackApiException {
        String botUserId = getBotIdToBotUserId().get(botId);
        if (botUserId != null) {
            return botUserId;
        } else {
            BotsInfoResponse botInfo = client.botsInfo(r -> r.bot(botId));
            if (botInfo.isOk()) {
                botUserId = botInfo.getBot().getUserId();
                if (botUserId != null) {
                    getBotIdToBotUserId().put(botId, botUserId);
                }
                return botUserId;
            } else {
                return null;
            }
        }
    }

}
