package com.sativa.streamscreenandroid;

import android.app.Activity;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private static final int REQUEST_CODE_SCREEN_CAPTURE = 100;
    private EditText portEditText;
    private Button startStreamButton;
    private boolean isStreaming = false;  // Tracks if streaming is active

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        portEditText = findViewById(R.id.portEditText);
        startStreamButton = findViewById(R.id.startStreamButton);
        TextView hyperlink = findViewById(R.id.hyperlink);

        // Set up hyperlink click to open URL
        hyperlink.setOnClickListener(v -> {
            String url = "https://beeralator.com";
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        });

        // Set up button click to start/stop streaming
        startStreamButton.setOnClickListener(v -> {
            if (isStreaming) {
                stopStreaming();
            } else {
                requestScreenCapturePermission();
            }
        });
    }

    private void requestScreenCapturePermission() {
        MediaProjectionManager projectionManager =
                (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        Intent captureIntent = projectionManager.createScreenCaptureIntent();
        startActivityForResult(captureIntent, REQUEST_CODE_SCREEN_CAPTURE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_SCREEN_CAPTURE && resultCode == RESULT_OK) {
            // Validate port input
            String portText = portEditText.getText().toString();
            int port;
            try {
                port = Integer.parseInt(portText);
                if (port < 1024 || port > 65535) throw new NumberFormatException(); // Invalid port range
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Please enter a valid port number (1024-65535).", Toast.LENGTH_SHORT).show();
                return;
            }

            // Start the streaming service
            Intent serviceIntent = new Intent(this, ScreenCaptureService.class);
            serviceIntent.putExtra("resultCode", resultCode);
            serviceIntent.putExtra("data", data);
            serviceIntent.putExtra("port", port);
            startForegroundService(serviceIntent);

            // Update UI to indicate streaming has started
            isStreaming = true;
            startStreamButton.setText(R.string.stop_streaming22);
        }
    }

    private void stopStreaming() {
        // Stop the streaming service
        Intent serviceIntent = new Intent(this, ScreenCaptureService.class);
        stopService(serviceIntent);

        // Update UI to indicate streaming has stopped
        isStreaming = false;
        startStreamButton.setText(R.string.start_streaming22);
        Toast.makeText(this, "Streaming stopped", Toast.LENGTH_SHORT).show();
    }
}