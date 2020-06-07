package com.example.youtubeuploader;

import android.Manifest;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.GooglePlayServicesAvailabilityIOException;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.googleapis.media.MediaHttpUploader;
import com.google.api.client.googleapis.media.MediaHttpUploaderProgressListener;
import com.google.api.client.http.AbstractInputStreamContent;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.InputStreamContent;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.YouTubeScopes;
import com.google.api.services.youtube.model.Video;
import com.google.api.services.youtube.model.VideoSnippet;
import com.google.api.services.youtube.model.VideoStatus;
import com.nabinbhandari.android.permissions.PermissionHandler;
import com.nabinbhandari.android.permissions.Permissions;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

//import java.security.Permissions;

//import android.support.annotation.NonNull;

public class MainActivity extends Activity {
    GoogleAccountCredential mCredential;
    private TextView mOutputText;
    private Button mCallApiButton;
    private ImageView img;

    ProgressDialog mProgress;

    private static final String TAG = "MainActivity";
    private static final int CAPTURE_RETURN = 1;
    private static final int GALLERY_RETURN = 2;

//    r progressDialog;
    ArrayList<String> url;
    SQLiteDatabase mdb;
    static final int REQUEST_ACCOUNT_PICKER = 1000;
    static final int REQUEST_AUTHORIZATION = 1001;
    static final int REQUEST_GOOGLE_PLAY_SERVICES = 1002;
    static final int REQUEST_PERMISSION_GET_ACCOUNTS = 1003;
    ProgressBar  progressDialog;
    private static final String BUTTON_TEXT = "Upload the Video";
    private static final String PREF_ACCOUNT_NAME = "accountName";
    private static final String[] SCOPES = { YouTubeScopes.YOUTUBE_READONLY,YouTubeScopes.YOUTUBE_UPLOAD };

    /**
     * Create the main activity.
     * @param savedInstanceState previously saved instance data.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
         url=new ArrayList<>();
//        progressDialog=(ProgressBar)findViewById(R.id.progressBar);
        mdb=this.openOrCreateDatabase("Url",MODE_PRIVATE,null);
//        ImageView img=(ImageView) findViewById(R.id.ytImg);

        LinearLayout activityLayout = new LinearLayout(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT);
        activityLayout.setLayoutParams(lp);
        activityLayout.setOrientation(LinearLayout.VERTICAL);
        activityLayout.setPadding(16, 16, 16, 16);

        ViewGroup.LayoutParams tlp = new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);

        mCallApiButton = new Button(this);
        mCallApiButton.setText(BUTTON_TEXT);
//       
        mCallApiButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Permissions.check(MainActivity.this, new String[]{Manifest.permission.GET_ACCOUNTS,
                                Manifest.permission.READ_EXTERNAL_STORAGE},
                        "Accounts and Storage permissions are required ", new Permissions.Options()
                                .setSettingsDialogTitle("Warning!").setRationaleDialogTitle("Info"),
                        new PermissionHandler() {
                            @Override
                            public void onGranted() {
                                mCallApiButton.setEnabled(false);
                                mOutputText.setText("");
                                getResultsFromApi();
                                mCallApiButton.setEnabled(true);
                            }
                        });
            }
        });
        activityLayout.addView(mCallApiButton);

        mOutputText = new TextView(this);
        mOutputText.setLayoutParams(tlp);
        mOutputText.setPadding(16, 16, 16, 16);
        mOutputText.setVerticalScrollBarEnabled(true);
        mOutputText.setMovementMethod(new ScrollingMovementMethod());
        mOutputText.setText(
                "                           Click to Upload The Video");
        activityLayout.addView(mOutputText);

        mProgress = new ProgressDialog(this);
        mProgress.setMessage("Calling YouTube Data API ...");

        setContentView(activityLayout);

        // Initialize credentials and service object.
        mCredential = GoogleAccountCredential.usingOAuth2(
                getApplicationContext(), Arrays.asList(SCOPES))
                .setBackOff(new ExponentialBackOff());
    }
    public void bclick(View view){
        Permissions.check(MainActivity.this, new String[]{Manifest.permission.GET_ACCOUNTS,
                        Manifest.permission.READ_EXTERNAL_STORAGE},
                "Accounts and Storage permissions are required ", new Permissions.Options()
                        .setSettingsDialogTitle("Warning!").setRationaleDialogTitle("Info"),
                new PermissionHandler() {
                    @Override
                    public void onGranted() {
                        mCallApiButton.setEnabled(false);
                        mOutputText.setText("");
                        getResultsFromApi();
                        mCallApiButton.setEnabled(true);
                    }
                });
    }



    /**
     * Attempt to call the API, after verifying that all the preconditions are
     * satisfied. The preconditions are: Google Play Services installed, an
     * account was selected and the device currently has online access. If any
     * of the preconditions are not satisfied, the app will prompt the user as
     * appropriate.
     */
    private void getResultsFromApi() {
        if (! isGooglePlayServicesAvailable()) {
            acquireGooglePlayServices();
        } else if (mCredential.getSelectedAccountName() == null) {
            chooseAccount();
        } else if (! isDeviceOnline()) {
            mOutputText.setText("No network connection available.");
        } else {
//            new MakeRequestTask(mCredential).execute();
            mOutputText.setText("Credentials Initialized");
            initVideo();
        }
    }

