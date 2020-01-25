package com.github.seratch.jslack.lightning.middleware.builtin;

import com.slack.api.app_backend.outgoing_webhooks.WebhookPayloadParser;
import com.slack.api.app_backend.slash_commands.SlashCommandPayloadParser;
import com.slack.api.app_backend.util.JsonPayloadExtractor;
import com.slack.api.app_backend.outgoing_webhooks.WebhookPayloadDetector;
import com.slack.api.app_backend.util.RequestTokenVerifier;
import com.slack.api.app_backend.slash_commands.SlashCommandPayloadDetector;
import com.slack.api.util.json.GsonFactory;
import com.github.seratch.jslack.lightning.middleware.Middleware;
import com.github.seratch.jslack.lightning.middleware.MiddlewareChain;
import com.github.seratch.jslack.lightning.request.Request;
import com.github.seratch.jslack.lightning.response.Response;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import lombok.extern.slf4j.Slf4j;

import static com.github.seratch.jslack.lightning.middleware.MiddlewareOps.isNoSlackSignatureRequest;

@Deprecated
@Slf4j
public class LegacyRequestVerification implements Middleware {

    private final RequestTokenVerifier verifier;
    private final JsonPayloadExtractor jsonPayloadExtractor = new JsonPayloadExtractor();
    private final SlashCommandPayloadDetector commandRequestDetector = new SlashCommandPayloadDetector();
    private final SlashCommandPayloadParser commandPayloadParser = new SlashCommandPayloadParser();
    private final WebhookPayloadDetector webhooksRequestDetector = new WebhookPayloadDetector();
    private final WebhookPayloadParser webhookPayloadParser = new WebhookPayloadParser();
    private final Gson gson = GsonFactory.createSnakeCase();

    public LegacyRequestVerification(String verificationToken) {
        this.verifier = new RequestTokenVerifier(verificationToken);
    }

    @Override
    public Response apply(Request req, Response resp, MiddlewareChain chain) throws Exception {
        if (isNoSlackSignatureRequest(req.getRequestType())) {
            return chain.next(req);
        }
        String actualToken;
        String body = req.getRequestBodyAsString();
        String json = jsonPayloadExtractor.extractIfExists(body);
        if (json != null) {
            JsonElement j = gson.fromJson(json, JsonElement.class);
            actualToken = j.getAsJsonObject().get("token").getAsString();
        } else {
            if (commandRequestDetector.isCommand(body)) {
                actualToken = commandPayloadParser.parse(body).getToken();
            } else if (webhooksRequestDetector.isWebhook(body)) {
                actualToken = webhookPayloadParser.parse(body).getToken();
            } else {
                log.info("Failed to find a verification token - {}", body);
                return Response.json(401, "{\"error\":\"invalid request\"}");
            }
        }
        if (verifier.isValid(actualToken)) {
            return chain.next(req);
        } else {
            log.info("Invalid verification token detected - {}", actualToken);
            return Response.json(401, "{\"error\":\"invalid request\"}");
        }
    }
}
