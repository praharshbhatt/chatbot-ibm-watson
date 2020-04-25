package com.example.vmac.WatBot;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import com.ibm.cloud.sdk.core.http.HttpMediaType;
import com.ibm.cloud.sdk.core.http.Response;
import com.ibm.cloud.sdk.core.http.ServiceCall;
import com.ibm.cloud.sdk.core.security.IamAuthenticator;
import com.ibm.watson.assistant.v2.model.DialogNodeOutputOptionsElement;
import com.ibm.watson.assistant.v2.model.RuntimeEntity;
import com.ibm.watson.assistant.v2.model.RuntimeIntent;
import com.ibm.watson.assistant.v2.model.RuntimeResponseGeneric;
import com.ibm.watson.developer_cloud.android.library.audio.MicrophoneHelper;
import com.ibm.watson.developer_cloud.android.library.audio.MicrophoneInputStream;
import com.ibm.watson.developer_cloud.android.library.audio.StreamPlayer;
import com.ibm.watson.developer_cloud.android.library.audio.utils.ContentType;
import com.ibm.watson.assistant.v2.Assistant;
import com.ibm.watson.assistant.v2.model.CreateSessionOptions;
import com.ibm.watson.assistant.v2.model.MessageInput;
import com.ibm.watson.assistant.v2.model.MessageOptions;
import com.ibm.watson.assistant.v2.model.MessageResponse;
import com.ibm.watson.assistant.v2.model.SessionResponse;
import com.ibm.watson.speech_to_text.v1.SpeechToText;
import com.ibm.watson.speech_to_text.v1.model.RecognizeOptions;
import com.ibm.watson.speech_to_text.v1.model.SpeechRecognitionResults;
import com.ibm.watson.speech_to_text.v1.websocket.BaseRecognizeCallback;
import com.ibm.watson.text_to_speech.v1.TextToSpeech;
import com.ibm.watson.text_to_speech.v1.model.SynthesizeOptions;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {


    private RecyclerView recyclerView;
    private ChatAdapter mAdapter;
    private ArrayList messageArrayList;
    private EditText inputMessage;
    private ImageButton btnSend;
    private ImageButton btnRecord;
    StreamPlayer streamPlayer = new StreamPlayer();
    private boolean initialRequest;
    private boolean permissionToRecordAccepted = false;
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private static String TAG = "MainActivity";
    private static final int RECORD_REQUEST_CODE = 101;
    private boolean listening = false;


    //Speech Recognizer
    final SpeechRecognizer mSpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
    final Intent mSpeechRecognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);

//    private MicrophoneInputStream capture;
    private Context mContext;
//    private MicrophoneHelper;

    private Assistant watsonAssistant;
    private Response<SessionResponse> watsonAssistantSession;
    //    private SpeechToText speechService;
    private TextToSpeech textToSpeech;
    String strLastMessage="";

    private void createServices() {
        watsonAssistant = new Assistant("2019-02-28", new IamAuthenticator(mContext.getString(R.string.assistant_apikey)));
        watsonAssistant.setServiceUrl(mContext.getString(R.string.assistant_url));

        textToSpeech = new TextToSpeech(new IamAuthenticator((mContext.getString(R.string.TTS_apikey))));
        textToSpeech.setServiceUrl(mContext.getString(R.string.TTS_url));

//        speechService = new SpeechToText(new IamAuthenticator(mContext.getString(R.string.STT_apikey)));
//        speechService.setServiceUrl(mContext.getString(R.string.STT_url));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mContext = getApplicationContext();

        inputMessage = findViewById(R.id.message);
        btnSend = findViewById(R.id.btn_send);
        btnRecord = findViewById(R.id.btn_record);
        String customFont = "Montserrat-Regular.ttf";
        Typeface typeface = Typeface.createFromAsset(getAssets(), customFont);
        inputMessage.setTypeface(typeface);
        recyclerView = findViewById(R.id.recycler_view);

        //For Microphone
        messageArrayList = new ArrayList<>();
        mAdapter = new ChatAdapter(messageArrayList);

        mSpeechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        mSpeechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        mSpeechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle bundle) {}

            @Override
            public void onBeginningOfSpeech() {}

            @Override
            public void onRmsChanged(float v) {}

            @Override
            public void onBufferReceived(byte[] bytes) {}

            @Override
            public void onEvent(int i, Bundle bundle) {}

            @Override
            public void onEndOfSpeech() {

            }

            @Override
            public void onError(int i) {
                enableMicButton();
            }

            @Override
            public void onResults(Bundle bundle) {
                //getting all the matches
                ArrayList<String> matches = bundle
                        .getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);

                //displaying the first match
                if (matches != null){
                    String strTMP=matches.get(0);
                    if (checkInternetConnection() && !strLastMessage.equals(strTMP)) {
                        strLastMessage=matches.get(0);
                        showMicText(strLastMessage);
                        sendMessage();
                        enableMicButton();
                    } else {
                    }
                }
            }

            @Override
            public void onPartialResults(Bundle bundle) {
                ArrayList<String> matches = bundle
                        .getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);

                //displaying the first match
                if (matches != null) showMicText(matches.get(0));
            }
        });




