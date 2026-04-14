package com.interview.assistant.dto;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;

public class RatingRequest {

    @NotBlank
    private String prompt;

    @NotBlank
    private String designMarkdown;

    @Min(1)
    @Max(5)
    private int rating;

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public String getDesignMarkdown() {
        return designMarkdown;
    }

    public void setDesignMarkdown(String designMarkdown) {
        this.designMarkdown = designMarkdown;
    }

    public int getRating() {
        return rating;
    }

    public void setRating(int rating) {
        this.rating = rating;
    }
}
