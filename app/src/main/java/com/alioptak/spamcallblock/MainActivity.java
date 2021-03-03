package com.alioptak.spamcallblock;

import android.Manifest.permission;
import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.provider.ContactsContract;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.alioptak.spamcallblock.database.DataBaseHandler;
import com.google.firebase.database.FirebaseDatabase;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private final String DISPLAY_NAME = Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB ? ContactsContract.Contacts.DISPLAY_NAME_PRIMARY : ContactsContract.Contacts.DISPLAY_NAME;

    private final String FILTER = DISPLAY_NAME + " NOT LIKE '%@%'";

    private final String ORDER = String.format("%1$s COLLATE NOCASE", DISPLAY_NAME);

    @SuppressLint("InlinedApi")
    private final String[] PROJECTION = {
            ContactsContract.Contacts._ID,
            DISPLAY_NAME,
            ContactsContract.Contacts.HAS_PHONE_NUMBER
    };

    Button button_main_gocontact;
    Button button_main_gohistory;
    ImageView imgeview_main_activate;
    String TAG = "MainActivity";

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ArrayList<String> liste = readFromFile(this);
        int end = liste.size();
        Set<String> set = new HashSet<>();
        for(int i = 0; i < end; i++){
            set.add(liste.get(i));
        }
        Iterator<String> it = set.iterator();
        ArrayList<String> newListe = new ArrayList<>();
        while(it.hasNext()) {
            //System.out.println(it.next());
            newListe.add(it.next());
        }
        Singleton.getInstance().setListNumberBlocked(newListe);


        setContentView(R.layout.activity_main);

        FirebaseDatabase.getInstance().setPersistenceEnabled(true);
        FirebaseDatabase.getInstance().goOnline();

        askPermission( permission.READ_CONTACTS, 10);
        askPermission( permission.READ_EXTERNAL_STORAGE, 12);
        askPermission( permission.WRITE_EXTERNAL_STORAGE, 13);
        button_main_gohistory = findViewById(R.id.button_main_gohistory);
        button_main_gohistory.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                askPermission( permission.READ_CALL_LOG, 8);
            }
        });

        button_main_gocontact = findViewById(R.id.button_main_gocontact);
        button_main_gocontact.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                askPermission( permission.READ_CONTACTS, 7);
            }
        });

        imgeview_main_activate = findViewById(R.id.imgeview_main_activate);
        imgeview_main_activate.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                askPermission( permission.ANSWER_PHONE_CALLS, 9);
                askPermission(permission.READ_PHONE_STATE, 9);
                // Only then: proceed.
            }
        });
        DataBaseHandler db = new DataBaseHandler(this);
        // Inserting Contacts
        Log.d("Insert: ", "Inserting ..");
        db.addContact(new Contact("Ravi", "9100000000"));
        db.addContact(new Contact("Srinivas", "9199999999"));

    }

    @Override
    protected void onPause() {
        super.onPause();
        ArrayList<String> newBlockedContact = Singleton.getInstance().getListNumberBlocked();
        writeToFile(newBlockedContact,this);
        System.out.println("Méthode onPause called");
    }


    public void goToContact(){
        Intent i = new Intent(MainActivity.this, ContactActivity.class);
        startActivity(i);
    }

    public void goToHistory(){
        Intent i = new Intent(MainActivity.this, HistoryActivity.class);
        startActivity(i);
    }


    public void askPermission(String perm, int requestCode){
        if (PackageManager.PERMISSION_GRANTED != ContextCompat.checkSelfPermission(this, perm)) {
            final String[] PERMISSIONS_STORAGE = {perm};
            ActivityCompat.requestPermissions(MainActivity.this, PERMISSIONS_STORAGE, requestCode);

        }else{
            switch(requestCode){
                case 7:
                    goToContact();
                    break;
                case 8:
                    goToHistory();
                    break;
                case 9: // DONT ADD ANYTHING HERE.
                case 11:
                    // Proceed!
                    break;
                case 10:
                    readContacts();
                    ArrayList<Contact> listContact = Singleton.getInstance().getListContact();
                    for (Contact contact: listContact) {
                        if(Singleton.getInstance().isBlocked(contact.getPhone_number())){
                            Singleton.getInstance().blockContact(contact);
                        }
                    }
                case 12:
                case 13:
                    break;


            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        boolean permissionGranted = false;
        try {
            permissionGranted = grantResults[0]== PackageManager.PERMISSION_GRANTED;
        }catch (Exception e){
            e.printStackTrace();
        }
        switch(requestCode){
            case 7:
                if(permissionGranted) goToContact();
                break;
            case 8:
                if(permissionGranted) goToHistory();
                break;
            case 9:
            case 11:
            case 12:
            case 13:
                break;
            case 10:
                permissionGranted = grantResults[0]== PackageManager.PERMISSION_GRANTED;
                readContacts();
            default:
                permissionGranted = false;
        }
        if(!permissionGranted){
            Toast.makeText(this, "Error: You didn't give the permission. Impossible to launch the service.", Toast.LENGTH_SHORT).show();
        }
    }

    public void readContacts() {
        ContentResolver contentResolver= getContentResolver();
        ArrayList<Contact> contacts = new ArrayList<Contact>();
        ContentResolver cr = getContentResolver();
        Cursor cursor = cr.query(ContactsContract.Contacts.CONTENT_URI, PROJECTION, FILTER, null, ORDER);
        if (cursor != null && cursor.moveToFirst()) {

            do {
                // get the contact's information
                String id = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts._ID));
                String name = cursor.getString(cursor.getColumnIndex(DISPLAY_NAME));
                Integer hasPhone = cursor.getInt(cursor.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER));

                // get the user's phone number
                String phone = null;
                if (hasPhone > 0) {
                    Cursor cp = cr.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null,
                            ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?", new String[]{id}, null);
                    if (cp != null && cp.moveToFirst()) {
                        phone = cp.getString(cp.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER));
                        cp.close();
                    }
                }


                if(phone != null && phone.length() > 0){
                    if(phone.length()>2){
                        if(!phone.substring(0,3).contentEquals("+33") && phone.length()==10){
                            phone = "+33" + phone.substring(1);
                        }
                    }
                    Log.d(TAG, phone + " " + name);
                    Contact contact = new Contact(name, phone);
                    contacts.add(contact);
                }


            } while (cursor.moveToNext());
            Singleton.getInstance().setContacts(contacts);
            cursor.close();
        }
    }

    private void writeToFile(ArrayList<String> data, Context context) {//String data
        try {
            //OutputStreamWriter outputStreamWriter = new OutputStreamWriter(context.openFileOutput("config.txt", Context.MODE_PRIVATE));
            //outputStreamWriter.write(data);
            //outputStreamWriter.close();
            for (String str : data) {
                str += "\n";
                FileOutputStream output = openFileOutput("config.txt", MODE_PRIVATE);
                output.write(str.getBytes());
                if (output != null)
                    output.close();
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private ArrayList<String> readFromFile(Context context) {

        ArrayList<String> res = new ArrayList<>();

        try {
            InputStream inputStream = context.openFileInput("config.txt");

            if ( inputStream != null ) {
                InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
                BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
                String receiveString = "";
                StringBuilder stringBuilder = new StringBuilder();

                while ( (receiveString = bufferedReader.readLine()) != null ) {
                    res.add(receiveString);
                    stringBuilder.append("\n").append(receiveString);
                }

                inputStream.close();
                //ret = stringBuilder.toString();
            }
        }
        catch (FileNotFoundException e) {
            Log.e("login activity", "File not found: " + e.toString());
        } catch (IOException e) {
            Log.e("login activity", "Can not read file: " + e.toString());
        }
        return res;
    }
}