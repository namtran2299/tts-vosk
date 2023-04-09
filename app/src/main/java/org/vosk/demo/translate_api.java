package org.vosk.demo;

import android.os.AsyncTask;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;

import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class translate_api extends AsyncTask<String, String, String> {
    private OnTranslationCompleteListener listener;

    @Override
    protected String doInBackground(String... strings) {
        return postRequest(strings[0]);
    }

    private void httpExecute(String aurl, OutputStream output) throws Exception {
        URL url = new URL(aurl);
        URLConnection conexion = url.openConnection();
        conexion.connect();
        int lenghtOfFile = conexion.getContentLength();
        InputStream input = new BufferedInputStream(url.openStream());

        int count = 0;
        byte data[] = new byte[1024];
        long total = 0;

        while ((count = input.read(data)) != -1) {
            total += count;
            publishProgress("" + (int) ((total * 100) / lenghtOfFile));
            output.write(data, 0, count);
        }
        output.close();
        input.close();
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        listener.onStartTranslation();
    }

    @Override
    protected void onPostExecute(String text) {
        listener.onCompleted(text);
    }

    public interface OnTranslationCompleteListener {
        void onStartTranslation();

        void onCompleted(String text);

        void onError(Exception e);
    }

    public void setOnTranslationCompleteListener(OnTranslationCompleteListener listener) {
        this.listener = listener;
    }

    public String postRequest(String text){
        OkHttpClient client = new OkHttpClient();

        RequestBody formBody = new FormBody.Builder()
                .add("fromLang", "en")
                .add("to", "vi")
                .add("token", "cFRfiCqPzZuaE0ar_-93QB5ocNCWWmMw")
                .add("key", "1681038321258")
                .add("tryFetchingGenderDebiasedTranslations", "true")
                .add("text", text)
                .build();
        Request request = new Request.Builder()
                .header("cookie","MUID=00F21830208B639A37EE09D1214B6273; SUID=M; MUIDB=00F21830208B639A37EE09D1214B6273; _EDGE_S=SID=1AC6DA833A926D093C3BC86D3B526C6D; SRCHD=AF=NOFORM; SRCHUID=V=2&GUID=37B7ED308E5B48F38967E9A0BE8E2216&dmnchg=1; _SS=SID=1AC6DA833A926D093C3BC86D3B526C6D; _tarLang=default=vi; _TTSS_IN=hist=WyJlbiIsImF1dG8tZGV0ZWN0Il0=; _TTSS_OUT=hist=WyJ2aSJd; SRCHUSR=DOB=20230409&T=1681038321000&TPC=1681038322000; ipv6=hit=1681041924908&t=6; SRCHHPGUSR=SRCHLANG=vi&PV=5.4.0&WTS=63816635121&HV=1681038418; btstkn=uuvGiCoBSXqcoNOr3R0tBcyePJxci2bDJ0r68poYMZNYrp2pYURlroN6qvFRc2QW7zspz4JbQvLjISnffBQOejxhKLgL7hUblHn3gO1w6bk%253D")
                .header("origin","https://www.bing.com")
                .header("referer","https://www.bing.com/translator")
                .header("content-type","application/x-www-form-urlencoded")
                .header("user-agent","Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/107.0.0.0 Safari/537.36")
                .url("https://www.bing.com/ttranslatev3?isVertical=1&&IG=EFD2C011238F40049FAA6B1D7C9A4CB0&IID=translator.5028")
                .post(formBody)
                .build();

        try {
            Response response = client.newCall(request).execute();
            JSONArray jSONArray = new JSONArray( response.body().string());
            if(jSONArray.length()>0){
                JSONObject item1 = (JSONObject) jSONArray.get(0);
                JSONArray item2 = item1.getJSONArray("translations");

                return (String) item2.getJSONObject(0).get("text");
            }
            // Do something with the response.
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
        return "";
    }
}