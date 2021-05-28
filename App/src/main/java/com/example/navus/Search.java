package com.example.navus;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.os.Bundle;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import androidx.appcompat.app.AppCompatActivity;

import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SearchView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Search extends AppCompatActivity {
    private static final String TAG = "MyActivity";
    Map<String, Venue> venuedict = new HashMap<String, Venue>();
    Boolean issource = false; //flag to know which input user is in
    List<String> venue_list = new ArrayList<String>(); //store the venue names for the list
    Boolean LocationEnabled = false;
    Boolean validsource = false; //flag to know if source is valid
    Boolean validdestination = false; //flag to know if destination is valid
    EditText sourceedittext, destinationedittext;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.search);
        sourceedittext = findViewById(R.id.source);
        destinationedittext = findViewById(R.id.destination);
        destinationedittext.setBackgroundTintList(ColorStateList.valueOf(Color.RED)); //set the underline to be red
        LocationEnabled = getIntent().getExtras().getBoolean("locationenabled"); //get from previous activity if user enabled location

        ListView listView = findViewById(R.id.listresults);
        ArrayAdapter<String> arrayAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, venue_list);
        listView.setAdapter(arrayAdapter);

        if (LocationEnabled==true){
            sourceedittext.setText("YOUR LOCATION");
            sourceedittext.setTextColor(Color.BLUE);
            validsource = true;
        }

        String selectQuery = "SELECT  * FROM Venues";

        DBHelper myhelper = new DBHelper(this);
        SQLiteDatabase mydatabase = myhelper.getWritableDatabase();
        Cursor cursor = mydatabase.rawQuery(selectQuery, null);

        if (cursor.moveToFirst()){
            do{
                String name = cursor.getString(0);
                Venue NewVenue = new Venue(name, cursor.getString(1), cursor.getString(2), cursor.getString(3));
                venuedict.put(name, NewVenue);
            }while (cursor.moveToNext());
        }
        cursor.close();
        mydatabase.close();
        populatelist("");//populate list initially
        arrayAdapter.notifyDataSetChanged();//refresh listview

        sourceedittext.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                issource = true;
                //populate list view
                populatelist(sourceedittext.getText().toString());
                arrayAdapter.notifyDataSetChanged();//refresh listview
            }
        });

        sourceedittext.addTextChangedListener(new TextWatcher(){

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                issource = true;
                String searchterm = sourceedittext.getText().toString();
                if (searchterm.toUpperCase().equals("YOUR LOCATION")){
                    sourceedittext.setText("YOUR LOCATION"); //ensure it is in CAPS
                    sourceedittext.setTextColor(Color.BLUE); //reset back to black font unless it is your location
                }else{
                    sourceedittext.setTextColor(Color.BLACK); //reset back to black font unless it is your location
                }
                if (venuedict.containsKey(searchterm)){
                    sourceedittext.setBackgroundTintList(ColorStateList.valueOf(Color.GREEN)); //set the underline to be green if name exists
                    validsource = true; //set flag to true
                }else{
                    sourceedittext.setBackgroundTintList(ColorStateList.valueOf(Color.RED)); //set the underline to be red
                    validsource = false;
                }
                //populate list view
                populatelist(searchterm);
                arrayAdapter.notifyDataSetChanged();//refresh listview
                returntomap();
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });

        destinationedittext.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                issource = false;
                //populate list view
                populatelist(destinationedittext.getText().toString());
                arrayAdapter.notifyDataSetChanged();//refresh listview
            }
        });

        destinationedittext.addTextChangedListener(new TextWatcher(){

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                issource = false;
                String searchterm = destinationedittext.getText().toString();
                if (venuedict.containsKey(searchterm)){
                    destinationedittext.setBackgroundTintList(ColorStateList.valueOf(Color.GREEN)); //set the underline to be green if name exists
                    validdestination = true; //set flag to true
                }else{
                    destinationedittext.setBackgroundTintList(ColorStateList.valueOf(Color.RED)); //set the underline to be red
                    validdestination = false;
                }
                //populate list view
                populatelist(searchterm);
                arrayAdapter.notifyDataSetChanged();//refresh listview
                returntomap();
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });



        listView.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String venueselected = (String) listView.getItemAtPosition(position);
                if (issource){
                    sourceedittext.setText(venueselected);
                    sourceedittext.setSelection(sourceedittext.getText().length());//set cursor to end
                }else{
                    destinationedittext.setText(venueselected);
                    destinationedittext.setSelection(destinationedittext.getText().length());//set cursor to end
                }
            }
        });

    }

    void populatelist(String search){
        venue_list.clear();
        if (LocationEnabled && issource){ //add your location option if user enabled location
            venue_list.add("YOUR LOCATION");
        }
        for (String key : venuedict.keySet()) {
            if (key.toLowerCase().replaceAll("\\s+","").contains(search.toString().toLowerCase().replaceAll("\\s+",""))) { //ignore spaces
                venue_list.add(key);
            }
        }
        if (LocationEnabled && issource){
            Collections.sort(venue_list.subList(1, venue_list.size())); //sort the list except 1st element to keep your location at top
        }else{
            Collections.sort(venue_list); //sort the list
        }
    }

    void returntomap(){
        if (validsource && validdestination){
            Intent mainIntent = new Intent(Search.this,MapsActivity.class);
            String source = sourceedittext.getText().toString();
            String destination = destinationedittext.getText().toString();
            if (!source.equals("YOUR LOCATION")){ //if not your location then include source information
                mainIntent.putExtra("Source", source);
            }
            mainIntent.putExtra("Destination", destination);
            setResult(RESULT_OK, mainIntent);
            finish();
        }
    }
}

