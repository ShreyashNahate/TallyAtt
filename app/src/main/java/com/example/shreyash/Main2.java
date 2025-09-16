package com.example.shreyash;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.telephony.SmsManager;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.airbnb.lottie.LottieAnimationView;
import com.airbnb.lottie.LottieComposition;
import com.airbnb.lottie.LottieCompositionFactory;
import com.airbnb.lottie.LottieDrawable;
import com.airbnb.lottie.OnCompositionLoadedListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.*;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

public class Main2 extends AppCompatActivity {
    private boolean isAllSelected = false;


    // Added spinnerBatch
    private Spinner spinnerAddStudent, spinnerType, spinnerSubject, spinnerBatch;
    private EditText editTextName, editTextEnrollment, editTextMobile, editTextEmail;
    private TextView selectALL;
    private Button btnAddStudent, btnPickStartTime, btnPickEndTime, btnNotify, btnSearchAttendance;
    private ListView listViewStudents;
    private LinearLayout studentDetailsLayout;

    private DatabaseReference tpdb2 = FirebaseDatabase.getInstance().getReference("Students123");
    private DatabaseReference tpdb1 = FirebaseDatabase.getInstance().getReference("Students123");
    private List<String> studentList = new ArrayList<>();
    private ArrayAdapter<String> studentAdapter;

    public static final ArrayList<String> absentMobileList = new ArrayList<>();
    public static final ArrayList<String> absentEmailList = new ArrayList<>();

    private String selectedType = "Lecture", selectedSubject = "", startTime = "", endTime = "";
    private String[] lectureSubjects = {"DSA", "OOP", "DELD", "BCN", "UHV", "PME", "Digital Marketing"};
    private String[] labSubjects = {"DSA Lab", "OOP Lab", "PME Lab", "CEP"};
    private String[] batchOptions = {"Batch 1", "Batch 2", "Batch 3"};

    private static final String MESSAGE = "Hello , You are Absent for Today's Lecture . Kindly Report to the TG immediately";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main2);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.SEND_SMS}, 1);
        }
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

        // New spinner for batch selection - you must add this to your main2.xml layout
        spinnerBatch = findViewById(R.id.spinnerBatch);

        spinnerType = findViewById(R.id.spinnerType);
        spinnerSubject = findViewById(R.id.spinnerSubject);
        listViewStudents = findViewById(R.id.listViewStudents);
        studentDetailsLayout = findViewById(R.id.studentDetailsLayout);
        btnPickStartTime = findViewById(R.id.btnPickStartTime);
        btnPickEndTime = findViewById(R.id.btnPickEndTime);
        btnNotify = findViewById(R.id.btnNotify);
        btnSearchAttendance = findViewById(R.id.btnSearchAttendance);
        selectALL = findViewById(R.id.all);
        ImageView checkView = findViewById(R.id.checkView);
        loadStudents("All");
        SeekBar fontSeekBar = findViewById(R.id.fontSeekBar);
        TextView fontSizeLabel = findViewById(R.id.fontSizeLabel);

// Load saved font size
        int savedFontSize = getSharedPreferences("settings", MODE_PRIVATE).getInt("fontSize", 16);
        fontSeekBar.setProgress(savedFontSize);
        adjustFontSize(((ViewGroup) findViewById(android.R.id.content)).getChildAt(0), savedFontSize);

        fontSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (progress < 12) progress = 12; // minimum readable size
                adjustFontSize(((ViewGroup) findViewById(android.R.id.content)).getChildAt(0), progress);
                fontSizeLabel.setText("Font Size: " + progress + "sp");

                // Save preference
                getSharedPreferences("settings", MODE_PRIVATE)
                        .edit()
                        .putInt("fontSize", progress)
                        .apply();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });


        selectALL.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!isAllSelected) {
                    for (int i = 0; i < studentAdapter.getCount(); i++) {
                        listViewStudents.setItemChecked(i, true);
                    }
                    isAllSelected = true;
                } else {
                    for (int i = 0; i < studentAdapter.getCount(); i++) {
                        listViewStudents.setItemChecked(i, false);
                    }
                    isAllSelected = false;
                }
            }
        });

