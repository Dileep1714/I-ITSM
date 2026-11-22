package com.iitsm.backend.ai;

import com.iitsm.backend.config.ItsmProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/** Chooses the AI provider at start-up: Gemini when a key is configured, otherwise the disabled stand-in. */
@Configuration
public class AiConfig {

    private static final Logger log = LoggerFactory.getLogger(AiConfig.class);

    @Bean
    public AiAssistant aiAssistant(ItsmProperties.Gemini gemini, ObjectMapper mapper) {
        if (gemini.enabled()) {
            log.info("AI copilot enabled: Gemini, models {}", gemini.models());
            return new GeminiAssistant(gemini, mapper);
        }
        log.info("AI copilot disabled: no iitsm.ai.gemini.api-key configured (keyword intelligence still works)");
        return new DisabledAssistant();
    }
}
