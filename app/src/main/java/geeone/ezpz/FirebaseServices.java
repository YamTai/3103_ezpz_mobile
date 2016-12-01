package geeone.ezpz;

import android.app.ActivityManager;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ResultReceiver;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import DatabaseStructure.FileMetadata;

/**
 * Created by YamTai on 7.10.16.
 */

@SuppressWarnings({"FieldCanBeLocal", "DefaultFileTemplate"})
public class FirebaseServices extends Service {

    //  result receiver variables
    public static final int AUTHENTICATE_RESULT_CODE = 0;
    public static final String AUTHENTICATE_RESULT_DATA = "geeone.ezpz.AUTHENTICATE_RESULT_DATA";
    public static final int AUTHENTICATE_REGISTER_CODE = 1;
    public static final String AUTHENTICATE_REGISTER_DATA = "geeone.ezpz.AUTHENTICATE_REGISTER_DATA";
    public static final int UPLOAD_RESULT_CODE = 2;
    public static final String UPLOAD_RESULT_DATA = "geeone.ezpz.UPLOAD_RESULT_DATA";
    public static final int METADATA_FETCH_RESULT_CODE = 3;
    public static final String METADATA_FETCH_RESULT_DATA = "geeone.ezpz.METADATA_FETCH_RESULT_DATA";
    public static final int DELETE_RESULT_CODE = 4;
    public static final String DELETE_RESULT_DATA = "geeone.ezpz.DELETE_RESULT_DATA";
    public static final int UPLOAD_RESULT_FAILED = 0, UPLOAD_RESULT_SUCCESS = -1;
    public static final int UPLOAD_RESULT_INVALID_NAME = 1, UPLOAD_RESULT_FILESIZE_TOO_BIG = 2, UPLOAD_RESULT_STORAGE_LIMIT_REACHED = 3;

    //  firebase bucket stuff
    private final String BUCKET_REF = "gs://ezpz-23b89.appspot.com";
    //  firebase database stuff
    private final String DB_ROOT = "users";
    private final String DB_FILES = "files";
    private final String DB_LOGS = "logs";
    private final String DB_PRESENCE = "presence";
    private final String DB_LIMIT = "limit";
    private final String ACTION_CONNECT = "connect";
    private final String ACTION_DISCONNECT = "disconnect";
    private final String ACTION_CREATE = "create";
    private final String ACTION_DELETE = "delete";

    private final IBinder mBinder = new LocalBinder();

    private String userId;
    private ResultReceiver mResultReceiver;
    private FirebaseAuth mAuth;
    @SuppressWarnings("unused")
    private FirebaseAuth.AuthStateListener mAuthListener;
    private FirebaseStorage mStorage;
    private DatabaseReference mDatabaseRef;
    private StorageReference mStorageRef;

    private long currentSize, limit;
    private final long DEFAULT_LIMIT = 10000000;        //  10MB
    private final long DEFAULT_SIZE_LIMIT = 5000000;    //  5MB

    private Map<String, Object> fileMetadata;

    public class LocalBinder extends Binder {
        FirebaseServices getService(){
            return FirebaseServices.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        //  checks if device is rooted
        if (!RootChecker.isDeviceRooted()){
            mAuth = FirebaseAuth.getInstance();
            mDatabaseRef = FirebaseDatabase.getInstance().getReference().child(DB_ROOT);
            mStorage = FirebaseStorage.getInstance();
            mAuthListener = new FirebaseAuth.AuthStateListener() {
                @Override
                public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                    FirebaseUser user = firebaseAuth.getCurrentUser();
                    ActivityManager am = (ActivityManager)getApplicationContext().getSystemService(Context.ACTIVITY_SERVICE);
                    @SuppressWarnings("deprecation")
                    ComponentName cn = am.getRunningTasks(1).get(0).topActivity;
                    //noinspection StatementWithEmptyBody
                    if (user != null) {
                        // User is signed in
                    } else {
                        // User is signed out
                        userId = null;
                        if (!cn.getClassName().equals("LoginActivity")){
                            Intent i = new Intent(getApplicationContext(), LoginActivity.class);
                            startActivity(i);
                        }
                    }
                }
            };
            return mBinder;
        }else{
            return null;
        }
    }

    /*
        description: creates new account in Firebase
        parameter: email, password, resultreceiver for returning result of registration
     */
    public void register(String email, String password, ResultReceiver rr){
        mResultReceiver = rr;
        if (mAuth != null){
            mAuth.createUserWithEmailAndPassword(email, password).addOnCompleteListener(new OnCompleteListener<AuthResult>() {
                @Override
                public void onComplete(@NonNull Task<AuthResult> task) {
                    if (mResultReceiver != null) {
                        Bundle result = new Bundle();
                        if (task.isSuccessful()) {
                            result.putBoolean(AUTHENTICATE_REGISTER_DATA, true);
                        }else{
                            result.putBoolean(AUTHENTICATE_REGISTER_DATA, false);
                        }
                        mResultReceiver.send(AUTHENTICATE_REGISTER_CODE, result);
                    }
                }
            });
        }
    }

