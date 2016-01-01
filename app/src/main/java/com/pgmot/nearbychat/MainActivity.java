package com.pgmot.nearbychat;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.messages.Message;
import com.google.android.gms.nearby.messages.MessageListener;
import com.google.android.gms.nearby.messages.PublishOptions;

import butterknife.Bind;
import butterknife.ButterKnife;

public class MainActivity extends AppCompatActivity implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    @Bind(R.id.listView)
    ListView listView;
    @Bind(R.id.button)
    Button submitButton;
    @Bind(R.id.editText)
    EditText chatEditText;

    private ArrayAdapter<String> listViewAdapter;
    private boolean isListViewTop;
    private boolean isListViewBottom;
    private GoogleApiClient googleApiClient;
    private static final int REQUEST_RESOLVE_ERROR = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        googleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Nearby.MESSAGES_API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
        googleApiClient.connect();

        listViewAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        listView.setAdapter(listViewAdapter);

        chatEditText.setHint("input message");

        // ListViewが一番下か一番上かのフラグを取る
        listView.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {

            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                if (view.getChildCount() == 0) {
                    isListViewTop = true;
                    isListViewBottom = true;
                } else {
                    isListViewTop = view.getFirstVisiblePosition() == 0
                            && view.getChildAt(0).getTop() == view.getPaddingTop();

                    isListViewBottom = view.getLastVisiblePosition() == totalItemCount - 1
                            && view.getChildAt(view.getChildCount() - 1).getBottom() <= view.getBottom() - view.getPaddingBottom();
                }

                Log.v("log", "isListViewTop: " + (isListViewTop ? "true" : "false"));
                Log.v("log", "isListViewBottom: " + (isListViewBottom ? "true" : "false"));
            }
        });

        submitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String chatText = chatEditText.getText().toString();
                if ("".equals(chatText)) {
                    return;
                }

                chatEditText.setHint("sending message...");
                chatEditText.getText().clear();
                if (getCurrentFocus() != null) {
                    // IMEを隠す
                    ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
                }

                sendMessage(chatText);
            }
        });
    }

    private void addMessage(String message) {
        listViewAdapter.add(message);

        if (isListViewBottom) {
            // ListViewの位置を下に移動
            listView.setSelection(listViewAdapter.getCount());
        }
    }

    private void sendMessage(final String message) {
        Nearby.Messages.publish(googleApiClient, new Message(message.getBytes()), PublishOptions.DEFAULT)
                .setResultCallback(new ResultCallback<Status>() {
                    @Override
                    public void onResult(@NonNull Status status) {
                        chatEditText.setHint("input message");
                        addMessage(message);
                    }
                });
    }

    @Override
    public void onConnected(Bundle bundle) {
        Nearby.Messages.getPermissionStatus(googleApiClient)
                .setResultCallback(new ResultCallback<Status>() {
                    @Override
                    public void onResult(@NonNull Status status) {
                        if (status.isSuccess()) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Nearby.Messages.subscribe(googleApiClient, messageListener);
                                }
                            });
                        } else {
                            if (status.hasResolution()) {
                                try {
                                    status.startResolutionForResult(MainActivity.this, REQUEST_RESOLVE_ERROR);
                                } catch (IntentSender.SendIntentException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    }
                });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_RESOLVE_ERROR) {
            if (resultCode == Activity.RESULT_OK) {
                Nearby.Messages.subscribe(googleApiClient, messageListener);
            }
        }
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    @Override
    protected void onStart() {
        super.onStart();

        if (!googleApiClient.isConnected()) {
            // バックグラウンドから復帰したら接続し直す
            googleApiClient.connect();
        }
    }

    @Override
    protected void onStop() {
        if (googleApiClient.isConnected()) {
            Nearby.Messages.unsubscribe(googleApiClient, messageListener);
        }
        googleApiClient.disconnect();

        super.onStop();
    }

    MessageListener messageListener = new MessageListener() {
        @Override
        public void onFound(Message message) {
            addMessage(new String(message.getContent()));
        }
    };
}
