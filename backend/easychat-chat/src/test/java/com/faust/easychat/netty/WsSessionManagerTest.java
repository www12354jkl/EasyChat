package com.faust.easychat.netty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.faust.easychat.service.WsTicketService;
import com.faust.easychat.ws.WsOnlineStatusBroadcaster;
import com.faust.easychat.ws.WsSessionRegistry;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;

@ExtendWith(MockitoExtension.class)
class WsSessionManagerTest {

    @Mock
    private WsSessionRegistry sessionRegistry;
    @Mock
    private WsOnlineStatusBroadcaster onlineStatusBroadcaster;
    @Mock
    private WsTicketService wsTicketService;

    private WsSessionManager sessionManager;

    @BeforeEach
    void setUp() {
        sessionManager = new WsSessionManager(sessionRegistry, onlineStatusBroadcaster);
    }

    @Test
    void register_storesChannelSetsAttrMarksOnlineAndBroadcasts() {
        EmbeddedChannel channel = new EmbeddedChannel();

        sessionManager.register("user-1", channel);

        assertSame(channel, sessionManager.getChannel("user-1"));
        assertEquals("user-1", channel.attr(AuthWsHandler.USER_ID).get());
        verify(sessionRegistry).markOnline("user-1");
        verify(onlineStatusBroadcaster).broadcastAsync("user-1", true);
        channel.close();
    }

    @Test
    void unregister_currentChannel_removesMarksOfflineAndBroadcasts() {
        EmbeddedChannel channel = new EmbeddedChannel();
        sessionManager.register("user-1", channel);

        sessionManager.unregister(channel);

        assertNull(sessionManager.getChannel("user-1"));
        verify(sessionRegistry).markOffline("user-1");
        verify(onlineStatusBroadcaster).broadcastAsync("user-1", false);
        channel.close();
    }

    @Test
    void unregister_staleChannelAfterSameInstanceReconnect_doesNotMarkOfflineOrBroadcast() {
        EmbeddedChannel oldChannel = new EmbeddedChannel();
        EmbeddedChannel newChannel = new EmbeddedChannel();

        sessionManager.register("user-1", oldChannel);
        sessionManager.register("user-1", newChannel);

        assertFalse(oldChannel.isOpen(), "superseded channel should be closed on reconnect");
        assertSame(newChannel, sessionManager.getChannel("user-1"));

        // Simulate delayed handlerRemoved firing on the old channel after the new channel registered
        sessionManager.unregister(oldChannel);

        assertSame(newChannel, sessionManager.getChannel("user-1"));
        verify(sessionRegistry, never()).markOffline("user-1");
        verify(onlineStatusBroadcaster, never()).broadcastAsync("user-1", false);

        // Disconnecting the current channel does mark offline once
        sessionManager.unregister(newChannel);
        assertNull(sessionManager.getChannel("user-1"));
        verify(sessionRegistry, times(1)).markOffline("user-1");
        verify(onlineStatusBroadcaster, times(1)).broadcastAsync("user-1", false);
        newChannel.close();
    }

    @Test
    void authWsHandlerLifecycle_sameInstanceReconnect_doesNotBroadcastOffline() {
        EmbeddedChannel oldChannel = new EmbeddedChannel(new AuthWsHandler(wsTicketService, sessionManager));
        EmbeddedChannel newChannel = new EmbeddedChannel(new AuthWsHandler(wsTicketService, sessionManager));
        when(wsTicketService.consume("ticket-1")).thenReturn("user-1");
        when(wsTicketService.consume("ticket-2")).thenReturn("user-1");

        FullHttpRequest req1 = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/ws?ticket=ticket-1");
        oldChannel.writeInbound(req1);
        Object forwarded1 = oldChannel.readInbound();
        if (forwarded1 instanceof FullHttpRequest fhr) {
            fhr.release();
        }

        FullHttpRequest req2 = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/ws?ticket=ticket-2");
        newChannel.writeInbound(req2);
        Object forwarded2 = newChannel.readInbound();
        if (forwarded2 instanceof FullHttpRequest fhr) {
            fhr.release();
        }

        // Closing oldChannel triggers AuthWsHandler.channelInactive + handlerRemoved
        oldChannel.finishAndReleaseAll();

        assertSame(newChannel, sessionManager.getChannel("user-1"));
        verify(sessionRegistry, never()).markOffline("user-1");
        verify(onlineStatusBroadcaster, never()).broadcastAsync("user-1", false);

        // Closing the active newChannel marks offline exactly once (even with both channelInactive & handlerRemoved)
        newChannel.finishAndReleaseAll();

        assertNull(sessionManager.getChannel("user-1"));
        verify(sessionRegistry, times(1)).markOffline("user-1");
        verify(onlineStatusBroadcaster, times(1)).broadcastAsync("user-1", false);
    }

    @Test
    void unregister_calledTwiceForSameChannel_marksOfflineOnlyOnce() {
        EmbeddedChannel channel = new EmbeddedChannel();
        sessionManager.register("user-1", channel);

        sessionManager.unregister(channel);
        sessionManager.unregister(channel);

        verify(sessionRegistry, times(1)).markOffline("user-1");
        verify(onlineStatusBroadcaster, times(1)).broadcastAsync("user-1", false);
        channel.close();
    }

