package com.example.womensafetycomplanion;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class SettingsActivity extends AppCompatActivity {

    TextView tvPhone, tvUserId;
    EditText etName;
    Button btnSaveName, btnLogout, btnSosHistory;
    FirebaseAuth mAuth;
    FirebaseFirestore db;
    DatabaseReference userRef, sosRef;
    String userId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        FirebaseUser user = mAuth.getCurrentUser();

        if (user == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        userId = user.getUid();
        userRef = FirebaseDatabase.getInstance().getReference("users/" + userId);
        sosRef  = FirebaseDatabase.getInstance().getReference("sos_alerts/" + userId);

        tvPhone    = findViewById(R.id.tvPhone);
        tvUserId   = findViewById(R.id.tvUserId);
        etName     = findViewById(R.id.etName);
        btnSaveName  = findViewById(R.id.btnSaveName);
        btnLogout    = findViewById(R.id.btnLogout);
        btnSosHistory = findViewById(R.id.btnSosHistory);

        // Show phone number and user ID
        tvPhone.setText(user.getPhoneNumber() != null ? user.getPhoneNumber() : "No phone");
        tvUserId.setText("User ID: " + userId.substring(0, 12) + "...");

        // Load saved name from Realtime DB
        userRef.child("name").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    etName.setText(snapshot.getValue(String.class));
                }
            }
            @Override public void onCancelled(DatabaseError error) {}
        });

        // Save name
        btnSaveName.setOnClickListener(v -> saveName());

        // SOS History
        btnSosHistory.setOnClickListener(v -> showSosHistory());

        // Logout
        btnLogout.setOnClickListener(v -> showLogoutConfirmation());
    }

    private void saveName() {
        String name = etName.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(this, "Please enter your name", Toast.LENGTH_SHORT).show();
            return;
        }
        // Save to Realtime DB
        userRef.child("name").setValue(name)
                .addOnSuccessListener(unused ->
                        Toast.makeText(this, "Name saved!", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());

        // Also save to Firestore
        Map<String, Object> data = new HashMap<>();
        data.put("name", name);
        db.collection("users").document(userId).set(data);
    }

    private void showSosHistory() {
        sosRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (!snapshot.exists() || snapshot.getChildrenCount() == 0) {
                    new AlertDialog.Builder(SettingsActivity.this)
                            .setTitle("SOS History")
                            .setMessage("No SOS alerts have been triggered yet.")
                            .setPositiveButton("OK", null)
                            .show();
                    return;
                }

                StringBuilder history = new StringBuilder();
                int count = 1;
                for (DataSnapshot alert : snapshot.getChildren()) {
                    Long timestamp = alert.child("timestamp").getValue(Long.class);
                    String mapsLink = alert.child("mapsLink").getValue(String.class);
                    String status   = alert.child("status").getValue(String.class);

                    String time = timestamp != null
                            ? new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                            .format(new Date(timestamp))
                            : "Unknown time";

                    history.append("Alert #").append(count++).append("\n");
                    history.append("Time: ").append(time).append("\n");
                    history.append("Status: ").append(status).append("\n");
                    history.append("Location: ").append(
                            mapsLink != null && !mapsLink.equals("unavailable")
                                    ? "Location captured" : "Unavailable").append("\n\n");
                }

                new AlertDialog.Builder(SettingsActivity.this)
                        .setTitle("SOS History (" + (count-1) + " alerts)")
                        .setMessage(history.toString())
                        .setPositiveButton("OK", null)
                        .show();
            }
            @Override public void onCancelled(DatabaseError error) {
                Toast.makeText(SettingsActivity.this,
                        "Failed to load history", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showLogoutConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle("Log Out")
                .setMessage("Are you sure you want to log out?")
                .setPositiveButton("Log Out", (dialog, which) -> {
                    mAuth.signOut();
                    Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(this, LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}