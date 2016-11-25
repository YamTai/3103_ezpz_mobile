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

    private final int FIELDS_OK = 0;
    private final int PASSWORD_NOT_MATCHING = 1;
    private final int EMPTY_FIELD = 2;
    private final int EMAIL_FORMAT_ERROR = 3;
    private final int PASSWORD_FORMAT_ERROR = 4;
    private final int EMAIL_TOO_LONG = 5;
    private final int PASSWORD_TOO_LONG = 6;

    private FirebaseServices mService;
    private ServiceConnection mConnection;
    private boolean mBound = false;

    private EditText mEmail, mPassword, mConfirmedPassword;

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
        mConfirmedPassword  = (EditText) findViewById(R.id.editText_register_confirmedPassword);
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
        if (item.getItemId() == android.R.id.home){ //  B-05
            goToLogin();
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
            switch(validate()){
                case FIELDS_OK:{
                    String email = mEmail.getText().toString();
                    String password = mPassword.getText().toString();
                    mService.register(email, password, new RegistrationResultReceiver(this, new Handler()));
                    break;
                }
                case PASSWORD_NOT_MATCHING:{
                    Toast.makeText(this, R.string.register_toast_registration_passwordNotMatching, Toast.LENGTH_SHORT).show();
                    break;
                }
                case EMPTY_FIELD:{
                    Toast.makeText(this, R.string.register_toast_registration_emptyFields, Toast.LENGTH_SHORT).show();
                    break;
                }
                case EMAIL_FORMAT_ERROR:{
                    Toast.makeText(this, R.string.register_toast_registration_emailFormatError, Toast.LENGTH_SHORT).show();
                    break;
                }
                case PASSWORD_FORMAT_ERROR:{
                    Toast.makeText(this, R.string.register_toast_registration_passwordFormatError, Toast.LENGTH_SHORT).show();
                    break;
                }
                case EMAIL_TOO_LONG:{
                    Toast.makeText(this, R.string.register_toast_registration_emailTooLong, Toast.LENGTH_SHORT).show();
                    break;
                }
                case PASSWORD_TOO_LONG:{
                    Toast.makeText(this, R.string.register_toast_registration_passwordTooLong, Toast.LENGTH_SHORT).show();
                    break;
                }
                default:{
                    //  do nothing
                }
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
    private int validate(){
        String email = null, password = null, confirmedPassword = null;
        if (mEmail != null){
            email = mEmail.getText().toString();
            if (email.length() > 110){
                return EMAIL_TOO_LONG;
            }else if (!email.matches("[a-z,A-Z,0-9,!#$%&'*+-/=?^_`{|}~]{2,50}@[a-zA-Z]{2,50}\\.+[a-zA-Z]{2,5}(\\.[a-zA-Z]{2,5})?")){    //  B-02
                return EMAIL_FORMAT_ERROR;
            }
        }
        if (mPassword != null){
            password = mPassword.getText().toString();
            if (password.length() > 30){
                return PASSWORD_TOO_LONG;
            }else if (!password.matches("(?=.*[A-Za-z!@#$%^&*])(?=.*\\d)[A-Za-z!@#$%^&*\\d]{7,30}")){   //  B-02
                return PASSWORD_FORMAT_ERROR;
            }
        }
        if (mConfirmedPassword != null){
            confirmedPassword = mConfirmedPassword.getText().toString();
        }
        if (email != null && password != null && confirmedPassword != null){
            if (email.length() > 0 && password.length() > 0 && confirmedPassword.length() > 0){
                if (password.equals(confirmedPassword)){
                    return FIELDS_OK;
                }else{
                    return PASSWORD_NOT_MATCHING;
                }
            }
        }
        return EMPTY_FIELD;
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
