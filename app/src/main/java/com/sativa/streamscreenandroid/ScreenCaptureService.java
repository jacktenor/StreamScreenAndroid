package com.sativa.streamscreenandroid;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.IBinder;
import android.util.DisplayMetrics;
import androidx.core.app.NotificationCompat;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;

public class ScreenCaptureService extends Service {

    private static final String CHANNEL_ID = "ScreenCaptureChannel";
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private ServerSocket serverSocket;
    private boolean isStreaming = false;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        int resultCode = intent.getIntExtra("resultCode", Activity.RESULT_OK);
        Intent data = intent.getParcelableExtra("data");
        int port = intent.getIntExtra("port", -1);

        // Ensure the MediaProjection data is valid
        if (data == null || port == -1) {
            stopSelf();
            return START_STICKY;
        }

        // Start the foreground service with a notification immediately
        startForegroundService();

        // Initialize MediaProjection
        MediaProjectionManager projectionManager =
                (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        mediaProjection = projectionManager.getMediaProjection(resultCode, data);

        // Start capturing and streaming the screen
        startScreenCapture(port);

        return START_STICKY;
    }

    private void startForegroundService() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Screen Capture Service", NotificationManager.IMPORTANCE_LOW);
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Screen Capture Service")
                .setContentText("Screen sharing is active")
                .setSmallIcon(R.drawable.ic_notification) // Replace with actual icon
                .build();

        startForeground(1, notification);
    }

    private void startScreenCapture(int port) {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        imageReader = ImageReader.newInstance(metrics.widthPixels, metrics.heightPixels,
                PixelFormat.RGBA_8888, 2);

        virtualDisplay = mediaProjection.createVirtualDisplay("ScreenCapture",
                metrics.widthPixels, metrics.heightPixels, metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(), null, null);

        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(port);
                isStreaming = true;
                while (isStreaming) {
                    Socket clientSocket = serverSocket.accept();
                    OutputStream outputStream = clientSocket.getOutputStream();
                    while (isStreaming) {
                        Image image = imageReader.acquireLatestImage();
                        if (image != null) {
                            ByteArrayOutputStream jpegOutput = new ByteArrayOutputStream();
                            Bitmap bitmap = convertImageToBitmap(image);
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, jpegOutput);
                            byte[] jpegData = jpegOutput.toByteArray();

                            outputStream.write(jpegData);
                            outputStream.flush();
                            image.close();
                        }
                    }
                    clientSocket.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private Bitmap convertImageToBitmap(Image image) {
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer buffer = planes[0].getBuffer();
        int pixelStride = planes[0].getPixelStride();
        int rowStride = planes[0].getRowStride();
        int rowPadding = rowStride - pixelStride * image.getWidth();

        Bitmap bitmap = Bitmap.createBitmap(
                image.getWidth() + rowPadding / pixelStride,
                image.getHeight(),
                Bitmap.Config.ARGB_8888);
        bitmap.copyPixelsFromBuffer(buffer);

        return bitmap;
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        // Clean up resources and stop streaming
        if (mediaProjection != null) mediaProjection.stop();
        if (virtualDisplay != null) virtualDisplay.release();
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}