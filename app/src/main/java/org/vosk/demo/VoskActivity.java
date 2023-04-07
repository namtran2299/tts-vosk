// Copyright 2019 Alpha Cephei Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//       http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.vosk.demo;

import android.Manifest;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.ToggleButton;

import org.vosk.LibVosk;
import org.vosk.LogLevel;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechService;
import org.vosk.android.SpeechStreamService;
import org.vosk.android.StorageService;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.net.URLConnection;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Enumeration;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;


public class VoskActivity extends Activity implements
        RecognitionListener {

    static private final int STATE_START = 0;
    static private final int STATE_READY = 1;
    static private final int STATE_DONE = 2;
    static private final int STATE_FILE = 3;
    static private final int STATE_MIC = 4;

    /* Used to handle permission request */
    private static final int PERMISSIONS_REQUEST_RECORD_AUDIO = 1;

    private Model model;
    private SpeechService speechService;
    private SpeechStreamService speechStreamService;
    private TextView resultView;
    private TextView resultViewPartial;
    private String previousSent = "";
    private long counter  = 0;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.main);

        // Setup layout
        resultView = findViewById(R.id.result_text);
        resultViewPartial = findViewById(R.id.result_text_partial);
        resultView.setMovementMethod(new ScrollingMovementMethod());
        resultViewPartial.setMovementMethod(new ScrollingMovementMethod());
        setUiState(STATE_START);

        initSpinner();
        findViewById(R.id.recognize_file).setOnClickListener(view -> recognizeFile());
        findViewById(R.id.recognize_mic).setOnClickListener(view -> recognizeMicrophone());
        ((ToggleButton) findViewById(R.id.pause)).setOnCheckedChangeListener((view, isChecked) -> pause(isChecked));
        LibVosk.setLogLevel(LogLevel.INFO);

        // Check if user has given permission to record audio, init the model after permission is granted
        int permissionCheck = ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.RECORD_AUDIO);
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSIONS_REQUEST_RECORD_AUDIO);
        } else {
//            initModel();
        }

    }

    private void initSpinner() {
        //set auto complete
        final AutoCompleteTextView completeTextView = (AutoCompleteTextView) findViewById(R.id.edit_ip);
        final String[] labelArray = getResources().getStringArray(R.array.labelArray);
        final String[] linkArray = getResources().getStringArray(R.array.linkArray);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_dropdown_item_1line, labelArray);
        completeTextView.setAdapter(adapter);
        //set spinner
        final Spinner spinner = (Spinner) findViewById(R.id.spinner_ip);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                completeTextView.setText(spinner.getSelectedItem().toString());
                completeTextView.dismissDropDown();
                if (linkArray.length > position && position > 0) {
                    Log.d("Spinner: ", linkArray[position]);
                    download(linkArray[position]);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                completeTextView.setText(spinner.getSelectedItem().toString());
                completeTextView.dismissDropDown();
            }
        });

//        resultView.setOnClickListener(view -> {
//            ArrayAdapter<String> dataAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_dropdown_item_1line, labelArray);
//            dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
//            spinner.setAdapter(dataAdapter);
//            spinner.setVisibility(View.INVISIBLE);
//            spinner.performClick();
//        });

    }

    private void initModel2(String outputPath) {
        Executor executor = Executors.newSingleThreadExecutor(); // change according to your requirements
        Handler handler = new Handler(Looper.getMainLooper());
        final StorageService.Callback<Model> completeCallback = new StorageService.Callback<Model>() {
            @Override
            public void onComplete(Model result) {
                VoskActivity.this.model = result;
                setUiState(STATE_READY);
            }
        };
        final StorageService.Callback<IOException> errorCallback = new StorageService.Callback<IOException>() {
            @Override
            public void onComplete(IOException exception) {
                setErrorState("Failed to unpack the model" + exception.getMessage());
            }
        };
        executor.execute(() -> {
            try {
                Model model = new Model(outputPath);
                handler.post(() -> completeCallback.onComplete(model));
            } catch (final IOException e) {
                handler.post(() -> errorCallback.onComplete(e));
            }
        });

    }

    private void initModel() {
        StorageService.unpack(this, "model-en-us", "model",
                (model) -> {
                    this.model = model;
                    setUiState(STATE_READY);
                },
                (exception) -> setErrorState("Failed to unpack the model" + exception.getMessage()));
    }

    private ProgressDialog mProgressDialog;

    //    String unzipLocation = Environment.getExternalStorageDirectory() + "/model/";