    private void initVideo() {

        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_PICK);
        intent.setType("video/*");

        List<ResolveInfo> list = getPackageManager().queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
        if (list.size() <= 0) {
            Log.d(TAG, "no video picker intent on this hardware");
            Toast.makeText(MainActivity.this, "No video picker found on device", Toast.LENGTH_SHORT).show();
            return;
        }
        startActivityForResult(intent, GALLERY_RETURN);


    }

    /**
     * Attempts to set the account used with the API credentials. If an account
     * name was previously saved it will use that one; otherwise an account
     * picker dialog will be shown to the user. Note that the setting the
     * account to use with the credentials object requires the app to have the
     * GET_ACCOUNTS permission, which is requested here if it is not already
     * present. The AfterPermissionGranted annotation indicates that this
     * function will be rerun automatically whenever the GET_ACCOUNTS permission
     * is granted.
     */
    @AfterPermissionGranted(REQUEST_PERMISSION_GET_ACCOUNTS)
    private void chooseAccount() {
        if (EasyPermissions.hasPermissions(
                this, Manifest.permission.GET_ACCOUNTS)) {
            String accountName = getPreferences(Context.MODE_PRIVATE)
                    .getString(PREF_ACCOUNT_NAME, null);
            if (accountName != null) {
                mCredential.setSelectedAccountName(accountName);
                getResultsFromApi();
            } else {
                // Start a dialog from which the user can choose an account
                startActivityForResult(
                        mCredential.newChooseAccountIntent(),
                        REQUEST_ACCOUNT_PICKER);
            }
        } else {
            // Request the GET_ACCOUNTS permission via a user dialog
            EasyPermissions.requestPermissions(
                    this,
                    "This app needs to access your Google account (via Contacts).",
                    REQUEST_PERMISSION_GET_ACCOUNTS,
                    Manifest.permission.GET_ACCOUNTS);
        }
    }

    /**
     * Called when an activity launched here (specifically, AccountPicker
     * and authorization) exits, giving you the requestCode you started it with,
     * the resultCode it returned, and any additional data from it.
     * @param requestCode code indicating which activity result is incoming.
     * @param resultCode code indicating the result of the incoming
     *     activity result.
     * @param data Intent (containing result data) returned by incoming
     *     activity result.
     */
    @Override
    protected void onActivityResult(
            int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch(requestCode) {
            case REQUEST_GOOGLE_PLAY_SERVICES:
                if (resultCode != RESULT_OK) {
                    mOutputText.setText(
                            "This app requires Google Play Services. Please install " +
                                    "Google Play Services on your device and relaunch this app.");
                } else {
                    getResultsFromApi();
                }
                break;
            case REQUEST_ACCOUNT_PICKER:
                if (resultCode == RESULT_OK && data != null &&
                        data.getExtras() != null) {
                    String accountName =
                            data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                    if (accountName != null) {
                        SharedPreferences settings =
                                getPreferences(Context.MODE_PRIVATE);
                        SharedPreferences.Editor editor = settings.edit();
                        editor.putString(PREF_ACCOUNT_NAME, accountName);
                        editor.apply();
                        mCredential.setSelectedAccountName(accountName);
                        getResultsFromApi();
                    }
                }
                break;
            case REQUEST_AUTHORIZATION:
                if (resultCode == RESULT_OK) {
                    getResultsFromApi();
                }
                break;

            case CAPTURE_RETURN:
            case GALLERY_RETURN:
                if (resultCode == RESULT_OK) {

                    new UploadVideoAsync(data.getData()).execute();
                }
                break;
        }
    }
    //////////////////////////////////////////////////////////////////////

    private String uploadYoutube(Uri data) {

        Log.d("URI Data", data.toString());

        HttpTransport transport = AndroidHttp.newCompatibleTransport();
//        JsonFactory jsonFactory = new AndroidJsonFactory(); // GsonFactory
        JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();

        HttpRequestInitializer initializer = new HttpRequestInitializer() {
            @Override
            public void initialize(HttpRequest request) throws IOException {
                mCredential.initialize(request);

                request.setLoggingEnabled(true);
//                request.setIOExceptionHandler(new HttpBackOffIOExceptionHandler(new ExponentialBackOff()));
            }
        };

        YouTube.Builder youtubeBuilder = new YouTube.Builder(transport, jsonFactory, initializer);
        youtubeBuilder.setApplicationName(getString(R.string.app_name));
//        youtubeBuilder.setYouTubeRequestInitializer(new YouTubeRequestInitializer(API_KEY));
        YouTube youtube = youtubeBuilder.build();

        String PRIVACY_STATUS = "unlisted"; // or public,private
        String PARTS = "snippet,status,contentDetails";

        String videoId = null;
        try {
            Video videoObjectDefiningMetadata = new Video();
            videoObjectDefiningMetadata.setStatus(new VideoStatus().setPrivacyStatus(PRIVACY_STATUS));

            VideoSnippet snippet = new VideoSnippet();
            snippet.setTitle("CALL YOUTUBE DATA API UNLISTED TEST " + System.currentTimeMillis());
            snippet.setDescription("MyDescription");
            snippet.setTags(Arrays.asList(new String[]{"TaG1,TaG2"}));
            videoObjectDefiningMetadata.setSnippet(snippet);

            YouTube.Videos.Insert videoInsert = youtube.videos().insert(
                    PARTS,
                    videoObjectDefiningMetadata,
                    getMediaContent(getFileFromUri(data, MainActivity.this)));/*.setOauthToken(token);*/
//                    .setKey(API_KEY);

            MediaHttpUploader uploader = videoInsert.getMediaHttpUploader();
            uploader.setDirectUploadEnabled(false);

            MediaHttpUploaderProgressListener progressListener = new MediaHttpUploaderProgressListener() {
                public void progressChanged(MediaHttpUploader uploader) throws IOException {
                    Log.d(TAG, "progressChanged: " + uploader.getUploadState());
                    switch (uploader.getUploadState()) {
                        case INITIATION_STARTED:
                            break;
                        case INITIATION_COMPLETE:
                            break;
                        case MEDIA_IN_PROGRESS:
                            break;
                        case MEDIA_COMPLETE:
                        case NOT_STARTED:
                            Log.d(TAG, "progressChanged: upload_not_started");
                            break;
                    }
                }
            };
            uploader.setProgressListener(progressListener);

            Log.d(TAG, "Uploading..");
            Video returnedVideo = videoInsert.execute();
            Log.d(TAG, "Video upload completed");
            videoId = returnedVideo.getId();
            String vidId="https://www.youtube.com/watch?v="+videoId;
            DatabaseHelper d=new DatabaseHelper(this);
            d.InsertData(vidId);
            Cursor c=d.GetData();
            if(c.getCount()>0) {
                while (c.moveToNext()){
                    url.add(c.getString(0));
             }
            }
//            mdb.execSQL("CREATE TABLE IF NOT EXISTS tab(s VARCHAR)");
//            mdb.execSQL("INSERT INTO tab(s) VALUES (videoId)");

            // for getting from the database
//            Cursor c=mdb.rawQuery("SELECT * FROM tab",null);
//            while(c!=null){
//                url.add("https://www.youtube.com/watch?v="+c.getString(0));
//            }


            Log.d(TAG, String.format("videoId = [%s]", videoId));
        } catch (final GooglePlayServicesAvailabilityIOException availabilityException) {
            Log.e(TAG, "GooglePlayServicesAvailabilityIOException", availabilityException);
        } catch (UserRecoverableAuthIOException userRecoverableException) {
            Log.i(TAG, String.format("UserRecoverableAuthIOException: %s",
                    userRecoverableException.getMessage()));
        } catch (IOException e) {
            Log.e(TAG, "IOException", e);
        }

        return videoId;

    }
    ///////////////////////////databse
     public static class DatabaseHelper extends SQLiteOpenHelper {
          public static final String name="Url.db";
        public static final String Table="Youtube_Url";
        public static final String col1="VIDID";

        DatabaseHelper(Context context){//,String name,SQLiteDatabase.CursorFactory factory,int version
            super(context,name,null,1);

        }

        @Override
        public void onCreate(SQLiteDatabase db) {
          db.execSQL(" create table "+Table+" (VIDID VARCHAR) ");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
           db.execSQL("DROP TABLE IF EXISTS "+Table);
           onCreate(db);
        }
        public boolean InsertData(String vidId){
            SQLiteDatabase db=this.getWritableDatabase();
            ContentValues c=new ContentValues();
            c.put(col1,vidId);
            long r=db.insert(Table,null,c);
            if(r==-1){
                return false;
            }else{
                return true;
            }

        }

        public Cursor GetData(){
            SQLiteDatabase db=this.getWritableDatabase();
            Cursor c=db.rawQuery("select * from " +Table,null);
            return c;
        }
    }



    //////////////////////////////////

    public class UploadVideoAsync extends AsyncTask<Void, Void, String> {

        Uri data;

//        ProgressDialog progressDialog;



        public UploadVideoAsync(Uri data) {
            this.data = data;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
//            progressDialog = new ProgressDialog(MainActivity.this);
//            progressDialog.setMessage("Uploading Video to youtube");
//            progressDialog = (ProgressBar)rootView.findViewById(R.id.progressBar);
//            progressDialog.setVisibility(View.VISIBLE);
        }

        @Override
        protected String doInBackground(Void... voids) {

            return uploadYoutube(data);
        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);
            Toast.makeText(MainActivity.this, "VideoId is " + s, Toast.LENGTH_SHORT).show();
            Log.i("VideoId", "" + s);
//            progressDialog.setVisibility(View.INVISIBLE);
        }
    }
    //////////////////////////////////////////////////////////////////////

    /**
     * Respond to requests for permissions at runtime for API 23 and above.
     * @param requestCode The request code passed in
     *     requestPermissions(android.app.Activity, String, int, String[])
     * @param permissions The requested permissions. Never null.
     * @param grantResults The grant results for the corresponding permissions
     *     which is either PERMISSION_GRANTED or PERMISSION_DENIED. Never null.
     */