//        microphoneHelper = new MicrophoneHelper(this);

        //Load the layouts
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setItemAnimator(new DefaultItemAnimator());
        recyclerView.setAdapter(mAdapter);
        this.inputMessage.setText("");
        this.initialRequest = true;


        //Audio Record permission
        int permission = ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO);

        if (permission != PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "Permission to record denied");
            makeRequest();
        } else {
            Log.i(TAG, "Permission to record was already granted");
        }

        //Access Contacts Permission
        permission = ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_CONTACTS);

        if (permission != PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "Permission to Call denied");
            makeRequest();
        } else {
            Log.i(TAG, "Permission to Call was already granted");
        }

        recyclerView.addOnItemTouchListener(new RecyclerTouchListener(getApplicationContext(), recyclerView, new ClickListener() {
            @Override
            public void onClick(View view, final int position) {
                Message audioMessage = (Message) messageArrayList.get(position);
                if (audioMessage != null && !audioMessage.getMessage().isEmpty()) {
                    new SayTask().execute(audioMessage.getMessage());
                }
            }

            @Override
            public void onLongClick(View view, int position) {
                recordMessage();
            }
        }));

        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (checkInternetConnection()) {
                    sendMessage();
                }
            }
        });

        btnRecord.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                recordMessage();
            }
        });

        createServices();
        sendMessage();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.refresh:
                finish();
                startActivity(getIntent());

        }
        return (super.onOptionsItemSelected(item));
    }


    // Speech-to-Text Record Audio permission
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQUEST_RECORD_AUDIO_PERMISSION:
                permissionToRecordAccepted = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                break;
            case RECORD_REQUEST_CODE: {

                if (grantResults.length == 0
                        || grantResults[0] !=
                        PackageManager.PERMISSION_GRANTED) {

                    Log.i(TAG, "Permission has been denied by user");
                } else {
                    Log.i(TAG, "Permission has been granted by user");
                }
                return;
            }

            case MicrophoneHelper.REQUEST_PERMISSION: {
                if (grantResults.length > 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Permission to record audio denied", Toast.LENGTH_SHORT).show();
                }
            }
        }
        // if (!permissionToRecordAccepted ) finish();

    }


    /**
     * Check Internet Connection
     *
     * @return
     */
    private boolean checkInternetConnection() {
        // get Connectivity Manager object to check connection
        ConnectivityManager cm =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        boolean isConnected = activeNetwork != null &&
                activeNetwork.isConnectedOrConnecting();

        // Check for network connections
        if (isConnected) {
            return true;
        } else {
            Toast.makeText(this, " No Internet Connection available ", Toast.LENGTH_LONG).show();
            return false;
        }

    }


    //PERMISSIONS
    protected void makeRequest() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE, Manifest.permission.SEND_SMS},
                MicrophoneHelper.REQUEST_PERMISSION);
    }


    // Sending a message to Watson Assistant Service
    private void sendMessage() {

        final String inputmessage = this.inputMessage.getText().toString().trim();
        if (!this.initialRequest) {
            Message inputMessage = new Message();
            inputMessage.setMessage(inputmessage);
            inputMessage.setId("1");
            messageArrayList.add(inputMessage);
        } else {
            Message inputMessage = new Message();
            inputMessage.setMessage(inputmessage);
            inputMessage.setId("100");
            this.initialRequest = false;
            Toast.makeText(getApplicationContext(), "Tap on the message for Voice", Toast.LENGTH_LONG).show();
        }

        this.inputMessage.setText("");
        mAdapter.notifyDataSetChanged();

        Thread thread = new Thread(new Runnable() {
            public void run() {
                try {
                    if (watsonAssistantSession == null) {
                        ServiceCall<SessionResponse> call = watsonAssistant.createSession(new CreateSessionOptions.Builder().assistantId(mContext.getString(R.string.assistant_id)).build());
                        watsonAssistantSession = call.execute();
                    }

                    MessageInput input = new MessageInput.Builder()
                            .text(inputmessage)
                            .build();
                    MessageOptions options = new MessageOptions.Builder()
                            .assistantId(mContext.getString(R.string.assistant_id))
                            .input(input)
                            .sessionId(watsonAssistantSession.getResult().getSessionId())
                            .build();
                    Response<MessageResponse> response = watsonAssistant.message(options).execute();
                    Log.i(TAG, "run: " + response.getResult());

                    if (response != null &&
                            response.getResult().getOutput() != null &&
                            !response.getResult().getOutput().getGeneric().isEmpty()) {


                        List<RuntimeResponseGeneric> responses = response.getResult().getOutput().getGeneric();

                        //For speaking
                        for (RuntimeResponseGeneric r : responses) {
                            Message outMessage;
                            switch (r.responseType()) {
                                case "text":
                                    outMessage = new Message();
                                    outMessage.setMessage(r.text());
                                    outMessage.setId("2");

                                    messageArrayList.add(outMessage);

                                    // speak the message
                                    new SayTask().execute(outMessage.getMessage());
                                    break;

                                case "option":
                                    outMessage = new Message();
                                    String title = r.title();
                                    String OptionsOutput = "";
                                    for (int i = 0; i < r.options().size(); i++) {
                                        DialogNodeOutputOptionsElement option = r.options().get(i);
                                        OptionsOutput = OptionsOutput + option.getLabel() + "\n";

                                    }
                                    outMessage.setMessage(title + "\n" + OptionsOutput);
                                    outMessage.setId("2");

                                    messageArrayList.add(outMessage);

                                    // speak the message
                                    new SayTask().execute(outMessage.getMessage());
                                    break;

                                case "image":
                                    outMessage = new Message(r);
                                    messageArrayList.add(outMessage);

                                    // speak the description
                                    new SayTask().execute("You received an image: " + outMessage.getTitle() + outMessage.getDescription());
                                    break;
                                default:
                                    Log.e("Error", "Unhandled message type");
                            }
                        }

                        runOnUiThread(new Runnable() {
                            public void run() {
                                mAdapter.notifyDataSetChanged();
                                if (mAdapter.getItemCount() > 1) {
                                    recyclerView.getLayoutManager().smoothScrollToPosition(recyclerView, null, mAdapter.getItemCount() - 1);

                                }

                            }
                        });


                        //Clear last string
                        strLastMessage="";

                        //For Executing the function
                        List<RuntimeIntent> lstIntents = response.getResult().getOutput().getIntents();
                        List<RuntimeEntity> lstEntities = response.getResult().getOutput().getEntities();
                        Log.i(TAG, "Intents: " + response.getResult().getOutput().getIntents());
                        Log.i(TAG, "Entities: " + response.getResult().getOutput().getEntities());

                        //Get the most confident intent
                        RuntimeIntent intent = null;
                        for (RuntimeIntent intent1 : lstIntents) {
                            if (intent == null) {
                                intent = intent1;
                            } else if (intent.confidence() < intent1.confidence()) {
                                intent = intent1;
                            }
                        }

                        mainFunction(intent, lstEntities);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        thread.start();
    }


    //Record a message via Watson Speech to Text
    private void recordMessage() {
        if (listening != true) {
            mSpeechRecognizer.startListening(mSpeechRecognizerIntent);
            listening = true;
            Toast.makeText(MainActivity.this, "Listening....Click to Stop", Toast.LENGTH_LONG).show();
//            capture = microphoneHelper.getInputStream(true);
//            new Thread(new Runnable() {
//                @Override
//                public void run() {
//                    try {
//                        speechService.recognizeUsingWebSocket(getRecognizeOptions(capture), new MicrophoneRecognizeDelegate());
//                    } catch (Exception e) {
//                        showError(e);
//                    }
//                }
//            }).start();

        } else {
            mSpeechRecognizer.stopListening();
            listening = false;
//            try {
//                microphoneHelper.closeInputStream();
//                listening = false;
//                Toast.makeText(MainActivity.this, "Stopped Listening....Click to Start", Toast.LENGTH_LONG).show();
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
        }
    }



//    //Private Methods - Speech to Text
//    private RecognizeOptions getRecognizeOptions(InputStream audio) {
//        return new RecognizeOptions.Builder()
//                .audio(audio)
//                .contentType(ContentType.OPUS.toString())
//                .model("en-US_BroadbandModel")
//                .interimResults(true)
//                .inactivityTimeout(2000)
//                .build();
//    }

    private void showMicText(final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                inputMessage.setText(text);
            }
        });
    }

    private void enableMicButton() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                btnRecord.setEnabled(true);
            }
        });
    }

    private void showError(final Exception e) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
                e.printStackTrace();
            }
        });
    }

    //Watson Speech to Text Methods.
    private class MicrophoneRecognizeDelegate extends BaseRecognizeCallback {
        @Override
        public void onTranscription(SpeechRecognitionResults speechResults) {
            if (speechResults.getResults() != null && !speechResults.getResults().isEmpty()) {
                String text = speechResults.getResults().get(0).getAlternatives().get(0).getTranscript();
                showMicText(text);
            }
        }

        @Override
        public void onError(Exception e) {
            showError(e);
            enableMicButton();
        }

        @Override
        public void onDisconnected() {
            enableMicButton();
        }

    }












    //For Speaking
    private class SayTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... params) {
            streamPlayer.playStream(textToSpeech.synthesize(new SynthesizeOptions.Builder()
                    .text(params[0])
                    .voice(SynthesizeOptions.Voice.EN_US_LISAVOICE)
                    .accept(HttpMediaType.AUDIO_WAV)
                    .build()).execute().getResult());
            return "Did synthesize";
        }
    }


    //IBM Watson Dashboard link: https://eu-gb.assistant.watson.cloud.ibm.com/eu-gb/crn:v1:bluemix:public:conversation:eu-gb:a~2F3ae22790ecf84e0a8898b55c4375073c:4c7a928f-1e7a-42e2-9ed7-a984760ab5a8::/skills/05b68df9-9713-444e-ab17-5aa7c1e9a4e8/build/intents

    //====================================MAIN FUNCTIONS==================================
    private void mainFunction(RuntimeIntent jobIntent, List<RuntimeEntity> lstEntities) {


        if (jobIntent.intent().equals("open_app")) {
            //For Opening Apps
            Log.d(TAG, "mainFunction: Open app");

            String strAppName = getEntity("app_name", lstEntities).value();
            if (!openApp(strAppName)) {
                new SayTask().execute("The " + strAppName + " does not appear to be installed.");
            }


        } else if (jobIntent.intent().equals("call")) {
            //For Calling
            Log.d(TAG, "mainFunction: Calling");

            String strContactName = "";
            if (getEntity("person", lstEntities).value() != null) {
                strContactName = getEntity("person", lstEntities).value();
                Log.d(TAG, "Call : " + strContactName);

                //Find the Contact Phone Number
                String strContactPhoneNumber = getPhone(strContactName, getApplicationContext());
                if (strContactPhoneNumber == null)
                    new SayTask().execute("No Phone number for the  " + strContactPhoneNumber + " found.");

                //Request permission is not given
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                    makeRequest();
                    return;
                }

                //Call
                Intent intent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + strContactPhoneNumber));
                startActivity(intent);
                new SayTask().execute("No Phone number for the  " + strContactPhoneNumber + " found.");

            } else if (getEntity("sys-person", lstEntities).value() != null) {
                strContactName = getEntity("sys-person", lstEntities).value();

                //Find the Contact Phone Number
                String strContactPhoneNumber = getPhone(strContactName, getApplicationContext());
                if (strContactPhoneNumber == null)
                    new SayTask().execute("No Phone number for the  " + strContactPhoneNumber + " found.");


                //Request permission is not given
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                    makeRequest();
                    return;
                }


                //Call
                Intent intent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + strContactPhoneNumber));
                startActivity(intent);
                new SayTask().execute("No Phone number for the  " + strContactPhoneNumber + " found.");

            } else {
                new SayTask().execute("The Caller name could not be identified. Please try again later.");
            }


        } else if (jobIntent.intent().equals("sms")) {
            //For SMS
            Log.d(TAG, "mainFunction: SMS");

            String strContactName = "";
            if (getEntity("person", lstEntities).value() != null) {
                strContactName = getEntity("person", lstEntities).value();
                //Find the Contact Phone Number
                String strContactPhoneNumber = getPhone(strContactName, getApplicationContext());
                if (strContactPhoneNumber == null)
                    new SayTask().execute("No Phone number for the  " + strContactPhoneNumber + " found.");

                //Request permission is not given
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
                    makeRequest();
                    return;
                }

                //Get SMS Body
                String strMessageBody = "";
                strMessageBody = getEntity("message_body", lstEntities).value();
                if (strMessageBody == null || strMessageBody.equals("")) {
                    new SayTask().execute("No SMS body found.");
                    return;
                }

                //SMS
                sendSMS(strContactPhoneNumber, strMessageBody);
                new SayTask().execute("Message sent to " + strContactPhoneNumber + ".");

            } else if (getEntity("sys-person", lstEntities).value() != null) {
                strContactName = getEntity("sys-person", lstEntities).value();

                //Find the Contact Phone Number
                String strContactPhoneNumber = getPhone(strContactName, getApplicationContext());
                if (strContactPhoneNumber == null)
                    new SayTask().execute("No Phone number " + strContactPhoneNumber + " found.");

                //Request permission is not given
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
                    makeRequest();
                    return;
                }

                //Get SMS Body
                String strMessageBody = "";
                strMessageBody = getEntity("message_body", lstEntities).value();
                if (strMessageBody == null || strMessageBody.equals("")) {
                    new SayTask().execute("No SMS body found.");
                    return;
                }

                //SMS
                sendSMS(strContactPhoneNumber, strMessageBody);
                new SayTask().execute("Message sent to " + strContactPhoneNumber + ".");

            } else {
                new SayTask().execute("The Caller name could not be identified. Please try again later.");
            }

        } else if (jobIntent.intent().equals("search_query")) {
            //For Search Queries
            Log.d(TAG, "mainFunction: Search");

            String strSearch = getEntity("search_query", lstEntities).value();
            if (strSearch == null || strSearch.equals("")) {
                new SayTask().execute("I do not know what to search, Please try searching any other way.");
                return;
            }

            //Search in Google
            String url = "http://www.google.com/search?q=" + strSearch;
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setData(Uri.parse(url));
            startActivity(i);
        }
    }

    private RuntimeEntity getEntity(String strSearch, List<RuntimeEntity> lstEntities) {
        //Get the correct entity
        RuntimeEntity entity = null;
        for (RuntimeEntity entity1 : lstEntities) {
            if (entity == null) {
                entity = entity1;
            } else if (entity.entity().equals(strSearch)) {
                entity = entity1;
            }
        }

        return entity;
    }


    //=========================== App Functions ===========================
    //For Opening apps
    boolean openApp(String appName) {
        boolean blAppFound = false;

        final PackageManager pm = getApplicationContext().getPackageManager();
        List<ApplicationInfo> lstInstalledApps = pm.getInstalledApplications(0);

        for (int i = 0; i < lstInstalledApps.size() - 1; i++) {
            ApplicationInfo ai = lstInstalledApps.get(i);

            final String applicationName = (String) (ai != null ? pm.getApplicationLabel(ai) : "unknown");
            Log.d("", applicationName);
            if (!applicationName.equals("unknown") && applicationName.toLowerCase().trim().equals(appName.toLowerCase().trim())) {
                //The actual code for opening apps
                Intent launchIntent = getPackageManager().getLaunchIntentForPackage(ai.packageName);
                if (launchIntent != null) {
                    startActivity(launchIntent);//null pointer check in case package name was not found
                }
                blAppFound = true;
                break;
            }
        }
        return blAppFound;
    }


    //Get Phone Number from a contact name
    public String getPhone(final String contactName, Context context) {
        String result = null;
        String selection = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " like'%" + contactName + "%'";
        String[] projection = new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER};
        Cursor c = context.getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection, selection, null, null);
        if (c.moveToFirst()) {
            result = c.getString(0);
        }
        c.close();
        if (result == null)
            result = "This contact is not saved into your device";
        return result;
    }

    public String getContactName(final String phoneNumber, Context context) {
        Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber));

        String[] projection = new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME};

        String contactName = "";
        Cursor cursor = context.getContentResolver().query(uri, projection, null, null, null);

        if (cursor != null) {
            if (cursor.moveToFirst()) {
                contactName = cursor.getString(0);
            }
            cursor.close();
        }

        return contactName;
    }

    //Send SMS
    protected void sendSMS(String strContact, String strMessageBody) {
        Uri uri = Uri.parse("smsto:" + strContact);
        Intent intent = new Intent(Intent.ACTION_SENDTO, uri);
        intent.putExtra("sms_body", strMessageBody);
        startActivity(intent);
    }
}