// When the button is clicked, you can now simply play the animation
        btnNotify.setOnClickListener(view -> {

            notifyAbsentStudents();
        });
        updateSubjectSpinner();

        String[] typeOptions = {"Lecture", "Lab"};
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, typeOptions);
        spinnerType.setAdapter(typeAdapter);

        spinnerType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedType = typeOptions[position];
                updateSubjectSpinner();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        btnPickStartTime.setOnClickListener(view -> pickTime(btnPickStartTime, true));
        btnPickEndTime.setOnClickListener(view -> pickTime(btnPickEndTime, false));

        btnSearchAttendance.setOnClickListener(view -> {
            Intent intent = new Intent(Main2.this, SearchAttendanceActivity.class);
            startActivity(intent);
        });
    }

    private void notifyAbsentStudents() {
        try {

            btnNotify.setEnabled(false);
            FrameLayout overlay = findViewById(R.id.overlay);
            ImageView checkView = findViewById(R.id.checkView);

            overlay.setVisibility(View.VISIBLE);
            checkView.setVisibility(View.VISIBLE);

// reset
            checkView.setScaleX(0f);
            checkView.setScaleY(0f);
            checkView.setAlpha(0f);

// animate pop + fade
            checkView.animate()
                    .scaleX(1f).scaleY(1f)
                    .alpha(1f)
                    .setDuration(300)
                    .withEndAction(() -> checkView.animate()
                            .alpha(0f)
                            .setDuration(200)
                            .withEndAction(() -> {
                                checkView.setVisibility(View.GONE);
                                overlay.setVisibility(View.GONE);
                            })
                    );

            SparseBooleanArray checkedItems = listViewStudents.getCheckedItemPositions();
            absentEmailList.clear();
            absentMobileList.clear();

            String date = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

            AtomicInteger totalAbsentStudents = new AtomicInteger(0);
            AtomicInteger processedStudents = new AtomicInteger(0);

            for (int i = 0; i < listViewStudents.getCount(); i++) {
                String studentData = studentList.get(i);
                String enrollment = studentData.split(" - ")[1];

                boolean isAbsent = checkedItems.get(i);
                String status = isAbsent ? "Absent" : "Present";

                if (!"Present".equals(status)) {
                    totalAbsentStudents.incrementAndGet();
                    tpdb1.child(enrollment).addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            if (snapshot.exists()) {
                                String mobile = snapshot.child("mobile").getValue(String.class);
                                String email = snapshot.child("email").getValue(String.class);
                                absentMobileList.add(mobile);
                                absentEmailList.add(email);
                            }
                            if (processedStudents.incrementAndGet() == totalAbsentStudents.get()) {
                                allDataFetched();
                                btnNotify.setEnabled(true);
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            processedStudents.incrementAndGet();
                        }
                    });
                }
                startTime = btnPickStartTime.getText().toString();
                endTime = btnPickEndTime.getText().toString();
                String timestamp = startTime + endTime;
                Map<String, Object> attendanceMap = new HashMap<>();
                attendanceMap.put("status", status);
                attendanceMap.put("timestamp", timestamp);
                tpdb1.child(enrollment).child("Subjects").child(selectedSubject).child("Attendence").child(date).child(startTime + " - " + endTime).setValue(attendanceMap);
                tpdb2.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        long present = 0, absent = 0;
                        DataSnapshot subjectSnap = snapshot.child(enrollment)
                                .child("Subjects")
                                .child(selectedSubject);
                        if (subjectSnap.child("present_count").exists()) {
                            present = subjectSnap.child("present_count").getValue(Long.class);
                        } else {
                            tpdb2.child(enrollment)
                                    .child("Subjects")
                                    .child(selectedSubject)
                                    .child("present_count")
                                    .setValue(present);
                        }
                        if (subjectSnap.child("absent_count").exists()) {
                            absent = subjectSnap.child("absent_count").getValue(Long.class);
                        } else {
                            tpdb2.child(enrollment)
                                    .child("Subjects")
                                    .child(selectedSubject)
                                    .child("absent_count")
                                    .setValue(absent);
                        }
                        if (status.equals("Present")) {
                            present += 1;
                            tpdb2.child(enrollment)
                                    .child("Subjects")
                                    .child(selectedSubject)
                                    .child("present_count")
                                    .setValue(present);
                        } else {
                            absent += 1;
                            tpdb2.child(enrollment)
                                    .child("Subjects")
                                    .child(selectedSubject)
                                    .child("absent_count")
                                    .setValue(absent);
                        }
                        long total = present + absent;
                        double percentage = (total > 0) ? (present * 100.0) / total : 0;
                        tpdb2.child(enrollment)
                                .child("Subjects")
                                .child(selectedSubject)
                                .child("percentage")
                                .setValue(percentage);
                    }
                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        btnNotify.setEnabled(true);
                        Log.e("FIREBASE", "Error updating count: " + error.getMessage());
                    }
                });
            }
            Toast.makeText(Main2.this, "Notified Students", Toast.LENGTH_SHORT).show();

