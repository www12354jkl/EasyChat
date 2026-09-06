package com.faust.easychat.ws;

import static org.mockito.Mockito.verify;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LocalWsPushRouterTest {

    @Mock
    private WsChannelWriter channelWriter;

    @InjectMocks
    private LocalWsPushRouter router;

    @Test
    void route_deliversLocally() {
        Map<String, Object> payload = Map.of("userId", "u1", "online", true);

        router.route("peer-1", "ONLINE_STATUS", payload);

        verify(channelWriter).sendToUser("peer-1", "ONLINE_STATUS", payload);
    }
}
