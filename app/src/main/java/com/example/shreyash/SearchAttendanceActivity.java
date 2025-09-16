package com.example.shreyash;

import android.app.DatePickerDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.Manifest;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.database.*;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.*;

public class SearchAttendanceActivity extends AppCompatActivity {

    private TextView btnStartDate, btnEndDate;
    private Button btnFetchAttendance;
    private Spinner spinnerSubjectSearch;
    private EditText editTextRollNumber; // New EditText for roll number input
    private RecyclerView recyclerViewAttendanceResults;

    private DatabaseReference attendanceRef, rootRef; // Reference to Firebase Attendance data
    private List<AttendanceRecord> attendanceRecords = new ArrayList<>();
    private List<AttendanceRecord2> attendanceRecords2 = new ArrayList<>();
    private AttendanceRecordAdapter adapter;
    private static final int WRITE_EXTERNAL_STORAGE = 1;

    private String startDate, endDate, selectedSubject;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search_attendance);
        try {
            btnStartDate = findViewById(R.id.editTextStartDate);
            btnEndDate = findViewById(R.id.editTextEndDate);
            spinnerSubjectSearch = findViewById(R.id.spinnerSubjectSearch);
            btnFetchAttendance = findViewById(R.id.btnFetchAttendance);
            editTextRollNumber = findViewById(R.id.editTextRollNumber); // Initialize roll number input
            recyclerViewAttendanceResults = findViewById(R.id.recycler_view_attendance_results);
            FloatingActionButton btnGenerateExcel = findViewById(R.id.btnGenerateExcel);
            // Initialize Firebase reference
            attendanceRef = FirebaseDatabase.getInstance().getReference("Students123");
            rootRef = FirebaseDatabase.getInstance().getReference("Students123");

            // Populate subject spinner
            populateSubjects();

            // Set up RecyclerView
            recyclerViewAttendanceResults.setLayoutManager(new LinearLayoutManager(this));

            // Date selection using DatePickerDialog
            btnStartDate.setOnClickListener(view -> showDatePickerDialog(true));
            btnEndDate.setOnClickListener(view -> showDatePickerDialog(false));

            // Fetch Attendance Records
            btnFetchAttendance.setOnClickListener(view -> fetchAttendanceRecords());
            btnGenerateExcel.setOnClickListener(view -> generateExcelFile());
        }catch (Exception e) {
            Toast.makeText(SearchAttendanceActivity.this, "Unexpected error. Please try again.", Toast.LENGTH_LONG).show();
            Log.e("TallyAtt", "onCreate Search failed", e);
        }
    }
    private String[] lectureSubjects = {"DSA", "OOP","PME", "DELD", "BCN", "UHV", "Digital Marketing"};
    private String[] labSubjects = {"DSA Lab", "OOP Lab", "PME Lab", "CEP"};
    private void populateSubjects() {
        try {
            String[] subjects = {"DSA", "OOP", "DELD", "BCN", "PME" , "UHV", "DSA Lab", "OOP Lab", "PME Lab", "Digital Marketing", "CEP"};
            ArrayAdapter<String> subjectAdapter = new ArrayAdapter<>(this,
                    R.layout.custom_spinner_item,
                    subjects);

            spinnerSubjectSearch.setAdapter(subjectAdapter);
        }catch (Exception e) {
                Toast.makeText(SearchAttendanceActivity.this, "Unexpected error. Please try again.", Toast.LENGTH_LONG).show();
                Log.e("TallyAtt", "notifyAbsentStudents failed", e);
        }
    }

    private void showDatePickerDialog(boolean isStartDate) {
        try {
            Calendar calendar = Calendar.getInstance();
            int year = calendar.get(Calendar.YEAR);
            int month = calendar.get(Calendar.MONTH);
            int day = calendar.get(Calendar.DAY_OF_MONTH);

            DatePickerDialog datePickerDialog = new DatePickerDialog(
                    this,
                    (view, selectedYear, selectedMonth, selectedDay) -> {
                        String selectedDate = String.format(Locale.US, "%04d-%02d-%02d", selectedYear, selectedMonth + 1, selectedDay);
                        if (isStartDate) {
                            startDate = selectedDate;
                            btnStartDate.setText("Start Date: " + startDate);
                        } else {
                            endDate = selectedDate;
                            btnEndDate.setText("End Date: " + endDate);
                        }
                    },
                    year,
                    month,
                    day
            );
            datePickerDialog.show();
        }catch (Exception e) {
            Toast.makeText(SearchAttendanceActivity.this, "Unexpected error. Please try again.", Toast.LENGTH_LONG).show();
            Log.e("TallyAtt", "shoeDatePickerDialog failed", e);
        }
    }

    private void fetchAttendanceRecords() {
        try {
            if (startDate == null || endDate == null) {
                Toast.makeText(this, "Please select both dates", Toast.LENGTH_SHORT).show();
                return;
            }

            selectedSubject = (String) spinnerSubjectSearch.getSelectedItem();
            String rollNumber = "SIT" + editTextRollNumber.getText().toString().trim(); // Get roll number input


            attendanceRecords.clear(); // Clear previous records
//        attendanceRecords2.clear();
            attendanceRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (!snapshot.exists()) {
                        Toast.makeText(SearchAttendanceActivity.this, "No data found ", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    for (DataSnapshot studentSnapshot : snapshot.getChildren()) {
                        String enrollment = studentSnapshot.child("enrollment").getValue(String.class);
                        String name = studentSnapshot.child("name").getValue(String.class);


                        if (rollNumber.isEmpty() || enrollment.contains(rollNumber)) {
                            DataSnapshot subjectSnapshot = studentSnapshot.child("Subjects").child(selectedSubject);
                            if (!subjectSnapshot.exists()) {
                                Toast.makeText(SearchAttendanceActivity.this, "No data found ", Toast.LENGTH_SHORT).show();
                                return;
                            }
                            int present = subjectSnapshot.child("present_count").getValue(Integer.class);
                            int absent = subjectSnapshot.child("absent_count").getValue(Integer.class);
                            int totalLecture = present + absent;
                            int percentage = subjectSnapshot.child("percentage").getValue(Integer.class);
                            attendanceRecords2.add(new AttendanceRecord2(enrollment, name, selectedSubject, present, absent, totalLecture, percentage));


                            if (subjectSnapshot.exists()) {
                                DataSnapshot attendanceSnapshot = subjectSnapshot.child("Attendence");

                                for (DataSnapshot dateSnapshot : attendanceSnapshot.getChildren()) {
                                    String dateKey = dateSnapshot.getKey();


                                    DataSnapshot attendanceSnapshot2 = subjectSnapshot.child("Attendence").child(dateKey);

                                    for (DataSnapshot dateSnapshot2 : attendanceSnapshot2.getChildren()) {

                                        String time = dateSnapshot2.getKey();


                                        DataSnapshot attendanceSnapshot3 = subjectSnapshot.child("Attendence").child(dateKey).child(time);
                                        String status = attendanceSnapshot3.child("status").getValue(String.class);
                                        boolean inRange = isDateInRange(dateKey, startDate, endDate);


                                        if (inRange) {
                                            attendanceRecords.add(new AttendanceRecord(enrollment, status, dateKey + " ( " + time + " )", selectedSubject, name));
                                        }
                                    }
                                }
                            }
                        }
                    }

                    updateRecycler();
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Toast.makeText(SearchAttendanceActivity.this, "Failed to fetch data", Toast.LENGTH_SHORT).show();
                }
            });
        }catch (Exception e) {
            Toast.makeText(SearchAttendanceActivity.this, "Unexpected error. Please try again.", Toast.LENGTH_LONG).show();
            Log.e("TallyAtt", "fetchAttendenceRecord failed", e);
        }


    }

    private boolean isDateInRange(String dateKey, String startDate, String endDate) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            Date current = sdf.parse(dateKey);
            Date start = sdf.parse(startDate);
            Date end = sdf.parse(endDate);

            return current != null && !current.before(start) && !current.after(end);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private void updateRecycler() {
        if (adapter == null) {
            adapter = new AttendanceRecordAdapter(attendanceRecords);
            recyclerViewAttendanceResults.setAdapter(adapter);
        } else {
            adapter.notifyDataSetChanged();
        }
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        try {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            if (requestCode == 1) {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission granted, proceed with generating Excel file
                    generateExcelFile();
                } else {
                    // Permission denied, notify user
                    Toast.makeText(this, "Permission denied. Cannot save the file.", Toast.LENGTH_SHORT).show();
                }
            }
        }catch (Exception e) {
            Toast.makeText(SearchAttendanceActivity.this, "Unexpected error. Please try again.", Toast.LENGTH_LONG).show();
            Log.e("TallyAtt", "onReqPer failed", e);
        }
    }

    private void generateExcelFile() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            // For Android versions below 10 (API level 29), request permission
            saveFileScopedStorage(attendanceRecords2);
        }
    }
    private void saveFileScopedStorage(List<AttendanceRecord2> attendanceRecords2) {
        try {
            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("Attendance Summary");
            // Header 1
            Row header1 = sheet.createRow(0);
            header1.createCell(0).setCellValue("Roll No");
            header1.createCell(1).setCellValue("Name");
            header1.createCell(2).setCellValue("DSA");
            header1.createCell(3).setCellValue("OOP");
            header1.createCell(4).setCellValue("DELD");
            header1.createCell(5).setCellValue("BCN");
            header1.createCell(6).setCellValue("UHV");
            header1.createCell(7).setCellValue("PME");
            header1.createCell(8).setCellValue("Digital Marketing");
            header1.createCell(9).setCellValue("DSA Lab");
            header1.createCell(10).setCellValue("OOP Lab");
            header1.createCell(11).setCellValue("PME Lab");
            header1.createCell(12).setCellValue("CEP");
            header1.createCell(13).setCellValue("Total Lectures");
            header1.createCell(14).setCellValue("Total Present");
            header1.createCell(15).setCellValue("Aggregrate");

            // Header 2 (sub-columns)
            Row header2 = sheet.createRow(1);
            for (int i = 2; i <= 15; i += 1) {
                header2.createCell(i).setCellValue("");
            }
//            Cell cell = header2.getCell(2);   // column index 2 (Lectures)
//            String lectureText = cell.getStringCellValue();

            // Step 1: Group by student
            Map<String, Map<String, AttendanceRecord2>> studentMap = new LinkedHashMap<>();

            for (AttendanceRecord2 record : attendanceRecords2) {
                String key = record.getEnrollmentNumber() + "_" + record.getName();
                studentMap.putIfAbsent(key, new HashMap<>());
                studentMap.get(key).put(record.getSubject(), record);
            }

            // Subject column mapping
            Map<String, Integer> subjectColumnMap = new HashMap<>();

            subjectColumnMap.put("DSA", 2);
            subjectColumnMap.put("OOP", 5);
            subjectColumnMap.put("DELD", 8);
            subjectColumnMap.put("BCN", 11);
            subjectColumnMap.put("UHV", 14);
            subjectColumnMap.put("PME", 17);
            subjectColumnMap.put("Digital Marketing", 20);
            subjectColumnMap.put("DSA Lab", 23);
            subjectColumnMap.put("OOP Lab", 26);
            subjectColumnMap.put("PME Lab", 29);
            subjectColumnMap.put("CEP", 32);

            int rowIndex = 1;
            for (String studentKey : studentMap.keySet()) {
                String[] parts = studentKey.split("_");
                String enrollment = parts[0];
                String name = parts[1];

                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(enrollment);
                row.createCell(1).setCellValue(name);

                int totalLec = 0;
                int totalPre = 0;
                float aggregate = 0;

                Map<String, AttendanceRecord2> subjectMap = studentMap.get(studentKey);

                for (String subject : subjectMap.keySet()) {
                    AttendanceRecord2 rec = subjectMap.get(subject);
                    int col = subjectColumnMap.getOrDefault(subject, -1);

                    if (col != -1) {
                        row.createCell(col).setCellValue(rec.getPresentCount());
//                        row.createCell(col + 1).setCellValue(rec.getPresentCount());
//                        row.createCell(col + 2).setCellValue(rec.getPercentage());

                        totalLec += rec.getTotalLecture();
                        totalPre += rec.getPresentCount();
                    }
                }

                aggregate = ((100 * totalPre) / totalLec);

                row.createCell(35).setCellValue(totalLec);
                row.createCell(37).setCellValue(totalPre);
                row.createCell(49).setCellValue(aggregate);
            }

            try {
                FileOutputStream fileOut = new FileOutputStream(new File(getFilesDir(), "attendance_records.xlsx"));
                workbook.write(fileOut);
                fileOut.close();
                try {
                    // For Android 10+ (API 29+)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        ContentValues values = new ContentValues();
                        values.put(MediaStore.MediaColumns.DISPLAY_NAME, "attendance_records.xlsx");
                        values.put(MediaStore.MediaColumns.MIME_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                        values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

                        Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                        if (uri != null) {
                            OutputStream stream = getContentResolver().openOutputStream(uri);
                            workbook.write(stream);
                            stream.close();
                            Toast.makeText(this, "File saved to Downloads folder", Toast.LENGTH_SHORT).show();

                            // Optionally open the file
                            Intent intent = new Intent(Intent.ACTION_VIEW);
                            intent.setDataAndType(uri, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            startActivity(intent);
                        }
                    } else {
                        // For older versions
                        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                        File file = new File(downloadsDir, "attendance_records.xlsx");

                        FileOutputStream fileOut1 = new FileOutputStream(file);
                        workbook.write(fileOut1);
                        fileOut1.close();

                        Toast.makeText(this, "File saved to Downloads folder", Toast.LENGTH_SHORT).show();

                        // Optionally open the file
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setDataAndType(Uri.fromFile(file), "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                        startActivity(intent);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    Toast.makeText(this, "Failed to save file", Toast.LENGTH_SHORT).show();
                }
                Toast.makeText(this, "Excel file generated successfully!", Toast.LENGTH_SHORT).show();

            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(this, "Failed to generate Excel file", Toast.LENGTH_SHORT).show();
            }
        }catch (Exception e) {
            Toast.makeText(SearchAttendanceActivity.this, "Unexpected error. Please try again.", Toast.LENGTH_LONG).show();
            Log.e("TallyAtt", "saveFileStorage failed", e);
        }
    }



}