//    String StorezipFileLocation = Environment.getExternalStorageDirectory() + "/DownloadedZip";
//    String DirectoryName = Environment.getExternalStorageDirectory() + "/model/";
    String StorezipFileLocation;
    String DirectoryName;


    private void download(String url) {
        File externalFilesDir = this.getExternalFilesDir(null);
        if (externalFilesDir == null) {
            throw new RuntimeException("cannot get external files dir, "
                    + "external storage state is " + Environment.getExternalStorageState());
        }
        this.StorezipFileLocation = externalFilesDir.getAbsolutePath() + "/DownloadedZip";
        this.DirectoryName = externalFilesDir.getAbsolutePath() + "/model/";

//        File targetDir = new File(externalFilesDir, "model");
//        String resultPath = new File(targetDir, sourcePath).getAbsolutePath();

        String signedPath = DirectoryName + md5(url);
        File uuidFile = new File(signedPath);
        if (uuidFile.exists()) {

            try {
                String path = readLine(new FileInputStream(uuidFile));
                initModel2(path);
            } catch (IOException e) {
                Log.e("download", "download: ", e);
            }
        } else {
//            DownloadZipfile mew = new DownloadZipfile();
//            mew.execute(url, signedPath);
        }

    }


    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSIONS_REQUEST_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Recognizer initialization is a time-consuming and it involves IO,
                // so we execute it in async task
