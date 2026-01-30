package com.example.uwhapp.dto;
import java.util.Map;

public class PushSubscription {
    private String endpoint;
    private Map<String,String> keys;
    // getters & setters
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public Map<String,String> getKeys() { return keys; }
    public void setKeys(Map<String,String> keys) { this.keys = keys; }
}
