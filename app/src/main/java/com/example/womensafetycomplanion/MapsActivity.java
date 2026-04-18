package com.example.womensafetycomplanion;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import java.util.HashMap;
import java.util.Map;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {

    GoogleMap mMap;
    FusedLocationProviderClient fusedClient;
    LocationCallback locationCallback;
    DatabaseReference locationRef;
    String userId;
    boolean isSharingLocation = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        locationRef = FirebaseDatabase.getInstance().getReference("locations/" + userId);
        fusedClient = LocationServices.getFusedLocationProviderClient(this);

        SupportMapFragment mapFragment = (SupportMapFragment)
                getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        Button btnStop = findViewById(R.id.btnStopSharing);
        btnStop.setOnClickListener(v -> {
            if (isSharingLocation) {
                stopSharingLocation();
                btnStop.setText("Start Sharing");
                isSharingLocation = false;
                Toast.makeText(this, "Location sharing stopped", Toast.LENGTH_SHORT).show();
            } else {
                startSharingLocation();
                btnStop.setText("Stop Sharing");
                isSharingLocation = true;
                Toast.makeText(this, "Location sharing started", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
        }
        startSharingLocation();
    }

    private void startSharingLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Location permission not granted", Toast.LENGTH_SHORT).show();
            return;
        }

        LocationRequest request = new LocationRequest.Builder(5000)
                .setMinUpdateIntervalMillis(3000)
                .build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult result) {
                Location loc = result.getLastLocation();
                if (loc != null) {
                    LatLng position = new LatLng(loc.getLatitude(), loc.getLongitude());

                    // Update map
                    mMap.clear();
                    mMap.addMarker(new MarkerOptions()
                            .position(position)
                            .title("My Location"));
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(position, 16f));

                    // Save to Firebase Realtime DB
                    Map<String, Object> data = new HashMap<>();
                    data.put("lat", loc.getLatitude());
                    data.put("lng", loc.getLongitude());
                    data.put("timestamp", System.currentTimeMillis());
                    locationRef.setValue(data);
                }
            }
        };

        fusedClient.requestLocationUpdates(request, locationCallback, getMainLooper());
    }

    private void stopSharingLocation() {
        if (locationCallback != null) {
            fusedClient.removeLocationUpdates(locationCallback);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopSharingLocation();
    }
}