//                initModel();
            } else {
                finish();
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (speechService != null) {
            speechService.stop();
            speechService.shutdown();
        }

        if (speechStreamService != null) {
            speechStreamService.stop();
        }
    }

    @Override
    public void onResult(String hypothesis) {
        hypothesis = getSent(hypothesis);
        Log.d("STT", "onResult: " + hypothesis);
        if (hypothesis.isEmpty()) {
            if (resultViewPartial.length() > 0) {
                counter++;
                resultView.append(counter+"."+resultViewPartial.getText().toString() + "\n\n");
            }
        } else {
            resultViewPartial.setText(hypothesis);
        }
//        previousSent = hypothesis;
    }

    @Override
    public void onFinalResult(String hypothesis) {
        hypothesis = getSent(hypothesis);
        Log.d("STT", "onFinalResult: " + hypothesis);
        if (hypothesis.isEmpty()) {
            if (resultViewPartial.length() > 0) {
                counter++;
                resultView.append(counter+"."+resultViewPartial.getText().toString() + "\n\n");
            }
        } else {
            resultViewPartial.setText(hypothesis);
        }
//        previousSent = hypothesis;
        setUiState(STATE_DONE);
        if (speechStreamService != null) {
            speechStreamService = null;
        }
    }

    @Override
    public void onPartialResult(String hypothesis) {
        hypothesis = getSent(hypothesis);
        Log.d("STT", "onPartialResult: " + hypothesis);
        if (hypothesis.isEmpty()) {
            if (resultViewPartial.length() > 0) {
                counter++;
                resultView.append(counter+"."+resultViewPartial.getText().toString() + "\n\n");
            }
        } else {
            resultViewPartial.setText(hypothesis);
        }
//        previousSent = hypothesis;
    }

    private String getSent(String hypothesis) {
        if (hypothesis == null) return "";
        int p2 = hypothesis.lastIndexOf('"');
        if (p2 < 0) return "";
        int p0 = hypothesis.lastIndexOf(':', p2 - 1);
        if (p0 < 0) return "";
        int p1 = hypothesis.indexOf('"', p0 + 1);
        if (p1 < 0 || p1 + 1 >= p2) return "";
        return hypothesis.substring(p1 + 1, p2).trim();
    }

    @Override
    public void onError(Exception e) {
        setErrorState(e.getMessage());
    }

    @Override
    public void onTimeout() {
        setUiState(STATE_DONE);
    }

    private void setUiState(int state) {
        switch (state) {
            case STATE_START:
                resultView.setText("");
                resultViewPartial.setText(R.string.preparing);
                findViewById(R.id.recognize_file).setEnabled(false);
                findViewById(R.id.recognize_mic).setEnabled(false);
                findViewById(R.id.pause).setEnabled((false));
                break;
            case STATE_READY:
                resultView.setText("");
                resultViewPartial.setText(R.string.ready);
                ((Button) findViewById(R.id.recognize_mic)).setText(R.string.recognize_microphone);
                findViewById(R.id.recognize_file).setEnabled(true);
                findViewById(R.id.recognize_mic).setEnabled(true);
                findViewById(R.id.pause).setEnabled((false));
                break;
            case STATE_DONE:
                ((Button) findViewById(R.id.recognize_file)).setText(R.string.recognize_file);
                ((Button) findViewById(R.id.recognize_mic)).setText(R.string.recognize_microphone);
                findViewById(R.id.recognize_file).setEnabled(true);
                findViewById(R.id.recognize_mic).setEnabled(true);
                findViewById(R.id.pause).setEnabled((false));
                ((ToggleButton) findViewById(R.id.pause)).setChecked(false);
                break;
            case STATE_FILE:
                ((Button) findViewById(R.id.recognize_file)).setText(R.string.stop_file);
                resultView.setText("");
                resultViewPartial.setText(getString(R.string.starting));
                findViewById(R.id.recognize_mic).setEnabled(false);
                findViewById(R.id.recognize_file).setEnabled(true);
                findViewById(R.id.pause).setEnabled((false));
                break;
            case STATE_MIC:
                ((Button) findViewById(R.id.recognize_mic)).setText(R.string.stop_microphone);
                resultView.setText("");
                resultViewPartial.setText(getString(R.string.say_something));
                findViewById(R.id.recognize_file).setEnabled(false);
                findViewById(R.id.recognize_mic).setEnabled(true);
                findViewById(R.id.pause).setEnabled((true));
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + state);
        }
    }

    private void setErrorState(String message) {
        resultView.append(message);
        resultViewPartial.setText(message);
        ((Button) findViewById(R.id.recognize_mic)).setText(R.string.recognize_microphone);
        findViewById(R.id.recognize_file).setEnabled(false);
        findViewById(R.id.recognize_mic).setEnabled(false);
    }

    private void recognizeFile() {
        if (speechStreamService != null) {
            setUiState(STATE_DONE);
            speechStreamService.stop();
            speechStreamService = null;
        } else {
            setUiState(STATE_FILE);
            try {
                Recognizer rec = new Recognizer(model, 16000.f, "[\"one zero zero zero one\", " +
                        "\"oh zero one two three four five six seven eight nine\", \"[unk]\"]");

                InputStream ais = getAssets().open(
                        "10001-90210-01803.wav");
                if (ais.skip(44) != 44) throw new IOException("File too short");

                speechStreamService = new SpeechStreamService(rec, ais, 16000);
                speechStreamService.start(this);
            } catch (IOException e) {
                setErrorState(e.getMessage());
            }
        }
    }

    private void recognizeMicrophone() {
        if (speechService != null) {
            setUiState(STATE_DONE);
            speechService.stop();
            speechService = null;
            saveCurrentConversationText();
        } else {
            setUiState(STATE_MIC);
            try {
                Recognizer rec = new Recognizer(model, 16000.0f);
                speechService = new SpeechService(rec, 16000.0f);
                speechService.startListening(this);
            } catch (IOException e) {
                setErrorState(e.getMessage());
            }
        }
    }


    private void pause(boolean checked) {
        if (speechService != null) {
            speechService.setPause(checked);
        }
    }

    class DownloadZipfile extends AsyncTask<String, String, String> {
        String result = "";
        String signedPath = "";

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            mProgressDialog = new ProgressDialog(VoskActivity.this);
            mProgressDialog.setMessage("Downloading...");
            mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            mProgressDialog.setCancelable(false);
            mProgressDialog.show();
        }

        @Override
        protected String doInBackground(String... aurl) {
            int count;

            try {
                deleteContents(new File(StorezipFileLocation));
                this.signedPath = aurl[1];

                URL url = new URL(aurl[0]);
                URLConnection conexion = url.openConnection();
                conexion.connect();
                int lenghtOfFile = conexion.getContentLength();
                InputStream input = new BufferedInputStream(url.openStream());

                OutputStream output = new FileOutputStream(StorezipFileLocation);

                byte data[] = new byte[1024];
                long total = 0;

                while ((count = input.read(data)) != -1) {
                    total += count;
                    publishProgress("" + (int) ((total * 100) / lenghtOfFile));
                    output.write(data, 0, count);
                }
                output.close();
                input.close();
                result = "true";

            } catch (Exception e) {

                result = "false";
                Log.e("Download", "Failed to downloading data", e);
            }
            return null;

        }

        protected void onProgressUpdate(String... progress) {
            Log.d("ANDRO_ASYNC", progress[0]);
            mProgressDialog.setProgress(Integer.parseInt(progress[0]));
        }

        @Override
        protected void onPostExecute(String unused) {
            mProgressDialog.dismiss();
            if (result.equalsIgnoreCase("true")) {
                try {
                    unzip(this.signedPath);

                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            } else {

            }
        }
    }

    public void unzip(String signedPath) throws IOException {
        mProgressDialog = new ProgressDialog(VoskActivity.this);
        mProgressDialog.setMessage("Please Wait...");
        mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        mProgressDialog.setCancelable(false);
        mProgressDialog.show();
        new UnZipTask().execute(StorezipFileLocation, DirectoryName, signedPath);
    }


    private class UnZipTask extends AsyncTask<String, Void, Boolean> {
        String lastDirectory = "";
        String filePath = "";

        @SuppressWarnings("rawtypes")
        @Override
        protected Boolean doInBackground(String... params) {
            filePath = params[0];
            String destinationPath = params[1];
            String signedPath = params[2];

            File archive = new File(filePath);
            try {
                deleteContents(new File(destinationPath));

                String firstName = "";
                ZipFile zipfile = new ZipFile(archive);
                for (Enumeration e = zipfile.entries(); e.hasMoreElements(); ) {
                    ZipEntry entry = (ZipEntry) e.nextElement();
                    if (firstName.isEmpty()) {
                        firstName = entry.getName();
                    }
                    unzipEntry(zipfile, entry, destinationPath);
                }


                UnzipUtil d = new UnzipUtil(StorezipFileLocation, DirectoryName);
                d.unzip();
                lastDirectory = DirectoryName + firstName;
                writeToFile(lastDirectory, signedPath, VoskActivity.this);
            } catch (Exception e) {
                return false;
            } finally {
                archive.deleteOnExit();
            }

            return true;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            mProgressDialog.dismiss();
            deleteContents(new File(filePath));
        }

        private void unzipEntry(ZipFile zipfile, ZipEntry entry, String outputDir) throws IOException {

            if (entry.isDirectory()) {
                createDir(new File(outputDir, entry.getName()));
                return;
            }

            File outputFile = new File(outputDir, entry.getName());
            if (!outputFile.getParentFile().exists()) {
                createDir(outputFile.getParentFile());
            }

            // Log.v("", "Extracting: " + entry);
            BufferedInputStream inputStream = new BufferedInputStream(zipfile.getInputStream(entry));
            BufferedOutputStream outputStream = new BufferedOutputStream(new FileOutputStream(outputFile));

            try {

            } finally {
                outputStream.flush();
                outputStream.close();
                inputStream.close();
            }
        }

        private void createDir(File dir) {
            if (dir.exists()) {
                return;
            }
            if (!dir.mkdirs()) {
                throw new RuntimeException("Can not create dir " + dir);
            }
        }
    }

    private static String readLine(InputStream is) throws IOException {
        return new BufferedReader(new InputStreamReader(is)).readLine();
    }

    private static boolean deleteContents(File dir) {
        File[] files = dir.listFiles();
        boolean success = true;
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    success &= deleteContents(file);
                }
                if (!file.delete()) {
                    success = false;
                }
            }
        }
        return success;
    }

    public static String md5(final String s) {
        final String MD5 = "MD5";
        try {
            // Create MD5 Hash
            MessageDigest digest = java.security.MessageDigest
                    .getInstance(MD5);
            digest.update(s.getBytes());
            byte messageDigest[] = digest.digest();

            // Create Hex String
            StringBuilder hexString = new StringBuilder();
            for (byte aMessageDigest : messageDigest) {
                String h = Integer.toHexString(0xFF & aMessageDigest);
                while (h.length() < 2)
                    h = "0" + h;
                hexString.append(h);
            }
            return hexString.toString();

        } catch (NoSuchAlgorithmException e) {
            Log.e("md5", "md5: ", e);
        }
        return "";
    }

    private void saveCurrentConversationText() {
        String data = resultView.getText().toString();
        File externalFilesDir = this.getExternalFilesDir(null);
        if (externalFilesDir == null) {
            return;
        }
        String baseContentDir = externalFilesDir.getAbsolutePath() + "/contents/";
        new File(baseContentDir).mkdirs();
        String fileName = DateFormat.format("yyyy-MM-dd_hh_mm_ss", new java.util.Date()).toString();
        writeToFile(data, baseContentDir + fileName + ".txt", this);
    }

    private void writeToFile(String data, String pathName, Context context) {
//        File uuidFile = new File( pathName);
//        try {
//            FileOutputStream stream = new FileOutputStream(uuidFile);
//            stream.write(data.getBytes());
//            stream.close();
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        } finally {
//        }
        try {
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(new FileOutputStream(pathName));
            outputStreamWriter.write(data);
            outputStreamWriter.close();
        } catch (IOException e) {
            Log.e("Exception", "File write failed: " + e.toString());
        }
    }
}
