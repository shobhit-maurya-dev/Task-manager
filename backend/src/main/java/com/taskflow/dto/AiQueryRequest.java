package com.taskflow.dto;

public class AiQueryRequest {
    private String query;

    public AiQueryRequest() {}

    public AiQueryRequest(String query) {
        this.query = query;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }
}