//            lottieAnimationView.setVisibility(View.GONE);
        }catch (Exception e) {
            Toast.makeText(Main2.this, "Unexpected error. Please try again.", Toast.LENGTH_LONG).show();
            Log.e("TallyAtt", "notifyAbsentStudents failed", e);
            btnNotify.setEnabled(true);
        }
    }

    private void updateSubjectSpinner() {
        try {
            String[] subjects = selectedType.equals("Lecture") ? lectureSubjects : labSubjects;
            ArrayAdapter<String> subjectAdapter =
                    new ArrayAdapter<>(this,
                            android.R.layout.simple_spinner_dropdown_item,
                            subjects);

            spinnerSubject.setAdapter(subjectAdapter);

            if (selectedType.equals("Lab")) {
                spinnerBatch.setVisibility(View.VISIBLE);
                ArrayAdapter<String> batchAdapter = new ArrayAdapter<>(this,
                        android.R.layout.simple_spinner_dropdown_item, batchOptions);
                spinnerBatch.setAdapter(batchAdapter);

                // Immediately load students for the default batch when "Lecture" is selected
                loadStudents("Batch 1");

                spinnerBatch.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                        String selectedBatch = parent.getItemAtPosition(position).toString();
                        loadStudents(selectedBatch);
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {}
                });

            } else {
                spinnerBatch.setVisibility(View.GONE);
                loadStudents("All");
            }

            spinnerSubject.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent,
                                           View view,
                                           int position,
                                           long id) {
                    selectedSubject = subjects[position];
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                }
            });
        }catch (Exception e) {
            Toast.makeText(Main2.this, "Unexpected error. Please try again.", Toast.LENGTH_LONG).show();
            Log.e("TallyAtt", "updateSubjectSpinner failed", e);
        }
    }

    private void pickTime(Button button, boolean isStartTime) {
        try {
            Calendar calendar = Calendar.getInstance();
            int hour = calendar.get(Calendar.HOUR_OF_DAY);
            int minute = calendar.get(Calendar.MINUTE);

            TimePickerDialog timePickerDialog = new TimePickerDialog(this,
                    (view, hourOfDay, min) -> {
                        String amPm = (hourOfDay < 12) ? "AM" : "PM";
                        int hour12 = (hourOfDay == 0) ? 12 : (hourOfDay > 12 ? hourOfDay - 12 : hourOfDay);
                        String time = String.format("%02d:%02d %s", hour12, min, amPm);

                        if (isStartTime) {
                            button.setText("From : " + time);
                            startTime = time;
                        } else {
                            button.setText("To : " + time);
                            endTime = time;
                        }
                    }, hour, minute, false);

            timePickerDialog.show();
        }catch (Exception e) {
            Toast.makeText(Main2.this, "Unexpected error. Please try again.", Toast.LENGTH_LONG).show();
            Log.e("TallyAtt", "picktime failed", e);
        }
    }

    private void loadStudents(String selectedBatch) {
        try {
            tpdb1.addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    studentList.clear();
                    for (DataSnapshot studentSnapshot : snapshot.getChildren()) {
                        Student student = studentSnapshot.getValue(Student.class);
                        if (student != null && student.getEnrollment() != null) {

                            int enrollmentNumber = 0;
                            try {
                                // Extract the numeric part from "SIT01"
                                String enrollmentPart = student.getEnrollment().substring(3);
                                enrollmentNumber = Integer.parseInt(enrollmentPart);
                            } catch (Exception e) {
                                Log.e("LoadStudents", "Invalid enrollment format: " + student.getEnrollment());
                                continue;
                            }

                            boolean isBatch1 = selectedBatch.equals("Batch 1") && enrollmentNumber >= 1 && enrollmentNumber <= 26;
                            boolean isBatch2 = selectedBatch.equals("Batch 2") && enrollmentNumber >= 27 && enrollmentNumber <= 52;
                            boolean isBatch3 = selectedBatch.equals("Batch 3") && enrollmentNumber >= 53 && enrollmentNumber <= 77;

                            if (selectedBatch.equals("All") || isBatch1 || isBatch2 || isBatch3) {
                                studentList.add(student.getName() + " - " + student.getEnrollment());
                            }
                        }
                    }

                    studentAdapter = new ArrayAdapter<>(Main2.this,
                            android.R.layout.simple_list_item_multiple_choice, studentList);
                    listViewStudents.setAdapter(studentAdapter);
                    listViewStudents.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Toast.makeText(Main2.this, "Failed to load students", Toast.LENGTH_SHORT).show();
                }
            });
        }catch (Exception e) {
            Toast.makeText(Main2.this, "Unexpected error. Please try again.", Toast.LENGTH_LONG).show();
            Log.e("TallyAtt", "LoadStudent failed", e);
        }
    }
    private void adjustFontSize(View view, float size) {
        if (view instanceof ViewGroup) {
            for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
                adjustFontSize(((ViewGroup) view).getChildAt(i), size);
            }
        } else if (view instanceof TextView) {
            ((TextView) view).setTextSize(size);
        }
    }


    private void sendSmsToAll() {
        for (String phoneNumber : absentMobileList) {
            try {
                SmsManager smsManager = null;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.DONUT) {
                    smsManager = SmsManager.getDefault();
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.DONUT) {
                    smsManager.sendTextMessage(phoneNumber, null, MESSAGE, null, null);
                }
            } catch (Exception e) {
                Toast.makeText(this, "SMS Failed for: " + phoneNumber, Toast.LENGTH_SHORT).show();
                e.printStackTrace();
            }
        }
    }

    private void allDataFetched() {
        CheckBox checkboxEmail = findViewById(R.id.checkbox_email);
        CheckBox checkboxMessage = findViewById(R.id.checkbox_message);



        if (absentEmailList.isEmpty()) {
            Toast.makeText(Main2.this, "No students selected for email!", Toast.LENGTH_SHORT).show();
            return;
        }

        if (checkboxEmail.isChecked() && checkboxMessage.isChecked()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CUPCAKE) {
                new SendMailTask(absentEmailList).execute();
            }
            sendSmsToAll();
            Toast.makeText(Main2.this, "Sending Email and Message", Toast.LENGTH_SHORT).show();
        } else if (checkboxEmail.isChecked()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CUPCAKE) {
                new SendMailTask(absentEmailList).execute();
            }
            Toast.makeText(Main2.this, "Sending Email", Toast.LENGTH_SHORT).show();
        } else if (checkboxMessage.isChecked()) {
            sendSmsToAll();
            Toast.makeText(Main2.this, "Sending Message", Toast.LENGTH_SHORT).show();
        } else {
            handleSendAction(checkboxEmail, checkboxMessage);
        }
    }

    public void handleSendAction(CheckBox checkboxEmail , CheckBox checkboxMessage) {
        if (checkboxEmail.isChecked() && checkboxMessage.isChecked()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CUPCAKE) {
                new SendMailTask(absentEmailList).execute();
            }
            sendSmsToAll();
            Toast.makeText(Main2.this, "Sending Email and Message", Toast.LENGTH_SHORT).show();
        } else if (checkboxEmail.isChecked()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CUPCAKE) {
                new SendMailTask(absentEmailList).execute();
            }
            Toast.makeText(Main2.this, "Sending Email", Toast.LENGTH_SHORT).show();
        } else if (checkboxMessage.isChecked()) {
            sendSmsToAll();
            Toast.makeText(Main2.this, "Sending Message", Toast.LENGTH_SHORT).show();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.CUPCAKE)
    public class SendMailTask extends AsyncTask<Void ,Void ,Void>{

        private String userEmail="itdit2025@gmail.com";
        private String userPassword="vxhf stmi keci lbsf";
        private List<String> emailList;

        public SendMailTask(List<String> selectedEmails){
            this.emailList= absentEmailList;
        }

        @Override
        protected Void doInBackground(Void... voids){
            try {
                if (!emailList.isEmpty()) {
                    sendEmailsInParallel();
                } else {
                    Log.e("SendMailTask", "No student emails selected.");
                }
            }catch (Exception e) {
                Toast.makeText(Main2.this, "Unexpected error. Please try again.", Toast.LENGTH_LONG).show();
                Log.e("TallyAtt", "SentMailTask failed", e);
            }
            return null;
        }

        private void sendEmailsInParallel(){
            Log.d("SendMailTask" ,"Preparing to send emails...");
            try{
                Properties props= new Properties();
                props.put("mail.smtp.auth" ,"true");
                props.put("mail.smtp.starttls.enable" ,"true");
                props.put("mail.smtp.host" ,"smtp.gmail.com");
                props.put("mail.smtp.port" ,"587");
                Session session= Session.getInstance(props,new Authenticator(){
                    protected PasswordAuthentication getPasswordAuthentication(){
                        return new PasswordAuthentication(userEmail,userPassword);
                    }
                });
                Thread emailThread= new Thread(() -> {
                    try{
                        Message message= new MimeMessage(session);
                        try{
                            message.setFrom(new InternetAddress(userEmail,"IT Department"));
                        }catch(UnsupportedEncodingException e){
                            Log.e("SendMailTask" ,"Encoding error in sender name" ,e);
                            message.setFrom(new InternetAddress(userEmail));
                        }
                        message.setReplyTo(InternetAddress.parse("support@yourcollege.com"));
                        for(String email: emailList){
                            message.addRecipient(Message.RecipientType.BCC,new InternetAddress(email));
                        }
                        message.setHeader("Content-Type","text/html; charset=UTF-8");
                        message.setHeader("X-Priority","1");
                        message.setHeader("X-Mailer","AndroidMailer");
                        message.setHeader("Precedence","bulk");
                        String emailBody="<h2>Hello Student ,<h2>" + "<p>This is an <strong>official announcement</strong> from your college.</p>" + "<p>You are Absent for Today's " +selectedSubject+" class . Kindly Report to Your TG immediately.</p>" + "<br><p>Regards,<br><strong>Your College</strong></p>";
                        message.setSubject("📢 Important College Notice");
                        message.setContent(emailBody,"text/html; charset=utf-8");
                        Transport.send(message);
                        Log.d("SendMailTask" ,"✅ Email sent successfully!");
                    }catch(MessagingException e){
                        Log.e("SendMailTask" ,"❌ Error sending email" ,e);
                    }
                });
                emailThread.start();
            }catch(Exception e){
                Log.e("SendMailTask" ,"❌ Error preparing email" ,e);
            }
        }
    }
}