    /*
        description: authenticate credentials with Firebase
        parameter: email, password, resultreceiver for returning result of authentication
     */
    public void authenticate(String email, String password, ResultReceiver rr){
        mResultReceiver = rr;
        if (mAuth != null){
            mAuth.signInWithEmailAndPassword(email, password).addOnCompleteListener(new OnCompleteListener<AuthResult>() {
                @Override
                public void onComplete(@NonNull Task<AuthResult> task) {
                    if (mResultReceiver != null) {
                        Bundle result = new Bundle();
                        if (task.isSuccessful()) {
                            FirebaseUser user = mAuth.getCurrentUser();
                            assert user != null;
                            userId = user.getUid();
                            mDatabaseRef = mDatabaseRef.child(userId);
                            mStorageRef = mStorage.getReferenceFromUrl(BUCKET_REF).child(userId);
                            setPresence(true);
                            log(ACTION_CONNECT, null);
                            result.putBoolean(AUTHENTICATE_RESULT_DATA, true);
                        }else{
                            result.putBoolean(AUTHENTICATE_RESULT_DATA, false);
                            userId = null;
                        }
                        mResultReceiver.send(AUTHENTICATE_RESULT_CODE, result);
                    }
                }
            });
        }
    }

    /*
        description: uploads images (via uri) to Firebase storage, then updates Firebase database (add record)
        parameter: image's uri, delete on disconnection bool, resultreceiver for returning result of upload
     */
    public void upload(Uri imageUri, boolean deleteOnDC, ResultReceiver rr) {
        final boolean deleteOnDisconnect = deleteOnDC;
        mResultReceiver = rr;
        final Bundle result = new Bundle();
        if (mStorageRef != null){
            if (imageUri != null){
                long size = getSizeFromUri(imageUri);
                Log.d("SIZE", String.valueOf(size));
                if (size > DEFAULT_SIZE_LIMIT){
                    result.putInt(UPLOAD_RESULT_DATA, UPLOAD_RESULT_FILESIZE_TOO_BIG);
                    mResultReceiver.send(UPLOAD_RESULT_CODE, result);
                    return;
                }
                if (currentSize + size > limit){
                    result.putInt(UPLOAD_RESULT_DATA, UPLOAD_RESULT_STORAGE_LIMIT_REACHED);
                    mResultReceiver.send(UPLOAD_RESULT_CODE, result);
                    return;
                }
                String actualName = getNameFromUri(imageUri);
                if (actualName == null){
                    result.putInt(UPLOAD_RESULT_DATA, UPLOAD_RESULT_INVALID_NAME);
                    mResultReceiver.send(UPLOAD_RESULT_CODE, result);
                    return;
                }else{
                    String[] fileNameSplit = actualName.split("\\.");
                    if (fileNameSplit[0] != null){
                        actualName = fileNameSplit[0] + "_" + String.valueOf(System.currentTimeMillis());   //  B-01
                    }else{
                        result.putInt(UPLOAD_RESULT_DATA, UPLOAD_RESULT_INVALID_NAME);
                        mResultReceiver.send(UPLOAD_RESULT_CODE, result);
                        return;
                    }
                }
                final StorageReference sRef = mStorageRef.child(actualName);
                UploadTask uTask = sRef.putFile(imageUri);
                uTask.addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        //  if upload task fails
                        result.putInt(UPLOAD_RESULT_DATA, UPLOAD_RESULT_FAILED);
                        mResultReceiver.send(UPLOAD_RESULT_CODE, result);
                    }
                }).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                    @Override
                    public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                        //  if upload task succeeds
                    if (mDatabaseRef != null){
                        //  updates database
                        final DatabaseReference dRef = mDatabaseRef.child(DB_FILES).child(sRef.getName());
                        sRef.getMetadata().addOnSuccessListener(new OnSuccessListener<StorageMetadata>() {
                            @Override
                            public void onSuccess(StorageMetadata storageMetadata) {
                                //noinspection ConstantConditions
                                FileMetadata f = new FileMetadata(storageMetadata.getName(),
                                            storageMetadata.getContentType(),
                                            storageMetadata.getDownloadUrl().toString(),
                                            "gs://" + storageMetadata.getBucket() + "/" + storageMetadata.getPath(),
                                            storageMetadata.getSizeBytes(),
                                            deleteOnDisconnect);
                                dRef.setValue(f);
                                log(ACTION_CREATE, storageMetadata.getName());
                            }
                        });
                    }
                    result.putInt(UPLOAD_RESULT_DATA, UPLOAD_RESULT_SUCCESS);
                    mResultReceiver.send(UPLOAD_RESULT_CODE, result);
                    }
                });
            }
        }
    }

    /*
        description: get image's file name from uri
        parameter: image uri
     */
    private String getNameFromUri(Uri imageUri){
        String result = null;
        Cursor c = getContentResolver().query(imageUri, null, null, null, null);
        if (c != null){
            c.moveToFirst();
            result = c.getString(3);
            c.close();
        }
        return result;
    }

    /*
        description: get image's size from uri
        parameter: image uri
    */
    private long getSizeFromUri(Uri imageUri){
        long result = 0;
        Cursor c = getContentResolver().query(imageUri, null, null, null, null);
        if (c != null) {
            c.moveToFirst();
            result = c.getLong(2);
            c.close();
        }
        return result;
    }

    /*
        description: deletes file from Firebase storage, then updates Firebase database (remove record)
        parameter: file name to delete, resultreceiver for returning result of deletion
     */
    public void delete(@NonNull String fileName, ResultReceiver rr){
        final String toDelete = fileName;
        mResultReceiver = rr;
        if (mStorageRef != null){
            StorageReference sRef = mStorageRef.child(toDelete);
            final Bundle result = new Bundle();
            sRef.delete().addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    //  if deletion fails
                    result.putBoolean(DELETE_RESULT_DATA, false);
                    mResultReceiver.send(DELETE_RESULT_CODE, result);
                }
            }).addOnSuccessListener(new OnSuccessListener<Void>() {
                @Override
                public void onSuccess(Void aVoid) {
                    //  if deletion succeeds
                    if (mDatabaseRef != null) {
                        //  updates database
                        DatabaseReference dRef = mDatabaseRef.child(DB_FILES).child(toDelete);
                        dRef.removeValue();
                        log(ACTION_DELETE, toDelete);
                    }
                    result.putBoolean(DELETE_RESULT_DATA, true);
                    mResultReceiver.send(DELETE_RESULT_CODE, result);
                }
            });
        }
    }

    /*
        description: disconncts user and stops FirebaseServices
     */
    public void logout(){
        setPresence(false);
        log(ACTION_DISCONNECT, null);
        if (mAuth != null){
            mAuth.signOut();
        }
        stopSelf();
    }

    /*
        description: writes to database logs
        parameter: action taken by user, file name (if required)
     */
    private void log(@NonNull String action, String fileName){
        if (mDatabaseRef != null){
            DatabaseReference dRef = mDatabaseRef.child(DB_LOGS).push();
            Map<String, Object> data = new HashMap<>();
            data.put("actionBy", "user");
            data.put("fileName", fileName);
            data.put("actionType", action);
            data.put("timestamp", ServerValue.TIMESTAMP);
            dRef.setValue(data);
        }
    }

    /*
        description: sets user status in Firebase database (online/offline)
        parameter: user status (online/offline)
     */
    private void setPresence(boolean online){
        if (mDatabaseRef != null){
            DatabaseReference dRef = mDatabaseRef.child(DB_PRESENCE);
            dRef.setValue(online);
            if (online){
                final DatabaseReference lRef = mDatabaseRef.child(DB_LIMIT);
                lRef.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        if (dataSnapshot.getValue() == null){
                            lRef.setValue(DEFAULT_LIMIT);
                        }else{
                            limit = (long)dataSnapshot.getValue();
                        }
                    }
                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                    }
                });
            }
        }
    }

    /*
        description: listener for updating user's list of files currently being shared
        parameter: resultreceiver for returning list of files
     */
    public void startMetadataListener(ResultReceiver rr){
        mResultReceiver = rr;
        DatabaseReference dRef = mDatabaseRef.child(DB_FILES);
        ValueEventListener valueListener =  new ValueEventListener() {
            @Override
            @SuppressWarnings("unchecked")
            public void onDataChange(DataSnapshot dataSnapshot) {
                fileMetadata = (Map<String, Object>) dataSnapshot.getValue();
                ArrayList<FileMetadata> resultPayload = new ArrayList<>();
                Bundle result = new Bundle();
                currentSize = 0;
                if (fileMetadata != null){
                    for(Object o : fileMetadata.values()){
                        Map<String, Object> m = (Map<String, Object>) o;
                        String fileName = (String) m.get(FileMetadata.NAME);
                        String fileType = (String) m.get(FileMetadata.TYPE);
                        String downloadUrl = (String) m.get(FileMetadata.URL);
                        String storageDirectory = (String) m.get(FileMetadata.DIRECTORY);
                        Long size = (Long) m.get(FileMetadata.SIZE);
                        currentSize += size;
                        boolean deleteOnDisconnect = (boolean) m.get(FileMetadata.DELETE_ON_DC);
                        if ((fileName != null) && (fileType != null) && (downloadUrl != null) && (size > 0)){
                            FileMetadata fm = new FileMetadata(fileName, fileType, downloadUrl, storageDirectory, size, deleteOnDisconnect);
                            resultPayload.add(fm);
                        }
                    }
                }
                result.putParcelableArrayList(METADATA_FETCH_RESULT_DATA, resultPayload);
                mResultReceiver.send(METADATA_FETCH_RESULT_CODE, result);
            }
            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        };
        dRef.addValueEventListener(valueListener);
    }
}
