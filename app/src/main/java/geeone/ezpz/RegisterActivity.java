package geeone.ezpz;

import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.ResultReceiver;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

public class RegisterActivity extends AppCompatActivity {

    private FirebaseServices mService;
    private ServiceConnection mConnection;
    private boolean mBound = false;

    private EditText mEmail, mPassword;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);
        setTitle(R.string.register_title);

        //  service connection to FirebaseServices
        mConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                mService = ((FirebaseServices.LocalBinder)service).getService();
                mBound = true;
            }
            @Override
            public void onServiceDisconnected(ComponentName name) {
                mService = null;
                mBound = false;
            }
        };
    }

    @Override
    protected void onStart(){
        super.onStart();

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        mEmail = (EditText) findViewById(R.id.editText_register_email);
        mPassword = (EditText) findViewById(R.id.editText_register_password);

        //  checks if phone is rooted
        if (!RootChecker.isDeviceRooted()){
            if (!mBound){
                Intent i = new Intent(this, FirebaseServices.class);
                startService(i);
                bindService(i, mConnection, BIND_AUTO_CREATE);
            }
        }else{
            AlertDialog noStart = new AlertDialog.Builder(this).create();
            noStart.setTitle(R.string.appStartFailedDialog_title);
            noStart.setMessage(getString(R.string.appStartFailedDialog_content));
            noStart.setButton(AlertDialog.BUTTON_NEUTRAL, getString(R.string.appStartFailedDialog_button_close), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    finish();
                }
            });
            noStart.show();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:{
                goToLogin();
            }
            default:{
                //  do nothing
            }
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed(){
        super.onBackPressed();
        if (mBound){
            unbindService(mConnection);
        }
    }

    @SuppressWarnings("UnusedParameters")
    public void register(View v){
        if (mBound){
            if (validate()){
                String email = mEmail.getText().toString();
                String password = mPassword.getText().toString();
                mService.register(email, password, new RegistrationResultReceiver(this, new Handler()));
            }
        }
    }

    public void clear(View v){
        if (mEmail != null){
            mEmail.setText("");
        }
        if (mPassword != null){
            mPassword.setText("");
        }
    }

    private void goToLogin(){
        Intent i = new Intent(this, LoginActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
        if (mBound){
            unbindService(mConnection);
        }
        startActivity(i);
    }

    /*
        description: simple validation
     */
    private boolean validate(){
        String email = null, password = null;
        if (mEmail != null){
            email = mEmail.getText().toString();
        }
        if (mPassword != null){
            password = mPassword.getText().toString();
        }
        if (email != null && password != null){
            if (email.length() > 0 && password.length() > 0){
                return true;
            }
        }
        return false;
    }

    /*
        description: custom resultreceiver for receiving results from FirebaseServices
     */
    private class RegistrationResultReceiver extends ResultReceiver{
        private final Context context;
        private RegistrationResultReceiver(Context c, Handler h){
            super(h);
            context = c;
        }
        @Override
        public void onReceiveResult(int resultCode, Bundle resultData) {
            switch(resultCode){
                case FirebaseServices.AUTHENTICATE_REGISTER_CODE:{
                    boolean registered = resultData.getBoolean(FirebaseServices.AUTHENTICATE_REGISTER_DATA, false);
                    if (registered){
                        Toast.makeText(context, R.string.register_toast_registration_success, Toast.LENGTH_SHORT).show();
                        goToLogin();
                    }else{
                        Toast.makeText(context, R.string.register_toast_registration_failed, Toast.LENGTH_SHORT).show();
                    }
                    break;
                }
                default:{
                    //  do nothing
                }
            }
        }
    }
}
