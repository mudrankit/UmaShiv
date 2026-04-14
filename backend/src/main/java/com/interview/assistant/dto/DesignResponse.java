package com.interview.assistant.dto;

import java.util.ArrayList;
import java.util.List;

public class DesignResponse {

    private String title;
    private List<String> functionalRequirements = new ArrayList<String>();
    private List<String> nonFunctionalRequirements = new ArrayList<String>();
    private List<String> entities = new ArrayList<String>();
    private List<String> apis = new ArrayList<String>();
    private String diagram;
    private String summary;
    private List<String> tradeOffs = new ArrayList<String>();
    private List<String> followUpQuestions = new ArrayList<String>();
    private String rawMarkdown;
    private String provider;
    private String model;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public List<String> getFunctionalRequirements() {
        return functionalRequirements;
    }

    public void setFunctionalRequirements(List<String> functionalRequirements) {
        this.functionalRequirements = functionalRequirements;
    }

    public List<String> getNonFunctionalRequirements() {
        return nonFunctionalRequirements;
    }

    public void setNonFunctionalRequirements(List<String> nonFunctionalRequirements) {
        this.nonFunctionalRequirements = nonFunctionalRequirements;
    }

    public List<String> getEntities() {
        return entities;
    }

    public void setEntities(List<String> entities) {
        this.entities = entities;
    }

    public List<String> getApis() {
        return apis;
    }

    public void setApis(List<String> apis) {
        this.apis = apis;
    }

    public String getDiagram() {
        return diagram;
    }

    public void setDiagram(String diagram) {
        this.diagram = diagram;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public List<String> getTradeOffs() {
        return tradeOffs;
    }

    public void setTradeOffs(List<String> tradeOffs) {
        this.tradeOffs = tradeOffs;
    }

    public List<String> getFollowUpQuestions() {
        return followUpQuestions;
    }

    public void setFollowUpQuestions(List<String> followUpQuestions) {
        this.followUpQuestions = followUpQuestions;
    }

    public String getRawMarkdown() {
        return rawMarkdown;
    }

    public void setRawMarkdown(String rawMarkdown) {
        this.rawMarkdown = rawMarkdown;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }
}