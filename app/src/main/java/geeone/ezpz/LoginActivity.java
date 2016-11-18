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
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

public class LoginActivity extends AppCompatActivity {

    private FirebaseServices mService;
    private ServiceConnection mConnection;
    private boolean mBound = false;

    private EditText mEmail, mPassword;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        setTitle(R.string.login_title);

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
        mEmail = (EditText) findViewById(R.id.editText_login_email);
        mPassword = (EditText) findViewById(R.id.editText_login_password);

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

    @SuppressWarnings("UnusedParameters")
    public void login(View v){
        if (mBound){
            if (validate()) {
                mService.authenticate(mEmail.getText().toString(), mPassword.getText().toString(), new LoginResultReceiver(this, new Handler()));
            }
        }
    }

    @SuppressWarnings("UnusedParameters")
    public void register(View v){
        if (mBound){
            if (validate()){
                String email = mEmail.getText().toString();
                String password = mPassword.getText().toString();
                mService.register(email, password, new LoginResultReceiver(this, new Handler()));
            }
        }
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
    private class LoginResultReceiver extends ResultReceiver{
        private final Context context;
        private LoginResultReceiver(Context c, Handler h){
            super(h);
            context = c;
        }
        @Override
        public void onReceiveResult(int resultCode, Bundle resultData) {
            switch(resultCode){
                case FirebaseServices.AUTHENTICATE_RESULT_CODE:{
                    boolean authenticated = resultData.getBoolean(FirebaseServices.AUTHENTICATE_RESULT_DATA, false);
                    if (authenticated){
                        Toast.makeText(context, R.string.login_toast_login_success, Toast.LENGTH_SHORT).show();
                        Intent i = new Intent(context, MainActivity.class);
                        unbindService(mConnection);
                        startActivity(i);
                    }else{
                        Toast.makeText(context, R.string.login_toast_login_fail, Toast.LENGTH_SHORT).show();
                    }
                    break;
                }
                case FirebaseServices.AUTHENTICATE_REGISTER_CODE:{
                    boolean registered = resultData.getBoolean(FirebaseServices.AUTHENTICATE_REGISTER_DATA, false);
                    if (registered){
                        Toast.makeText(context, "Registration Successful", Toast.LENGTH_SHORT).show();
                    }else{
                        Toast.makeText(context, "Registration failed", Toast.LENGTH_SHORT).show();
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
