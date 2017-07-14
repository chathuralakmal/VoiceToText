package com.fexcon.voicetotext;

import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.internal.http2.Header;

public class MainActivity extends AppCompatActivity {

    public final static String MEDIA_FOLDER = "/.nomedia/FexconAudio/";

    private boolean isRecording = false;
    private static String audioFilePath;

    private MediaRecorder mediaRecorder;
    private MediaPlayer mediaPlayer;

    private Button stopButton;
    private Button playButton;
    private Button recordButton;
    private Button processAudio;
    private TextView processedText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        recordButton = (Button)findViewById(R.id.record_audio);
        playButton = (Button)findViewById(R.id.play_audio);
        stopButton = (Button)findViewById(R.id.stop_audio);
        processAudio = (Button)findViewById(R.id.process_audio);

        recordButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    recordAudio(view);
                }catch (IOException e){
                    e.printStackTrace();
                }
            }
        });

        playButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    playAudio(view);
                }catch (IOException e){
                    e.printStackTrace();
                }
            }
        });

        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                    stopClicked(view);
            }
        });


        processAudio.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                processAudio(view);
            }
        });

        processedText = (TextView)findViewById(R.id.audio_text);


        File f = new File(Environment.getExternalStorageDirectory(), MEDIA_FOLDER);
        if (!f.exists()) {
            f.mkdirs();
        }

        audioFilePath = Environment.getExternalStorageDirectory().getAbsolutePath()
                        +MEDIA_FOLDER+"/myaudio.amr";
    }



    /** Methods **/
    public void recordAudio (View view) throws IOException
    {
        File f = new File(Environment.getExternalStorageDirectory().getAbsolutePath()
                +MEDIA_FOLDER+"/myaudio.amr");
        if (!f.exists()) {
            f.delete();
        }

        isRecording = true;
        stopButton.setEnabled(true);
        playButton.setEnabled(false);
        recordButton.setEnabled(false);


        try {
            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.AMR_WB);
            mediaRecorder.setOutputFile(audioFilePath);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_WB);
            mediaRecorder.setAudioEncodingBitRate(16000);
            mediaRecorder.prepare();
        } catch (Exception e) {
            e.printStackTrace();
        }

        mediaRecorder.start();
    }

    public void stopClicked (View view)
    {

        stopButton.setEnabled(false);
        playButton.setEnabled(true);

        if (isRecording)
        {
            recordButton.setEnabled(false);
            mediaRecorder.stop();
            mediaRecorder.release();
            mediaRecorder = null;
            isRecording = false;
        } else {
            mediaPlayer.release();
            mediaPlayer = null;
            recordButton.setEnabled(true);
        }
    }

    public void playAudio (View view) throws IOException
    {
        playButton.setEnabled(false);
        recordButton.setEnabled(false);
        stopButton.setEnabled(true);

        mediaPlayer = new MediaPlayer();
        mediaPlayer.setDataSource(audioFilePath);
        mediaPlayer.prepare();
        mediaPlayer.start();
    }

    public void processAudio(View view){
        playButton.setEnabled(false);
        recordButton.setEnabled(false);
        stopButton.setEnabled(false);
        processAudio.setEnabled(false);


        JSONObject audioRequest = new JSONObject();
        JSONObject configRequest = new JSONObject();

        JSONObject contentJsonObject = new JSONObject();

        try {
            configRequest.put("encoding","AMR_WB");
            configRequest.put("sampleRateHertz","16000");
            configRequest.put("languageCode","en-US");

        }catch (JSONException e){
            e.printStackTrace();
        }


        File file = new File(Environment.getExternalStorageDirectory().getAbsolutePath()+MEDIA_FOLDER+"/myaudio.amr");
        String byteEncodedString;
        try {
            byte[] bytes = FileUtils.readFileToByteArray(file);
            byteEncodedString = Base64.encodeToString(bytes, Base64.NO_WRAP);

            audioRequest.put("content",byteEncodedString);
            contentJsonObject.put("config",configRequest);
            contentJsonObject.put("audio",audioRequest);
        }catch (Exception e){
            e.printStackTrace();
        }

        Log.e("JSON Content","--> "+contentJsonObject.toString());

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();

        MediaType JSON = MediaType.parse("application/json");
        RequestBody body = RequestBody.create(JSON, contentJsonObject.toString());

        final Request request = new Request.Builder()
                .url("https://speech.googleapis.com/v1/speech:recognize?key=AIzaSyAD8eSr6H8CnhA9tIz2Cid3FNbxbp-7k0w")
                //.url("https://requestb.in/1m9mys01")
                .post(body)
                .addHeader("content-type", "application/json")
                .build();

        Call call = client.newCall(request);

        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e("HttpService", "onFailure() Request was: " + request);
                resetApp();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    Log.e("Response","--> "+response.toString());
                    JSONObject tempRes = new JSONObject(response.body().string());
                    Log.e("Message","--> "+tempRes.toString());

                    if(tempRes.has("results")) {

                        JSONArray alternatives;
                        alternatives = tempRes.getJSONArray("results").getJSONObject(0).getJSONArray("alternatives");

                        final JSONObject transcript;
                        transcript = alternatives.getJSONObject(0);

                        Log.e("Check", "-->" + transcript.getString("transcript"));
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    processedText.setText(transcript.getString("transcript"));
                                }catch (Exception e){
                                    e.printStackTrace();
                                }
                            }
                        });

                    }

                    resetApp();

                }catch (Exception e){
                    e.printStackTrace();
                    resetApp();
                }

            }
        });


    }


    public void resetApp(){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                playButton.setEnabled(true);
                recordButton.setEnabled(true);
                stopButton.setEnabled(true);
                processAudio.setEnabled(true);
            }
        });
    }
}
