package com.lifepilot.ai;

import static org.junit.jupiter.api.Assertions.assertFalse;

import com.lifepilot.TestAppProperties;
import org.junit.jupiter.api.Test;

class OpenAiClientTest {
    @Test
    void missingApiKeyLeavesOptionalProviderDisabled() {
        OpenAiClient client = new OpenAiClient(TestAppProperties.defaults());

        assertFalse(client.isConfigured());
    }
}
