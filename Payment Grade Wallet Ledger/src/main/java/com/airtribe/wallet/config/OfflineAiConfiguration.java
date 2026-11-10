package com.airtribe.wallet.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** A deliberately small, anchored grammar for a reproducible demo without network credentials. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "wallet.ai.mode", havingValue = "offline", matchIfMissing = true)
public class OfflineAiConfiguration {
    private static final Pattern COUNT = Pattern.compile(
            "How many transfers from (\\d{4}-\\d{2}-\\d{2}) to (\\d{4}-\\d{2}-\\d{2})\\?",
            Pattern.CASE_INSENSITIVE);

    @Bean
    ChatModel offlineChatModel(ObjectMapper json) {
        return prompt -> {
            String output = "{\"type\":0}";
            String system = prompt.getInstructions().stream().filter(SystemMessage.class::isInstance)
                    .map(message -> message.getText()).findFirst().orElse("");
            String user = prompt.getInstructions().stream().filter(UserMessage.class::isInstance)
                    .map(message -> message.getText()).findFirst().orElse("");
            if (system.contains("advisory risk reviews")) {
                // Preserve truthful provenance: the service records its deterministic FALLBACK,
                // rather than labelling an offline response as an LLM assessment.
                throw new IllegalStateException("Offline mode requires administrator review");
            } else {
                try {
                    var input = json.readTree(user);
                    var matcher = COUNT.matcher(input.path("question").asText());
                    if (matcher.matches()) {
                        output = json.writeValueAsString(Map.of("type", 4, "from", matcher.group(1),
                                "to", matcher.group(2)));
                    }
                } catch (Exception invalid) {
                    // Unsupported or injected text produces no query plan.
                }
            }
            return new ChatResponse(List.of(new Generation(new AssistantMessage(output))));
        };
    }
}
