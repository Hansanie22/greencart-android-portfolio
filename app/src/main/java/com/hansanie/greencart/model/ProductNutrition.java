package com.hansanie.greencart.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductNutrition {
    private Long id;
    private String nutritionKey;
    private String nutritionValue;
}