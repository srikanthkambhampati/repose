/*
 * _=_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_=
 * Repose
 * _-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-
 * Copyright (C) 2010 - 2015 Rackspace US, Inc.
 * _-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_=_
 */
package org.openrepose.core.services.httpclient.impl;

import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.config.*;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.ssl.TrustStrategy;
import org.openrepose.core.service.httpclient.config.PoolType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.UUID;

public final class HttpConnectionPoolProvider {

    public static final String CLIENT_INSTANCE_ID = "CLIENT_INSTANCE_ID";
    private static final Logger LOG = LoggerFactory.getLogger(HttpConnectionPoolProvider.class);
    private static final String CHUNKED_ENCODING_PARAM = "chunked-encoding";

    private HttpConnectionPoolProvider() {
    }

    public static HttpClient genClient(PoolType poolConf) {
        //Generate a UUID for this client
        String uuid = UUID.randomUUID().toString();

        //SSL Configuration
        //todo: it seems odd that we trust /all/ certs. note that TrustSelfSignedStrategy.INSTANCE exists.
        SSLContext sslContext = null;
        try {
            sslContext = SSLContextBuilder.create().loadTrustMaterial(new TrustStrategy() {
                //Trust all certificates
                @Override
                public boolean isTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
                    return true;
                }
            }).build();
        } catch (Exception e) {
            LOG.error("Problem creating SSL context: ", e);
        }

        //Configuration for the HttpClientBuilder
        PoolingHttpClientConnectionManager poolingConnectionManager = new PoolingHttpClientConnectionManager();
        poolingConnectionManager.setDefaultMaxPerRoute(poolConf.getHttpConnManagerMaxPerRoute());
        poolingConnectionManager.setMaxTotal(poolConf.getHttpConnManagerMaxTotal());

        MessageConstraints messageConstraints = MessageConstraints.custom()
                .setMaxHeaderCount(poolConf.getHttpConnectionMaxHeaderCount())
                .setMaxLineLength(poolConf.getHttpConnectionMaxLineLength())
                .build();

        RequestConfig requestConfig = RequestConfig.custom()
                .setRedirectsEnabled(false)
                .setSocketTimeout(poolConf.getHttpSocketTimeout())
                .setConnectTimeout(poolConf.getHttpConnectionTimeout())
                .build();

        ConnectionConfig connectionConfig = ConnectionConfig.custom()
                .setBufferSize(poolConf.getHttpSocketBufferSize())
                .setMessageConstraints(messageConstraints)
                .build();

        SocketConfig socketConfig = SocketConfig.custom()
                .setTcpNoDelay(poolConf.isHttpTcpNodelay())
                .build();

        //todo: Actually, this won't work. A HttpClient object does not appear to be able to store state without using deprecated methods.
        Registry registry = RegistryBuilder.create()
                .register(CLIENT_INSTANCE_ID, uuid)
                .register(CHUNKED_ENCODING_PARAM, poolConf.isChunkedEncoding())
                .build();

        //Configure the HttpClientBuilder with the configuration from above
        HttpClientBuilder httpClientBuilder = HttpClientBuilder.create()
                .disableRedirectHandling()
                .disableCookieManagement()
                .setDefaultConnectionConfig(connectionConfig)
                .setDefaultSocketConfig(socketConfig)
                .setDefaultRequestConfig(requestConfig)
                .setConnectionManager(poolingConnectionManager)
                .setSslcontext(sslContext)
                .setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE)
                .setKeepAliveStrategy(new ConnectionKeepAliveWithTimeoutStrategy(poolConf.getKeepaliveTimeout()));

        //Build a configured HttpClient
        CloseableHttpClient httpClient = httpClientBuilder.build();

        LOG.info("HTTP connection pool {} with instance id {} has been created", poolConf.getId(), uuid);

        return httpClient;
    }
}
