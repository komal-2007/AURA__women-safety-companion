package com.example.womensafetycomplanion;

import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class FakeCallActivity extends AppCompatActivity {

    MediaPlayer ringtone, fakeVoice;
    TextView tvStatus;
    CountDownTimer autoAnswerTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fake_call);

        tvStatus = findViewById(R.id.tvStatus);

        // Play default phone ringtone (no need for audio file)
        Uri ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
        ringtone = MediaPlayer.create(this, ringtoneUri);
        ringtone.setLooping(true);
        ringtone.start();

        // Auto-answer after 8 seconds
        autoAnswerTimer = new CountDownTimer(8000, 1000) {
            public void onFinish() {
                answerCall();
            }
            public void onTick(long ms) {
                tvStatus.setText("Ringing... (" + (ms / 1000 + 1) + "s)");
            }
        }.start();

        // Answer button
        Button btnAnswer = findViewById(R.id.btnAnswer);
        btnAnswer.setOnClickListener(v -> answerCall());

        // Decline button
        Button btnDecline = findViewById(R.id.btnDecline);
        btnDecline.setOnClickListener(v -> {
            if (autoAnswerTimer != null) autoAnswerTimer.cancel();
            finish();
        });
    }

    private void answerCall() {
        if (autoAnswerTimer != null) autoAnswerTimer.cancel();
        if (ringtone != null) {
            ringtone.stop();
            ringtone.release();
            ringtone = null;
        }
        tvStatus.setText("On call with Mom...");

        // Play notification sound as fake voice (or add your own mp3 in res/raw/)
        Uri callSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        fakeVoice = MediaPlayer.create(this, callSound);
        fakeVoice.setLooping(true);
        fakeVoice.start();

        // End call after 60 seconds automatically
        new CountDownTimer(60000, 1000) {
            public void onFinish() { finish(); }
            public void onTick(long ms) {
                tvStatus.setText("On call... " + (ms / 1000) + "s");
            }
        }.start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (ringtone != null) { ringtone.release(); ringtone = null; }
        if (fakeVoice != null) { fakeVoice.release(); fakeVoice = null; }
        if (autoAnswerTimer != null) autoAnswerTimer.cancel();
    }
}