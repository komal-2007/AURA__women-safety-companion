package com.example.womensafetycomplanion;


import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.telephony.SmsManager;
import android.widget.Button;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import android.telephony.SmsManager;

public class HomeActivity extends AppCompatActivity implements SensorEventListener {

    SensorManager sensorManager;
    Sensor accelerometer;
    FusedLocationProviderClient fusedClient;
    FirebaseFirestore db;
    DatabaseReference sosRef;
    String userId;
    List<String> contactNumbers = new ArrayList<>();
    List<String> contactNames = new ArrayList<>();
    boolean sosTriggered = false;

    static final int PERMISSION_REQUEST_CODE = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        // Add this inside onCreate() after setContentView()

// Track if SMS was actually sent by the network
        registerReceiver(new android.content.BroadcastReceiver() {
            @Override
            public void onReceive(android.content.Context context, Intent intent) {
                switch (getResultCode()) {
                    case android.app.Activity.RESULT_OK:
                        Toast.makeText(HomeActivity.this,
                                "✅ SMS ACTUALLY SENT by network!", Toast.LENGTH_LONG).show();
                        break;
                    case android.telephony.SmsManager.RESULT_ERROR_GENERIC_FAILURE:
                        Toast.makeText(HomeActivity.this,
                                "❌ SMS FAILED — Generic error", Toast.LENGTH_LONG).show();
                        break;
                    case android.telephony.SmsManager.RESULT_ERROR_NO_SERVICE:
                        Toast.makeText(HomeActivity.this,
                                "❌ SMS FAILED — No cellular service!", Toast.LENGTH_LONG).show();
                        break;
                    case android.telephony.SmsManager.RESULT_ERROR_NULL_PDU:
                        Toast.makeText(HomeActivity.this,
                                "❌ SMS FAILED — Null PDU", Toast.LENGTH_LONG).show();
                        break;
                    case android.telephony.SmsManager.RESULT_ERROR_RADIO_OFF:
                        Toast.makeText(HomeActivity.this,
                                "❌ SMS FAILED — Radio/SIM off!", Toast.LENGTH_LONG).show();
                        break;
                }
            }
        }, new android.content.IntentFilter("SMS_SENT_"), android.os.Build.VERSION.SDK_INT >= 33
                ? android.content.Context.RECEIVER_NOT_EXPORTED : 0);

        checkAndRequestPermissions();

        db = FirebaseFirestore.getInstance();
        userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        fusedClient = LocationServices.getFusedLocationProviderClient(this);
        sosRef = FirebaseDatabase.getInstance().getReference("sos_alerts/" + userId);

