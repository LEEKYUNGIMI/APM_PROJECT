package com.leekyungmi.apm.sdk.sender;

import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

public class ApmEventSender {
    private final RestTemplate restTemplate;
    private final String apmServerUrl;


    public ApmEventSender(String apmServerUrl) {
        this.restTemplate = new RestTemplate();
        this.apmServerUrl = apmServerUrl;
    }

    public void send(String sppId, String type, String uri, String method, int statusCode, int durationMs, String message, String serverIp) {
        Map<String, Object> body = new HashMap<>();
        body.put("appId", sppId);
        body.put("type", type);
        body.put("uri", uri);
        body.put("method", method);
        body.put("statusCode", statusCode);
        body.put("durationMs", durationMs);
        body.put("message", message);
        body.put("serverIp", serverIp);

        try{
            restTemplate.postForObject(apmServerUrl + "/api/events", body, Void.class);
        }catch (Exception e){
            System.err.println("Failed to send event to APM server: " + e.getMessage());
        }

    }
}
