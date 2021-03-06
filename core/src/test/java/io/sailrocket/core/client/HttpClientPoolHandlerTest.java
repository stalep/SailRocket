/*
 * Copyright 2018 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
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

package io.sailrocket.core.client;

import static org.assertj.core.api.Assertions.assertThat;

import io.sailrocket.api.connection.HttpClientPool;
import io.sailrocket.api.http.HttpMethod;
import io.sailrocket.api.http.HttpRequest;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpVersion;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(VertxUnitRunner.class)
public class HttpClientPoolHandlerTest {

    protected volatile int count;
    private Vertx vertx = Vertx.vertx();

    @Before
    public void before(TestContext ctx) {
        count = 0;
        vertx.createHttpServer().requestHandler(req -> {
            count++;
            req.response().putHeader("foo", "bar").end("hello from server");
        }).listen(8088, "localhost", ctx.asyncAssertSuccess());
    }

    @After
    public void after(TestContext ctx) {
        vertx.close(ctx.asyncAssertSuccess());
    }

    @Test
    public void simpleHeaderRequest() throws Exception {
        HttpClientPool client = HttpClientProvider.netty.builder()
                .host("localhost")
                .concurrency(1)
                .port(8088)
                .protocol(HttpVersion.HTTP_1_1)
                .threads(1)
                .ssl(false)
                .size(1)
                .build();

        CountDownLatch startLatch = new CountDownLatch(1);
        client.start(nil -> startLatch.countDown());
        assertThat(startLatch.await(10, TimeUnit.SECONDS)).isTrue();

        HttpRequest conn = client.request(client.executors().next(), HttpMethod.GET, "/", null);
        CountDownLatch latch = new CountDownLatch(4);

        conn.statusHandler(code -> {
            assertThat(code).isEqualTo(200);
            latch.countDown();
        }).headerHandler((header, value) -> {
            if ("foo".equals(header)) {
                assertThat(value).isEqualTo("bar");
                latch.countDown();
            }
        })
        .bodyPartHandler(input -> {
            byte[] bytes = new byte[input.readableBytes()];
            input.readBytes(bytes, 0, bytes.length);
            assertThat(new String(bytes)).isEqualTo("hello from server");
            latch.countDown();
        }).endHandler(() -> {
            latch.countDown();
        });

        conn.end();

        assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(count).isEqualTo(1);
    }
}