        loadContacts();
        checkAndRequestPermissions(); // ← THIS is the fix

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        findViewById(R.id.btnSOS).setOnClickListener(v -> showSosCountdown());
        findViewById(R.id.btnContacts).setOnClickListener(v ->
                startActivity(new Intent(this, ContactsActivity.class)));
        findViewById(R.id.btnMap).setOnClickListener(v ->
                startActivity(new Intent(this, MapsActivity.class)));
        findViewById(R.id.btnFakeCall).setOnClickListener(v ->
                startActivity(new Intent(this, FakeCallActivity.class)));
        findViewById(R.id.btnSettings).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));
    }

    // ── PERMISSION FIX ──────────────────────────────────────────
    private void checkAndRequestPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                        != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.SEND_SMS
                    },
                    101
            );
        }
    }
    //NEW CODE

    //------------------



    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == 101) {
            boolean allGranted = true;

            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                Toast.makeText(this, "Permissions Granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Permissions Denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // ── LOAD CONTACTS ────────────────────────────────────────────
    private void loadContacts() {
        db.collection("users").document(userId)
                .collection("contacts")
                .get()
                .addOnSuccessListener(snapshot -> {
                    contactNumbers.clear();
                    contactNames.clear();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        contactNumbers.add(doc.getString("phone"));
                        contactNames.add(doc.getString("name"));
                    }
                });
    }

    // ── SOS COUNTDOWN ────────────────────────────────────────────
    private void showSosCountdown() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("🚨 SOS Alert")
                .setMessage("Sending SOS in 3 seconds...\nPress CANCEL to stop.")
                .setNegativeButton("CANCEL", (dialog, which) -> dialog.dismiss())
                .setCancelable(false);

        AlertDialog dialog = builder.create();
        dialog.show();

        new CountDownTimer(3000, 1000) {
            public void onFinish() {
                dialog.dismiss();
                triggerSOS();
            }
            public void onTick(long ms) {
                dialog.setMessage("Sending SOS in " + (ms / 1000 + 1) + " seconds...\nPress CANCEL to stop.");
            }
        }.start();
    }

    // ── TRIGGER SOS ──────────────────────────────────────────────
    private void triggerSOS() {
        if (sosTriggered) return;
        sosTriggered = true;

        // Vibrate immediately
        Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (vibrator != null)
            vibrator.vibrate(VibrationEffect.createWaveform(
                    new long[]{0, 500, 200, 500, 200, 500}, -1));

        Toast.makeText(this, "🚨 SOS triggered! Getting location...", Toast.LENGTH_SHORT).show();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            // No location permission — send SMS without location
            String message = "🚨 SOS ALERT! I need help! Location unavailable.";
            sendSmsToContacts(message);
            logSosToFirebase(0, 0, "unavailable");
            resetSosFlag();
            return;
        }

        // Step 1: Try getLastLocation first (instant)
        fusedClient.getLastLocation().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                // Got cached location — use it immediately
                android.location.Location location = task.getResult();
                handleLocationAndSend(location);
            } else {
                // No cached location — request a fresh one
                requestFreshLocation();
            }
        });

        // Reset flag after 15 seconds regardless
        resetSosFlag();
    }

    // Request a fresh GPS fix
    private void requestFreshLocation() {
        Toast.makeText(this, "📍 Getting fresh location...", Toast.LENGTH_SHORT).show();

        com.google.android.gms.location.LocationRequest locationRequest =
                new com.google.android.gms.location.LocationRequest.Builder(1000)
                        .setMaxUpdates(1)        // only need ONE update
                        .setPriority(com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY)
                        .build();

        com.google.android.gms.location.LocationCallback freshCallback =
                new com.google.android.gms.location.LocationCallback() {
                    @Override
                    public void onLocationResult(
                            com.google.android.gms.location.LocationResult result) {
                        fusedClient.removeLocationUpdates(this);
                        if (result != null && result.getLastLocation() != null) {
                            handleLocationAndSend(result.getLastLocation());
                        } else {
                            // Still no location — send without it
                            String message = "🚨 SOS ALERT! I need help! Could not get location.";
                            sendSmsToContacts(message);
                            logSosToFirebase(0, 0, "unavailable");
                        }
                    }
                };

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            fusedClient.requestLocationUpdates(locationRequest, freshCallback,
                    getMainLooper());

            // Timeout after 8 seconds — send without location if GPS takes too long
            new android.os.Handler(getMainLooper()).postDelayed(() -> {
                fusedClient.removeLocationUpdates(freshCallback);
            }, 8000);
        }
    }

    // Build message and send once we have location
    private void handleLocationAndSend(android.location.Location location) {
        double lat = location.getLatitude();
        double lng = location.getLongitude();
        String mapsLink = "https://maps.google.com/?q=" + lat + "," + lng;

        String message = "🚨 SOS ALERT!\n" +
                "I need URGENT help!\n" +
                "📍 My live location:\n" +
                mapsLink + "\n" +
                "Please come immediately or call me!";

        sendSmsToContacts(message);
        logSosToFirebase(lat, lng, mapsLink);

        Toast.makeText(this,
                "📍 Location captured: " + lat + ", " + lng,
                Toast.LENGTH_LONG).show();
    }

    // Reset the SOS flag after 15 seconds
    private void resetSosFlag() {
        new CountDownTimer(15000, 15000) {
            public void onFinish() { sosTriggered = false; }
            public void onTick(long ms) {}
        }.start();
    }

    // ── SEND SMS ─────────────────────────────────────────────────
    private void sendSmsToContacts(String message) {

        if (contactNumbers.isEmpty()) {
            Toast.makeText(this, "⚠️ No contacts! Add contacts first.", Toast.LENGTH_LONG).show();
            return;
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "❌ SMS permission not granted!", Toast.LENGTH_LONG).show();
            checkAndRequestPermissions();
            return;
        }

        // Use context-aware SmsManager (works on Android 12+)
        SmsManager smsManager;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            smsManager = this.getSystemService(SmsManager.class);
        } else {
            smsManager = SmsManager.getDefault();
        }

        if (smsManager == null) {
            Toast.makeText(this, "❌ SMS service unavailable on this device!", Toast.LENGTH_LONG).show();
            return;
        }

        // Keep message SHORT and simple — no emoji, no newlines
        // Some carriers block messages with emoji or special characters
        String cleanMessage = message
                .replace("🚨", "")
                .replace("📍", "")
                .replace("\n", " ")
                .trim();

        // Ensure message fits in one SMS (160 chars max for single SMS)
        // If longer, use sendMultipartTextMessage
        int sentCount = 0;

        for (String number : contactNumbers) {
            if (number == null || number.isEmpty()) continue;

            // Clean the number
            number = number.trim().replaceAll("[\\s\\-()]", "");
            if (!number.startsWith("+")) {
                number = "+91" + number;
            }

            try {
                // Create PendingIntents to track actual delivery
                Intent sentIntent = new Intent("SMS_SENT_" + number);
                Intent deliveredIntent = new Intent("SMS_DELIVERED_" + number);

                android.app.PendingIntent sentPI = android.app.PendingIntent.getBroadcast(
                        this, 0, sentIntent,
                        android.app.PendingIntent.FLAG_IMMUTABLE);

                android.app.PendingIntent deliveredPI = android.app.PendingIntent.getBroadcast(
                        this, 0, deliveredIntent,
                        android.app.PendingIntent.FLAG_IMMUTABLE);

                // Check if message needs to be split
                if (cleanMessage.length() > 160) {
                    ArrayList<String> parts = smsManager.divideMessage(cleanMessage);
                    ArrayList<android.app.PendingIntent> sentPIs = new ArrayList<>();
                    ArrayList<android.app.PendingIntent> deliveredPIs = new ArrayList<>();
                    for (int i = 0; i < parts.size(); i++) {
                        sentPIs.add(sentPI);
                        deliveredPIs.add(deliveredPI);
                    }
                    smsManager.sendMultipartTextMessage(number, null, parts, sentPIs, deliveredPIs);
                } else {
                    smsManager.sendTextMessage(number, null, cleanMessage, sentPI, deliveredPI);
                }

                sentCount++;
                Toast.makeText(this, "✅ SMS dispatched to: " + number, Toast.LENGTH_SHORT).show();

            } catch (Exception e) {
                Toast.makeText(this, "❌ Error sending to " + number + ": " + e.getMessage(),
                        Toast.LENGTH_LONG).show();
            }
        }

        if (sentCount > 0) {
            Toast.makeText(this, "🚨 SOS sent to " + sentCount + " contact(s)!", Toast.LENGTH_LONG).show();
        }
    }

    // ── LOG SOS TO FIREBASE REALTIME DB ──────────────────────────
    private void logSosToFirebase(double lat, double lng, String mapsLink) {
        String alertId = sosRef.push().getKey(); // auto-generate unique ID
        Map<String, Object> alert = new HashMap<>();
        alert.put("latitude", lat);
        alert.put("longitude", lng);
        alert.put("mapsLink", mapsLink);
        alert.put("timestamp", System.currentTimeMillis());
        alert.put("userId", userId);
        alert.put("status", "active");

        sosRef.child(alertId).setValue(alert)
                .addOnSuccessListener(unused ->
                        Toast.makeText(this, "SOS logged to database", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e ->
                        Toast.makeText(this, "DB log failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    // ── SHAKE DETECTION ──────────────────────────────────────────
    @Override
    public void onSensorChanged(SensorEvent event) {
        float x = event.values[0], y = event.values[1], z = event.values[2];
        double acceleration = Math.sqrt(x*x + y*y + z*z) - SensorManager.GRAVITY_EARTH;
        if (acceleration > 12 && !sosTriggered) showSosCountdown();
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    protected void onResume() {
        super.onResume();
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        loadContacts();
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }
}