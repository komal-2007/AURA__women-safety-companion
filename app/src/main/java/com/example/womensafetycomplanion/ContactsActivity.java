package com.example.womensafetycomplanion;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ContactsActivity extends AppCompatActivity {

    EditText etName, etPhone;
    Button btnAddContact;
    ListView lvContacts;
    FirebaseFirestore db;
    String userId;
    List<String> contactDisplay = new ArrayList<>();
    ArrayAdapter<String> adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contacts);

        db = FirebaseFirestore.getInstance();
        userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        etName = findViewById(R.id.etName);
        etPhone = findViewById(R.id.etPhone);
        btnAddContact = findViewById(R.id.btnAddContact);
        lvContacts = findViewById(R.id.lvContacts);

        // Setup list adapter
        adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, contactDisplay);
        lvContacts.setAdapter(adapter);

        // Load existing contacts
        loadContacts();

        // Add contact button
        btnAddContact.setOnClickListener(v -> addContact());

        // Long press to delete a contact
        lvContacts.setOnItemLongClickListener((parent, view, position, id) -> {
            deleteContact(position);
            return true;
        });
    }

    private void addContact() {
        String name = etName.getText().toString().trim();
        String phone = etPhone.getText().toString().trim();

        if (name.isEmpty() || phone.isEmpty()) {
            Toast.makeText(this, "Please fill both fields", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!phone.startsWith("+")) {
            Toast.makeText(this, "Phone must start with country code e.g. +91", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Object> contact = new HashMap<>();
        contact.put("name", name);
        contact.put("phone", phone);

        db.collection("users").document(userId)
                .collection("contacts")
                .add(contact)
                .addOnSuccessListener(ref -> {
                    Toast.makeText(this, name + " added!", Toast.LENGTH_SHORT).show();
                    etName.setText("");
                    etPhone.setText("");
                    loadContacts();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void loadContacts() {
        db.collection("users").document(userId)
                .collection("contacts")
                .get()
                .addOnSuccessListener(snapshot -> {
                    contactDisplay.clear();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        String entry = doc.getString("name") + "\n" + doc.getString("phone");
                        contactDisplay.add(entry);
                    }
                    adapter.notifyDataSetChanged();
                    if (contactDisplay.isEmpty()) {
                        Toast.makeText(this, "No contacts yet. Add one above!", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void deleteContact(int position) {
        db.collection("users").document(userId)
                .collection("contacts")
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<DocumentSnapshot> docs = snapshot.getDocuments();
                    if (position < docs.size()) {
                        docs.get(position).getReference().delete()
                                .addOnSuccessListener(unused -> {
                                    Toast.makeText(this, "Contact deleted", Toast.LENGTH_SHORT).show();
                                    loadContacts();
                                });
                    }
                });
    }
}