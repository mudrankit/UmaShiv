package com.interview.assistant.dto;

import java.util.ArrayList;
import java.util.List;

public class RatingResponse {

    private int score;
    private String verdict;
    private List<String> strengths = new ArrayList<String>();
    private List<String> improvements = new ArrayList<String>();

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public String getVerdict() {
        return verdict;
    }

    public void setVerdict(String verdict) {
        this.verdict = verdict;
    }

    public List<String> getStrengths() {
        return strengths;
    }

    public void setStrengths(List<String> strengths) {
        this.strengths = strengths;
    }

    public List<String> getImprovements() {
        return improvements;
    }

    public void setImprovements(List<String> improvements) {
        this.improvements = improvements;
    }
}
