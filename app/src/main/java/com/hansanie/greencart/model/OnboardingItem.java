package com.hansanie.greencart.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OnboardingItem {
    private int image;
    private String title;
    private String description;
}