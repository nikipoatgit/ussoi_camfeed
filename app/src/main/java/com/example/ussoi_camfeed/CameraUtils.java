package com.example.ussoi_camfeed;

import android.content.Context;
import android.util.Log;

import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerationAndroid;
import org.webrtc.CameraEnumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.Size;

import java.util.List;

public class CameraUtils {
    private static final String TAG = "CameraUtils";

    // This helper class finds the best resolution that matches the sensor's native aspect ratio.
    public static CameraEnumerationAndroid.CaptureFormat findBestCaptureFormat(
            Context context, String deviceName, int targetHeight) {

        CameraEnumerator enumerator = new Camera2Enumerator(context);
        List<CameraEnumerationAndroid.CaptureFormat> supportedFormats = enumerator.getSupportedFormats(deviceName);

        if (supportedFormats == null || supportedFormats.isEmpty()) {
            Log.e(TAG, "No supported formats for camera: " + deviceName);
            return null;
        }

        // 1. Find the native aspect ratio by looking at the highest resolution format
        CameraEnumerationAndroid.CaptureFormat largestFormat = supportedFormats.get(0);
        for (CameraEnumerationAndroid.CaptureFormat format : supportedFormats) {
            if (format.width * format.height > largestFormat.width * largestFormat.height) {
                largestFormat = format;
            }
        }
        double targetAspectRatio = (double) largestFormat.width / largestFormat.height;
        Log.d(TAG, "Native sensor aspect ratio is approx: " + String.format("%.2f", targetAspectRatio));


        // 2. Find the best format that matches the target height and aspect ratio
        CameraEnumerationAndroid.CaptureFormat bestFormat = null;
        int minDiff = Integer.MAX_VALUE;

        for (CameraEnumerationAndroid.CaptureFormat format : supportedFormats) {
            // Check if the aspect ratio is very close to the native one
            double formatAspectRatio = (double) format.width / format.height;
            if (Math.abs(targetAspectRatio - formatAspectRatio) < 0.1) { // Allow for small tolerance
                // Find the format with the height closest to our target
                int diff = Math.abs(format.height - targetHeight);
                if (diff < minDiff) {
                    minDiff = diff;
                    bestFormat = format;
                }
            }
        }

        if (bestFormat != null) {
            Log.d(TAG, "Found best match for " + targetHeight + "p: " +
                    bestFormat.width + "x" + bestFormat.height);
        } else {
            Log.w(TAG, "Could not find a format matching native aspect ratio for " + targetHeight + "p. Falling back to first available format.");
            return supportedFormats.get(0); // Fallback
        }

        return bestFormat;
    }
}
