package com.example.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MCP Server exposing the {@code get_exchange_rate} tool over Streamable HTTP ({@code /mcp}),
 * backed by the Frankfurter API.
 */
public class CurrencyMcpServer {

    private static final Logger logger = LoggerFactory.getLogger(CurrencyMcpServer.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final String EXCHANGE_RATE_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "currency_from": {
                  "type": "string",
                  "description": "The currency to convert from (e.g., 'USD').",
                  "default": "USD"
                },
                "currency_to": {
                  "type": "string",
                  "description": "The currency to convert to (e.g., 'EUR').",
                  "default": "EUR"
                },
                "currency_date": {
                  "type": "string",
                  "description": "The date for the exchange rate or 'latest'. Defaults to 'latest'.",
                  "default": "latest"
                }
              }
            }
            """;

    public static String getExchangeRate(String currencyFrom, String currencyTo, String currencyDate) {
        String from = (currencyFrom == null || currencyFrom.isBlank()) ? "USD" : currencyFrom;
        String to = (currencyTo == null || currencyTo.isBlank()) ? "EUR" : currencyTo;
        String date = (currencyDate == null || currencyDate.isBlank()) ? "latest" : currencyDate;

        logger.info("--- 🛠️ Tool: get_exchange_rate called for converting {} to {} ---", from, to);
        try {
            String url = String.format(
                    "https://api.frankfurter.dev/v1/%s?from=%s&to=%s",
                    URLEncoder.encode(date, StandardCharsets.UTF_8),
                    URLEncoder.encode(from, StandardCharsets.UTF_8),
                    URLEncoder.encode(to, StandardCharsets.UTF_8));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                logger.error("❌ API request failed with status {}", response.statusCode());
                return OBJECT_MAPPER.writeValueAsString(
                        Map.of("error", "API request failed with status " + response.statusCode()));
            }

            JsonNode data = OBJECT_MAPPER.readTree(response.body());
            if (!data.has("rates")) {
                logger.error("❌ rates not found in response: {}", data);
                return OBJECT_MAPPER.writeValueAsString(Map.of("error", "Invalid API response format."));
            }
            logger.info("✅ API response: {}", data);
            return response.body();
        } catch (Exception e) {
            logger.error("❌ API request failed: {}", e.getMessage());
            try {
                return OBJECT_MAPPER.writeValueAsString(Map.of("error", "API request failed: " + e.getMessage()));
            } catch (Exception ignored) {
                return "{\"error\": \"API request failed\"}";
            }
        }
    }

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));

        HttpServletStreamableServerTransportProvider transportProvider =
                HttpServletStreamableServerTransportProvider.builder()
                        .mcpEndpoint("/mcp")
                        .build();

        Tool exchangeRateTool = Tool.builder()
                .name("get_exchange_rate")
                .description("Use this to get current exchange rate.")
                .inputSchema(McpJsonDefaults.getMapper(), EXCHANGE_RATE_SCHEMA)
                .build();

        McpSyncServer mcpServer = McpServer.sync(transportProvider)
                .serverInfo("Currency MCP Server 💵", "0.1.0")
                .capabilities(ServerCapabilities.builder().tools(true).build())
                .toolCall(exchangeRateTool, (exchange, request) -> {
                    Map<String, Object> arguments = request.arguments() != null ? request.arguments() : Map.of();
                    String from = String.valueOf(arguments.getOrDefault("currency_from", "USD"));
                    String to = String.valueOf(arguments.getOrDefault("currency_to", "EUR"));
                    String date = String.valueOf(arguments.getOrDefault("currency_date", "latest"));
                    String resultJson = getExchangeRate(from, to, date);
                    return CallToolResult.builder()
                            .content(List.of(new TextContent(resultJson)))
                            .isError(false)
                            .build();
                })
                .build();

        Server jettyServer = new Server(port);
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        jettyServer.setHandler(context);

        ServletHolder servletHolder = new ServletHolder(transportProvider);
        servletHolder.setAsyncSupported(true);
        context.addServlet(servletHolder, "/mcp");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                mcpServer.close();
                jettyServer.stop();
            } catch (Exception ignored) {
            }
        }));

        logger.info("🚀 MCP server started on port {}", port);
        jettyServer.start();
        jettyServer.join();
    }
}