    @Test
    void register_sameChannelTwice_doesNotCloseActiveChannel() {
        EmbeddedChannel channel = new EmbeddedChannel();

        sessionManager.register("user-1", channel);
        sessionManager.register("user-1", channel);

        assertTrue(channel.isOpen());
        assertSame(channel, sessionManager.getChannel("user-1"));
        verify(sessionRegistry, never()).markOffline(anyString());
        channel.close();
    }

    @Test
    void unregister_unauthenticatedOrNullChannel_isNoOp() {
        EmbeddedChannel unauthenticated = new EmbeddedChannel();

        sessionManager.unregister(null);
        sessionManager.unregister(unauthenticated);

        verifyNoInteractions(sessionRegistry, onlineStatusBroadcaster);
        unauthenticated.close();
    }

    @Test
    void isOnline_delegatesToRegistryAndGuardsNull() {
        when(sessionRegistry.isOnline("user-1")).thenReturn(true);

        assertTrue(sessionManager.isOnline("user-1"));
        assertFalse(sessionManager.isOnline(null));
    }

    @Test
    void unregisterOldBeforeRegisterNew_marksOfflineThenOnlineAndKeepsNewChannelActive() {
        EmbeddedChannel oldChannel = new EmbeddedChannel();
        EmbeddedChannel newChannel = new EmbeddedChannel();

        sessionManager.register("user-1", oldChannel);
        sessionManager.unregister(oldChannel);
        assertNull(sessionManager.getChannel("user-1"));

        sessionManager.register("user-1", newChannel);

        assertSame(newChannel, sessionManager.getChannel("user-1"));
        assertTrue(newChannel.isOpen());

        InOrder inOrder = inOrder(sessionRegistry, onlineStatusBroadcaster);
        inOrder.verify(sessionRegistry).markOnline("user-1");
        inOrder.verify(onlineStatusBroadcaster).broadcastAsync("user-1", true);
        inOrder.verify(sessionRegistry).markOffline("user-1");
        inOrder.verify(onlineStatusBroadcaster).broadcastAsync("user-1", false);
        inOrder.verify(sessionRegistry).markOnline("user-1");
        inOrder.verify(onlineStatusBroadcaster).broadcastAsync("user-1", true);
        inOrder.verifyNoMoreInteractions();

        oldChannel.close();
        newChannel.close();
    }

    @Test
    void concurrentUnregisterOldAndRegisterNew_neverEndsWithStaleOffline() throws Exception {
        List<String> events = Collections.synchronizedList(new ArrayList<>());
        doAnswer(inv -> {
            events.add("online");
            return null;
        }).when(sessionRegistry).markOnline("user-1");
        // markOffline is skipped whenever register(newChannel) acquires the user lock before unregister(oldChannel).
        lenient().doAnswer(inv -> {
            events.add("offline");
            return null;
        }).when(sessionRegistry).markOffline("user-1");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        int expectedOfflineTotal = 0;
        try {
            for (int i = 0; i < 40; i++) {
                EmbeddedChannel oldChannel = new EmbeddedChannel();
                EmbeddedChannel newChannel = new EmbeddedChannel();
                sessionManager.register("user-1", oldChannel);
                events.clear();

                CyclicBarrier barrier = new CyclicBarrier(2);
                CountDownLatch done = new CountDownLatch(2);
                AtomicReference<Throwable> workerFailure = new AtomicReference<>();
                Runnable unregisterTask = () -> {
                    try {
                        barrier.await(2, TimeUnit.SECONDS);
                        sessionManager.unregister(oldChannel);
                    } catch (Throwable t) {
                        workerFailure.compareAndSet(null, t);
                    } finally {
                        done.countDown();
                    }
                };
                Runnable registerTask = () -> {
                    try {
                        barrier.await(2, TimeUnit.SECONDS);
                        sessionManager.register("user-1", newChannel);
                    } catch (Throwable t) {
                        workerFailure.compareAndSet(null, t);
                    } finally {
                        done.countDown();
                    }
                };
                if ((i & 1) == 0) {
                    pool.submit(unregisterTask);
                    pool.submit(registerTask);
                } else {
                    pool.submit(registerTask);
                    pool.submit(unregisterTask);
                }

                assertTrue(done.await(5, TimeUnit.SECONDS));
                assertNull(workerFailure.get());
                assertSame(newChannel, sessionManager.getChannel("user-1"));
                assertFalse(events.isEmpty());
                assertEquals("online", events.get(events.size() - 1));
                assertTrue(List.of("online").equals(events) || List.of("offline", "online").equals(events),
                        "Unexpected session transition sequence: " + events);
                expectedOfflineTotal += (int) events.stream().filter("offline"::equals).count();
                verify(onlineStatusBroadcaster, times(2 * (i + 1))).broadcastAsync("user-1", true);
                newChannel.close();
                oldChannel.close();
            }
        } finally {
            pool.shutdownNow();
        }
        verify(onlineStatusBroadcaster, times(expectedOfflineTotal)).broadcastAsync("user-1", false);
    }
}
