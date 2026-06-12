package io.mehdieidi.modless.backend.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SpringAiChatMemoryServiceTest {

    @Test
    void storesRecentMessagesAndClearsConversation() {
        SpringAiChatMemoryService memory = new SpringAiChatMemoryService();

        memory.appendUser("thread-1", "hello");
        memory.appendAssistant("thread-1", "hi");
        memory.appendUser("thread-1", "again");

        assertEquals(2, memory.recent("thread-1", 2).size());
        assertEquals("ASSISTANT", memory.recent("thread-1", 2).get(0).role());
        assertEquals("again", memory.recent("thread-1", 2).get(1).content());

        memory.clear("thread-1");

        assertTrue(memory.recent("thread-1", 2).isEmpty());
    }
}
