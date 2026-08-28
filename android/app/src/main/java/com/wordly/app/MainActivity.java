package com.wordly.app;

import android.os.Bundle;
import androidx.core.view.WindowCompat;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Belt-and-braces alongside the windowOptOutEdgeToEdgeEnforcement theme
        // attribute: reserve space for the system bars instead of drawing the
        // WebView content underneath them.
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
    }
}
