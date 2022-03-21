/**
 * Copyright © 2016-2022 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.thingsboard.server.msa;

import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.server.msa.mapper.WsTelemetryResponse;

import javax.net.ssl.SSLParameters;
import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Slf4j
public class WsClient extends WebSocketClient {
    private WsTelemetryResponse message;

    private volatile boolean firstReplyReceived;
    private CountDownLatch firstReply = new CountDownLatch(1);
    private CountDownLatch latch = new CountDownLatch(1);

    WsClient(URI serverUri) {
        super(serverUri);
    }

    @Override
    public void onOpen(ServerHandshake serverHandshake) {
    }

    @Override
    public void onMessage(String message) {
        if (!firstReplyReceived) {
            firstReplyReceived = true;
            firstReply.countDown();
        } else {
            WsTelemetryResponse response = JacksonUtil.fromString(message, WsTelemetryResponse.class);
            if (!response.getData().isEmpty()) {
                this.message = response;
                latch.countDown();
            }
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        log.info("ws is closed, due to [{}]", reason);
    }

    @Override
    public void onError(Exception ex) {
        ex.printStackTrace();
    }

    public WsTelemetryResponse getLastMessage() {
        try {
            latch.await(10, TimeUnit.SECONDS);
            return this.message;
        } catch (InterruptedException e) {
            log.error("Timeout, ws message wasn't received");
        }
        return null;
    }

    void waitForFirstReply() {
        try {
            firstReply.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            log.error("Timeout, ws message wasn't received");
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void onSetSSLParameters(SSLParameters sslParameters) {
        sslParameters.setEndpointIdentificationAlgorithm(null);
    }
}