//    @Override
//    public void onRequestPermissionsResult(int requestCode,
//                                           @NonNull String[] permissions,
//                                           @NonNull int[] grantResults) {
//        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
//        EasyPermissions.onRequestPermissionsResult(
//                requestCode, permissions, grantResults, this);
//    }

    /**
     * Callback for when a permission is granted using the EasyPermissions
     * library.
     * @param requestCode The request code associated with the requested
     *         permission
     * @param list The requested permission list. Never null.
     */
//    @Override
//    public void onPermissionsGranted(int requestCode, List<String> list) {
//        // Do nothing.
//    }

    /**
     * Callback for when a permission is denied using the EasyPermissions
     * library.
     * @param requestCode The request code associated with the requested
     *         permission
     * @param list The requested permission list. Never null.
     */
//    @Override
//    public void onPermissionsDenied(int requestCode, List<String> list) {
//        // Do nothing.
//    }

    /**
     * Checks whether the device currently has a network connection.
     * @return true if the device has a network connection, false otherwise.
     */
    private boolean isDeviceOnline() {
        ConnectivityManager connMgr =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        return (networkInfo != null && networkInfo.isConnected());
    }

    /**
     * Check that Google Play services APK is installed and up to date.
     * @return true if Google Play Services is available and up to
     *     date on this device; false otherwise.
     */
    private boolean isGooglePlayServicesAvailable() {
        GoogleApiAvailability apiAvailability =
                GoogleApiAvailability.getInstance();
        final int connectionStatusCode =
                apiAvailability.isGooglePlayServicesAvailable(this);
        return connectionStatusCode == ConnectionResult.SUCCESS;
    }

    /**
     * Attempt to resolve a missing, out-of-date, invalid or disabled Google
     * Play Services installation via a user dialog, if possible.
     */
    private void acquireGooglePlayServices() {
        GoogleApiAvailability apiAvailability =
                GoogleApiAvailability.getInstance();
        final int connectionStatusCode =
                apiAvailability.isGooglePlayServicesAvailable(this);
        if (apiAvailability.isUserResolvableError(connectionStatusCode)) {
            showGooglePlayServicesAvailabilityErrorDialog(connectionStatusCode);
        }
    }


    /**
     * Display an error dialog showing that Google Play Services is missing
     * or out of date.
     * @param connectionStatusCode code describing the presence (or lack of)
     *     Google Play Services on this device.
     */
    void showGooglePlayServicesAvailabilityErrorDialog(
            final int connectionStatusCode) {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        Dialog dialog = apiAvailability.getErrorDialog(
                MainActivity.this,
                connectionStatusCode,
                REQUEST_GOOGLE_PLAY_SERVICES);
        dialog.show();
    }

    /**
     * An asynchronous task that handles the YouTube Data API call.
     * Placing the API calls in their own task ensures the UI stays responsive.
     */
   /* private class MakeRequestTask extends AsyncTask<Void, Void, List<String>> {
        private com.google.api.services.youtube.YouTube mService = null;
        private Exception mLastError = null;

        MakeRequestTask(GoogleAccountCredential credential) {
            HttpTransport transport = AndroidHttp.newCompatibleTransport();
            JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
            mService = new com.google.api.services.youtube.YouTube.Builder(
                    transport, jsonFactory, credential)
                    .setApplicationName("YouTube Data API Android Quickstart")
                    .build();
        }

        /**
         * Background task to call YouTube Data API.
         * @param params no parameters needed for this task.

        @Override
        protected List<String> doInBackground(Void... params) {
            try {
                return getDataFromApi();
            } catch (Exception e) {
                mLastError = e;
                cancel(true);
                return null;
            }
        }

        /**
         * Fetch information about the "GoogleDevelopers" YouTube channel.
         * @return List of Strings containing information about the channel.
         * @throws IOException

        private List<String> getDataFromApi() throws IOException {
            // Get a list of up to 10 files.
            List<String> channelInfo = new ArrayList<String>();
            ChannelListResponse result = mService.channels().list("snippet,contentDetails,statistics")
                    .setForUsername("GoogleDevelopers")
                    .execute();
            List<Channel> channels = result.getItems();
            if (channels != null) {
                Channel channel = channels.get(0);
                channelInfo.add("This channel's ID is " + channel.getId() + ". " +
                        "Its title is '" + channel.getSnippet().getTitle() + ", " +
                        "and it has " + channel.getStatistics().getViewCount() + " views.");
            }
            return channelInfo;
        }

        @Override
        protected void onPreExecute() {
            mOutputText.setText("");
            mProgress.show();
        }

        @Override
        protected void onPostExecute(List<String> output) {
          mProgress.hide();
            if (output == null || output.size() == 0) {
                mOutputText.setText("No results returned.");
            } else {
                output.add(0, "Data retrieved using the YouTube Data API:");
                mOutputText.setText(TextUtils.join("\n", output));
            }
        }

        @Override
        protected void onCancelled() {
            mProgress.hide();
            if (mLastError != null) {
                if (mLastError instanceof GooglePlayServicesAvailabilityIOException) {
                    showGooglePlayServicesAvailabilityErrorDialog(
                            ((GooglePlayServicesAvailabilityIOException) mLastError)
                                    .getConnectionStatusCode());
                } else if (mLastError instanceof UserRecoverableAuthIOException) {
                    startActivityForResult(
                            ((UserRecoverableAuthIOException) mLastError).getIntent(),
                            MainActivity.REQUEST_AUTHORIZATION);
                } else {
                    mOutputText.setText("The following error occurred:\n"
                            + mLastError.getMessage());
                }
            } else {
                mOutputText.setText("Request cancelled.");
            }
        }
    }
    */

    private static File getFileFromUri(Uri uri, Activity activity) {

        Log.d("URI Path", uri.toString());

        try {
            String filePath = null;

            String[] proj = {MediaStore.Video.VideoColumns.DATA};

            Cursor cursor = activity.getContentResolver().query(uri, proj, null, null, null);

            if (cursor.moveToFirst()) {
                int column_index = cursor.getColumnIndexOrThrow(MediaStore.Video.VideoColumns.DATA);
                filePath = cursor.getString(column_index);
            }

            cursor.close();

            File file = new File(filePath);
            cursor.close();
            return file;
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }


    private AbstractInputStreamContent getMediaContent(File file) throws FileNotFoundException {
        InputStreamContent mediaContent = new InputStreamContent(
                "video/*",
                new BufferedInputStream(new FileInputStream(file)));
        mediaContent.setLength(file.length());

        return mediaContent;
    }
}