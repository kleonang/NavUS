package com.example.navus;

import com.google.firebase.database.FirebaseDatabase;

public class MyFireBase {

    private static FirebaseDatabase mDatabase;

    public static FirebaseDatabase getDatabase() {
        if (mDatabase == null) {
            mDatabase = FirebaseDatabase.getInstance(APIKeys.FirebaseURL);//connect to firebase
            mDatabase.setPersistenceEnabled(true);
        }
        return mDatabase;
    }
}
