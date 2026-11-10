package com.flowershop.common.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.InputStream;

/** Response 4xx/5xx → đọc mã lỗi trong body {@code ApiResponse} → {@link RemoteServiceException}. */
class RemoteErrorHandler implements RestClient.ResponseSpec.ErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(RemoteErrorHandler.class);
    /** Body lỗi chuẩn chỉ vài trăm byte. Đọc giới hạn để service đích lỗi trả body khổng lồ không làm tràn bộ nhớ. */
    private static final int MAX_BODY_BYTES = 64 * 1024;

    private final String service;
    private final ObjectMapper objectMapper;

    RemoteErrorHandler(String service, ObjectMapper objectMapper) {
        this.service = service;
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpRequest request, ClientHttpResponse response) throws IOException {
        int status = response.getStatusCode().value();
        String code = readCode(response);
        log.warn("{} responded {} code={} ({} {})", service, status, code, request.getMethod(), request.getURI().getPath());
        throw RemoteServiceException.rejected(service, status, code);
    }

    private String readCode(ClientHttpResponse response) {
        try (InputStream body = response.getBody()) {
            JsonNode json = objectMapper.readTree(body.readNBytes(MAX_BODY_BYTES));
            return json == null ? null : json.path("code").asText(null);
        } catch (Exception e) {
            return null;   // body rỗng hoặc không phải JSON (vd lỗi từ proxy/load balancer)
        }
    }
}
