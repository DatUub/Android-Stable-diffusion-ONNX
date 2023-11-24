package com.example.open.diffusion;

import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Color;
import android.os.Bundle;

import com.google.android.material.color.DynamicColors;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Use Material You user colours
        DynamicColors.applyIfAvailable(this);
        // Remove status bar background color
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        // Hide action bar
        getSupportActionBar().hide();
        setContentView(R.layout.activity_main);
        getSupportFragmentManager().beginTransaction().replace(R.id.container, MainFragment.newInstance()).commit();
    }
}