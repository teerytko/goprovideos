/**
 * main activity
 *
 * TODO: Try MX Player
 */

package com.intel.tsrytkon.goprovid;

import android.app.Activity;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Html;
import android.text.Spanned;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;


public class GoProActivity extends Activity {
    private static final String MEDIA = "media";
    private ListView listView;
    private TextView statusView;
    private static String STREAM_VIDEO = "https://www.youtube.com/watch?v=z0JebT4kIPU";
    private static String FILE_VIDEO = "/storage/sdcard0/DCIM/100ANDRO/VID_0010.mp4";
    //private static String FILE_VIDEO = "storage/extSdCard/DCIM/Camera/20141224_135804.mp4";
    private static String GOPRO_VIDEO = "http://10.5.5.9:8080/live/amba.m3u8";
    //https://www.youtube.com/watch?v=z0JebT4kIPU

    private class HttpAsyncTask extends AsyncTask<String, Void, String> {
        StringBuffer buffer = new StringBuffer();
        @Override
        protected String doInBackground(String... urls) {
            try {
                Document doc = Jsoup.connect(urls[0]).get();
                // Get document (HTML page) title
                String title = doc.title();
                Log.d("JSwA", "Title ["+title+"]");

                // Get meta info
                Elements metaElems = doc.select("meta");
                for (Element metaElem : metaElems) {
                    String name = metaElem.attr("name");
                    String content = metaElem.attr("content");
                }

                Elements links = doc.select("a");
                buffer.append("Links\r\n");
                for (Element topic : links) {
                    String data = topic.text();
                    String href = topic.attr("href");

                    buffer.append("Data ["+data+":"+href+"] \r\n");
                }
            }
            catch(Throwable t) {
                t.printStackTrace();
            }
            return buffer.toString();
        }
        // onPostExecute displays the results of the AsyncTask.
        @Override
        protected void onPostExecute(String result) {
            Toast.makeText(getBaseContext(), "Received!", Toast.LENGTH_LONG).show();
            System.out.print(result);
            //listView.addView();
        }
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_go_pro);
        // get reference to the views
        listView = (ListView) findViewById(R.id.listView);
        statusView = (TextView) findViewById(R.id.statusView);

        // check if you are connected or not
        if(isConnected()){
            statusView.setBackgroundColor(0xFF00CC00);
            statusView.setText("You are connected");
        }
        else{
            statusView.setText("You are NOT connected");
        }

        // show response on the EditText etResponse
        new HttpAsyncTask().execute("http://10.5.5.9:8080/");
    }
    public static String GET(String url){
        InputStream inputStream = null;
        String result = "";
        try {

            // create HttpClient
            HttpClient httpclient = new DefaultHttpClient();

            // make GET request to the given URL
            HttpResponse httpResponse = httpclient.execute(new HttpGet(url));
            System.out.println("httpclient.execute");

            // receive response as inputStream
            inputStream = httpResponse.getEntity().getContent();
            System.out.println("httpclient got content");

            // convert inputstream to string
            if(inputStream != null)
                result = convertInputStreamToString(inputStream);
            else
                result = "Did not work!";

        } catch (Exception e) {
            System.out.println("Error: "+e);
            //Log.d("InputStream", e.getMessage());
        }

        return result;
    }

    // convert inputstream to String
    private static String convertInputStreamToString(InputStream inputStream) throws IOException{
        System.out.println("convertInputStreamToString");
        BufferedReader bufferedReader = new BufferedReader( new InputStreamReader(inputStream));
        String line = "";
        String result = "";
        while((line = bufferedReader.readLine()) != null)
            Log.d("GoProVideos", "Line: "+line);
            result += line;

        inputStream.close();
        return result;

    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.go, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        else if (id == R.id.action_connect) {
            Intent intent = new Intent(GoProActivity.this,
                    MediaPlayerVideoActivity.class);
            intent.putExtra(MEDIA, FILE_VIDEO);
            startActivity(intent);
        }
        else if (id == R.id.action_play_file) {
            Intent intent = new Intent(GoProActivity.this,
                    FramePlayerVideoActivity.class);
            intent.putExtra(MEDIA, FILE_VIDEO);
            startActivity(intent);
        }
        else if (id == R.id.action_play_live) {
            Intent intent = new Intent(GoProActivity.this,
                    MediaPlayerVideoActivity.class);
            intent.putExtra(MEDIA, GOPRO_VIDEO);
            startActivity(intent);
        }

        return super.onOptionsItemSelected(item);
    }
    // check network connection
    public boolean isConnected(){
        ConnectivityManager connMgr = (ConnectivityManager) getSystemService(this.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        System.out.println(networkInfo);
        if (networkInfo != null && networkInfo.isConnected())
            return true;
        else
            return false;
    }
}
