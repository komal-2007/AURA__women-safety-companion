package com.example.womensafetycomplanion;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
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
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.ArrayList;
import java.util.List;

public class HomeActivity extends AppCompatActivity implements SensorEventListener {

    SensorManager sensorManager;
    Sensor accelerometer;
    FusedLocationProviderClient fusedClient;
    FirebaseFirestore db;
    String userId;
    List<String> contactNumbers = new ArrayList<>();
    boolean sosTriggered = false;
    static final int LOCATION_PERMISSION_CODE = 101;
    static final int SMS_PERMISSION_CODE = 102;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        db = FirebaseFirestore.getInstance();
        userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        fusedClient = LocationServices.getFusedLocationProviderClient(this);

        // Load trusted contacts from Firestore
        loadContacts();

        // Request permissions
        requestPermissions();

        // Shake detection setup
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        // SOS button
        Button btnSOS = findViewById(R.id.btnSOS);
        btnSOS.setOnClickListener(v -> showSosCountdown());

        // Navigate to Contacts
        Button btnContacts = findViewById(R.id.btnContacts);
        btnContacts.setOnClickListener(v ->
                startActivity(new Intent(this, ContactsActivity.class)));

        // Navigate to Map
        Button btnMap = findViewById(R.id.btnMap);
        btnMap.setOnClickListener(v ->
                startActivity(new Intent(this, MapsActivity.class)));

        // Navigate to Fake Call
        Button btnFakeCall = findViewById(R.id.btnFakeCall);
        btnFakeCall.setOnClickListener(v ->
                startActivity(new Intent(this, FakeCallActivity.class)));
    }

    // Load contacts from Firestore
    private void loadContacts() {
        db.collection("users").document(userId)
                .collection("contacts")
                .get()
                .addOnSuccessListener(snapshot -> {
                    contactNumbers.clear();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        contactNumbers.add(doc.getString("phone"));
                    }
                });
    }

    // Show 3-second countdown before sending SOS (so you can cancel during demo)
    private void showSosCountdown() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("SOS Alert")
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

    // Main SOS trigger
    private void triggerSOS() {
        if (sosTriggered) return;
        sosTriggered = true;

        // Vibrate phone
        Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 500, 200, 500, 200, 500}, -1));

        // Get location and send SMS
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            fusedClient.getLastLocation().addOnSuccessListener(location -> {
                String message;
                if (location != null) {
                    String mapsLink = "https://maps.google.com/?q="
                            + location.getLatitude() + "," + location.getLongitude();
                    message = "🚨 SOS ALERT! I need help! My location: " + mapsLink;
                } else {
                    message = "🚨 SOS ALERT! I need help! Could not get location.";
                }
                sendSmsToContacts(message);
            });
        } else {
            sendSmsToContacts("🚨 SOS ALERT! I need help! Location unavailable.");
        }

        Toast.makeText(this, "SOS Sent!", Toast.LENGTH_LONG).show();

        // Reset after 10 seconds so it can be triggered again
        new CountDownTimer(10000, 1000) {
            public void onFinish() { sosTriggered = false; }
            public void onTick(long ms) {}
        }.start();
    }

    // Send SMS to all trusted contacts
    private void sendSmsToContacts(String message) {
        if (contactNumbers.isEmpty()) {
            Toast.makeText(this, "No contacts added! Go to Contacts first.", Toast.LENGTH_LONG).show();
            return;
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                == PackageManager.PERMISSION_GRANTED) {
            SmsManager smsManager = SmsManager.getDefault();
            for (String number : contactNumbers) {
                smsManager.sendTextMessage(number, null, message, null, null);
            }
            Toast.makeText(this, "SMS sent to " + contactNumbers.size() + " contacts", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "SMS permission not granted", Toast.LENGTH_SHORT).show();
        }
    }

    // Shake detection
    @Override
    public void onSensorChanged(SensorEvent event) {
        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];
        double acceleration = Math.sqrt(x * x + y * y + z * z) - SensorManager.GRAVITY_EARTH;
        if (acceleration > 12 && !sosTriggered) {
            showSosCountdown();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    protected void onResume() {
        super.onResume();
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        loadContacts(); // refresh contacts every time you come back to home
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }

    // Request permissions
    private void requestPermissions() {
        List<String> permissions = new ArrayList<>();
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.SEND_SMS);
        }
        if (!permissions.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    permissions.toArray(new String[0]), LOCATION_PERMISSION_CODE);
        }
    }
}