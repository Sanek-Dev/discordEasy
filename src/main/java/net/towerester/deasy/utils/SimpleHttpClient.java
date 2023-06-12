package net.towerester.deasy.utils;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class SimpleHttpClient {
    private HttpClient client;
    private String baseUrl;
    private Map<String, String> defaultHeaders;
    private boolean exhausted;
    private int resetAfter;

    public static class Request {
        private String url;
        private String method;
        private Map<String, String> headers;
        private HttpRequest.BodyPublisher body;

        public static class Builder {
            private String url;
            private String method;
            private Map<String, String> headers;
            private HttpRequest.BodyPublisher body;

            public Builder(String url, String method) {
                this.url = url;
                this.method = method;
                this.headers = new HashMap<>();
                this.body = null;
            }

            /**
             * If the client has baseUrl set, then the value of the requestUrl will be baseUrl + requestUrl
             * @param url Request url*/
            public Builder setUrl(String url) {
                this.url = url;
                return this;
            }

            /**
             * @param method Request HTTP method (GET, POST, PUT, etc...)**/
            public Builder setMethod(String method) {
                this.method = method;
                return this;
            }

            /**
             * Add a header to a request
             * @param name Header name
             * @param value Header value**/
            public Builder addHeader(String name, String value) {
                this.headers.put(name, value);
                return this;
            }

            /**
             * Set the body for the request (requires a Content-Type header in most cases)
             * @param body Request Body**/
            public Builder setBody(HttpRequest.BodyPublisher body) {
                this.body = body;
                return this;
            }

            /**
             * @return Built Request**/
            public Request build() {
                return new Request(url, method, headers, body);
            }
        }

        /**
         * @param body Request body
         * @param method Request method
         * @param url Request url
         * @param headers Request header**/
        public Request(String url, String method, Map<String, String> headers, HttpRequest.BodyPublisher body) {
            this.url = url;
            this.method = method;
            this.headers = headers;
            this.body = body;
        }

        /**
         * Add a header to a request
         * @param name Header name
         * @param value Header value**/
        public void addHeader(String name, String value) {
            this.headers.put(name, value);
        }

        /**
         * Add a header to the request or, if it exists, replace its value with the argument value
         * @param name Header name
         * @param value Header value**/
        public void addOrReplaceHeader(String name, String value) {
            if(this.headers.containsKey(name)) {
                this.headers.replace(name, value);
            } else {
                this.headers.put(name, value);
            }
        }

        /**
         * If the client has baseUrl set, then the value of the requestUrl will be baseUrl + requestUrl
         * @param url Request url*/
        public void setUrl(String url) {
            this.url = url;
        }

        /**
         * @return Request url**/
        public String getUrl() {
            return this.url;
        }

        /**
         * Create original HttpRequest given current request**/
        public HttpRequest toHttpRequest() {
            HttpRequest.Builder b = HttpRequest.newBuilder();
            b.uri(URI.create(url));

            switch (method) {
                case "GET" -> b.GET();
                case "DELETE" -> b.DELETE();
                case "PUT" -> b.PUT(body);
                case "POST" -> b.POST(body);
                default -> b.method(method, body);
            }

            for(Map.Entry<String, String> entry: headers.entrySet()) {
                b.header(entry.getKey(), entry.getValue());
            }

            return b.build();
        }
    }

    public static class Builder {
        private String baseUrl;
        private Map<String, String> defaultHeaders;
        private boolean allowInsecureConnections;

        public Builder() {
            this.baseUrl = "";
            this.defaultHeaders = new HashMap<>();
            this.allowInsecureConnections = false;
        }

        /**
         * Set the base url for the client. When making requests, baseUrl will be concatenated with requestUrl
         * @param baseUrl Base url**/
        public Builder setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /**
         * The added header will be applied to all requests coming from this client
         * @param name Header name
         * @param value Header value**/
        public Builder addDefaultHeader(String name, String value) {
            this.defaultHeaders.put(name, value);
            return this;
        }

        /**
         * @param allowInsecureConnections If set to true, certificate verification will be disabled**/
        public Builder setAllowInsecureConnections(boolean allowInsecureConnections) {
            this.allowInsecureConnections = allowInsecureConnections;
            return this;
        }

        /**
         * @return Built SimpleHttpClient**/
        public SimpleHttpClient build() {
            HttpClient.Builder base = HttpClient.newBuilder();

            if(allowInsecureConnections) {
                TrustManager[] trustAllCerts = new TrustManager[]{
                        new X509TrustManager() {
                            public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                                return null;
                            }
                            public void checkClientTrusted(
                                    java.security.cert.X509Certificate[] certs, String authType) {
                            }
                            public void checkServerTrusted(
                                    java.security.cert.X509Certificate[] certs, String authType) {
                            }
                        }
                };

                SSLContext sslContext = null;

                try {
                    sslContext = SSLContext.getInstance("TLS");
                    sslContext.init(null, trustAllCerts, new SecureRandom());
                } catch(Exception e) {
                    e.printStackTrace();
                }

                base.sslContext(sslContext);
            }

            return new SimpleHttpClient(base.build(), baseUrl, defaultHeaders);
        }
    }

    /**
     * @param baseUrl Base url
     * @param client Original HttpClient
     * @param defaultHeaders Default headers**/
    public SimpleHttpClient(HttpClient client, String baseUrl, Map<String, String> defaultHeaders) {
        this.client = client;
        this.exhausted = false;
        this.resetAfter = 0;

        if(isValidUrl(baseUrl)) {
            this.baseUrl = baseUrl;
        }

        this.defaultHeaders = defaultHeaders;
    }

    /**
     * @return Base url**/
    public String getBaseUrl() {
        return baseUrl;
    }

    /**
     * @param exhausted If the value is set to true, then when calling to execute or executeAndReturn method, the thread will wait for resetAfter number of seconds, and after that it will execute the request
     * @param resetAfter The number of seconds that the thread will wait before executing if exhausted (1 second is added to the resetAfter value for safer execution of requests)**/
    public void setExhausted(boolean exhausted, int resetAfter) {
        this.exhausted = exhausted;

        if(exhausted) {
            this.resetAfter = resetAfter;
        }
    }

    public boolean isExhausted() {
        return exhausted;
    }

    /**
     * Set base url for http client
     * @param url Base url**/
    public void setBaseUrl(String url) {
        if(isValidUrl(url)) {
            this.baseUrl = url;
        }
    }

    /**
     * Execute an HTTP request
     * @return String response body**/
    public synchronized String execute(Request request) {
        try {
            if(exhausted) {
                wait(TimeUnit.SECONDS.toMillis(resetAfter + 1));

                this.exhausted = false;
                this.resetAfter = 0;

                if(!baseUrl.isEmpty()) {
                    request.setUrl(baseUrl + request.getUrl());
                }

                if(!defaultHeaders.isEmpty()) {
                    for(Map.Entry<String, String> entry: this.defaultHeaders.entrySet()) {
                        request.addOrReplaceHeader(entry.getKey(), entry.getValue());
                    }
                }
            } else {
                if(!baseUrl.isEmpty()) {
                    request.setUrl(baseUrl + request.getUrl());
                }

                if(!defaultHeaders.isEmpty()) {
                    for(Map.Entry<String, String> entry: this.defaultHeaders.entrySet()) {
                        request.addOrReplaceHeader(entry.getKey(), entry.getValue());
                    }
                }

            }
            return client.sendAsync(request.toHttpRequest(), HttpResponse.BodyHandlers.ofString()).join().body();
        } catch(Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Execute an HTTP request
     * @return Response**/
    public synchronized HttpResponse<String> executeAndReturn(Request request) {
        try {
            if(exhausted) {
                wait(TimeUnit.SECONDS.toMillis(resetAfter + 1));

                this.exhausted = false;
                this.resetAfter = 0;

                if(!baseUrl.isEmpty()) {
                    request.setUrl(baseUrl + request.getUrl());
                }

                if(!defaultHeaders.isEmpty()) {
                    for(Map.Entry<String, String> entry: this.defaultHeaders.entrySet()) {
                        request.addOrReplaceHeader(entry.getKey(), entry.getValue());
                    }
                }
            } else {
                if(!baseUrl.isEmpty()) {
                    request.setUrl(baseUrl + request.getUrl());
                }

                if(!defaultHeaders.isEmpty()) {
                    for(Map.Entry<String, String> entry: this.defaultHeaders.entrySet()) {
                        request.addOrReplaceHeader(entry.getKey(), entry.getValue());
                    }
                }

            }
            return client.sendAsync(request.toHttpRequest(), HttpResponse.BodyHandlers.ofString()).join();
        } catch(Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private boolean isValidUrl(String url) {
        try {
            new URL(url);
        } catch(IOException exception) {
            return false;
        }

        return true;
    }
}
