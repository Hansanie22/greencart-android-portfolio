package com.hansanie.greencart.model;

import androidx.annotation.NonNull;
import androidx.room.Entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity(tableName = "wishlist", primaryKeys = {"productId", "userId"})
public class Wishlist {
    @NonNull
    private Long productId;

    @NonNull
    private String userId; // Firebase UID එක ගබඩා කිරීමට
}