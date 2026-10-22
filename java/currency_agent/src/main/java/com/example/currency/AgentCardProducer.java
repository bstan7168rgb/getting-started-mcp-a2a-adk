package com.example.currency;

import io.a2a.server.PublicAgentCard;
import io.a2a.spec.AgentCard;
import io.a2a.util.Utils;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

/**
 * Produces the public {@link AgentCard} for the Currency A2A server.
 */
@ApplicationScoped
public class AgentCardProducer {

    @Produces
    @PublicAgentCard
    public AgentCard agentCard() {
        String port = System.getenv().getOrDefault("PORT", "8081");
        String agentUrl = System.getenv("AGENT_URL");
        if (agentUrl == null || agentUrl.isBlank()) {
            agentUrl = "http://localhost:" + port;
        }

        String cardJson = """
                {
                  "name": "currency_agent",
                  "description": "An agent that can help with currency conversions",
                  "url": "%s",
                  "version": "1.0.0",
                  "preferredTransport": "JSONRPC",
                  "capabilities": {
                    "streaming": false
                  },
                  "defaultInputModes": ["text/plain"],
                  "defaultOutputModes": ["text/plain"],
                  "skills": [
                    {
                      "id": "currency_conversion",
                      "name": "Currency Exchange Rate Lookup",
                      "description": "Looks up real-time currency exchange rates using the get_exchange_rate MCP tool",
                      "tags": ["currency", "exchange", "finance"]
                    }
                  ]
                }
                """.formatted(agentUrl);

        try {
            return Utils.OBJECT_MAPPER.readValue(cardJson, AgentCard.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create AgentCard", e);
        }
    }
}
