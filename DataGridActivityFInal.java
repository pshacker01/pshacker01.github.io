package com.example.cs360project2_mccormack;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashMap;

public class DataGridActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private DataAdapter adapter;
    private ArrayList<DataItem> dataList;
    private HashMap<Integer, DataItem> dataMap; // O(1) lookup by ID
    private DatabaseHelper dbHelper;

    private EditText editTitle, editDescription;
    private Button buttonAdd, buttonSort;
    private static final int SMS_PERMISSION_CODE = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_data_grid);

        dbHelper = new DatabaseHelper(this);
        dataList = dbHelper.getAllData();
        dataMap = new HashMap<>();
        buildHashMap(); // Initialize HashMap for O(1) lookups

        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new DataAdapter(dataList, this::deleteItem);
        recyclerView.setAdapter(adapter);

        editTitle = findViewById(R.id.editTitle);
        editDescription = findViewById(R.id.editDescription);
        buttonAdd = findViewById(R.id.buttonAdd);
        buttonSort = findViewById(R.id.buttonSort);

        // Request SMS permission IMMEDIATELY after login
        checkSmsPermission();

        // Sort button click listener - uses MergeSort O(n log n)
        buttonSort.setOnClickListener(v -> {
            mergeSort(dataList, 0, dataList.size() - 1);
            adapter.notifyDataSetChanged();
            Toast.makeText(this, "Items sorted by title (MergeSort)", Toast.LENGTH_SHORT).show();
        });

        buttonAdd.setOnClickListener(v -> {
            String title = editTitle.getText().toString().trim();
            String description = editDescription.getText().toString().trim();

            if (!title.isEmpty() && !description.isEmpty()) {
                dbHelper.addData(title, description);
                refreshDataStructures(); // Sync both ArrayList and HashMap
                adapter.notifyDataSetChanged();

                Toast.makeText(this, "Data added successfully!", Toast.LENGTH_SHORT).show();

                // Send SMS notification only if permission is granted
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                        == PackageManager.PERMISSION_GRANTED) {
                    sendSmsNotification();
                }
            } else {
                Toast.makeText(this, "Please enter all fields", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void deleteItem(DataItem item) {
        dbHelper.deleteData(item.getId());
        dataMap.remove(item.getId()); // O(1) removal from HashMap
        refreshDataStructures(); // Sync both ArrayList and HashMap
        adapter.notifyDataSetChanged();
    }

    private void checkSmsPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.SEND_SMS},
                    SMS_PERMISSION_CODE
            );
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == SMS_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "SMS permission granted!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "SMS permission denied. Notifications disabled.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void sendSmsNotification() {
        try {
            SmsManager smsManager = SmsManager.getDefault();
            smsManager.sendTextMessage("1234567890", null, "Data added successfully!", null, null);
            Toast.makeText(this, "Notification sent via SMS", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Failed to send SMS: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // ==================== ALGORITHM ENHANCEMENTS ====================

    /**
     * Builds the HashMap from the ArrayList for O(1) lookups.
     * Called during initialization and after data refresh.
     * Time Complexity: O(n) - one-time cost for O(1) future lookups
     */
    private void buildHashMap() {
        dataMap.clear();
        for (DataItem item : dataList) {
            dataMap.put(item.getId(), item);
        }
    }

    /**
     * Refreshes both data structures from the database.
     * Maintains synchronization between ArrayList (for display) and HashMap (for lookup).
     */
    private void refreshDataStructures() {
        dataList.clear();
        dataList.addAll(dbHelper.getAllData());
        buildHashMap();
    }

    /**
     * O(1) lookup by ID using HashMap.
     * Compared to ArrayList iteration which is O(n), this provides constant-time access.
     *
     * @param id The unique identifier of the item
     * @return The DataItem if found, null otherwise
     */
    public DataItem findItemById(int id) {
        return dataMap.get(id); // O(1) average case
    }

    // ==================== MERGESORT IMPLEMENTATION ====================

    /**
     * Stable MergeSort algorithm for sorting DataItems by title.
     * Time Complexity: O(n log n)
     * Space Complexity: O(n)
     * Stability: Preserves relative order of equal elements
     *
     * @param list The ArrayList to sort
     * @param left The starting index
     * @param right The ending index
     */
    private void mergeSort(ArrayList<DataItem> list, int left, int right) {
        if (left < right) {
            int mid = left + (right - left) / 2;

            // Recursively sort both halves
            mergeSort(list, left, mid);
            mergeSort(list, mid + 1, right);

            // Merge the sorted halves
            merge(list, left, mid, right);
        }
    }

    /**
     * Merges two sorted subarrays into a single sorted array.
     * This is the key operation that makes MergeSort stable.
     *
     * @param list The ArrayList containing the subarrays
     * @param left The starting index of the first subarray
     * @param mid The ending index of the first subarray
     * @param right The ending index of the second subarray
     */
    private void merge(ArrayList<DataItem> list, int left, int mid, int right) {
        // Calculate sizes of temporary arrays
        int n1 = mid - left + 1;
        int n2 = right - mid;

        // Create temporary arrays
        ArrayList<DataItem> leftArray = new ArrayList<>(n1);
        ArrayList<DataItem> rightArray = new ArrayList<>(n2);

        // Copy data to temporary arrays
        for (int i = 0; i < n1; i++) {
            leftArray.add(list.get(left + i));
        }
        for (int j = 0; j < n2; j++) {
            rightArray.add(list.get(mid + 1 + j));
        }

        // Merge the temporary arrays back
        int i = 0, j = 0, k = left;

        while (i < n1 && j < n2) {
            // Compare by title (case-insensitive) - stable: use <= for left priority
            if (leftArray.get(i).getTitle().compareToIgnoreCase(rightArray.get(j).getTitle()) <= 0) {
                list.set(k, leftArray.get(i));
                i++;
            } else {
                list.set(k, rightArray.get(j));
                j++;
            }
            k++;
        }

        // Copy remaining elements of leftArray
        while (i < n1) {
            list.set(k, leftArray.get(i));
            i++;
            k++;
        }

        // Copy remaining elements of rightArray
        while (j < n2) {
            list.set(k, rightArray.get(j));
            j++;
            k++;
        }
    }
}
