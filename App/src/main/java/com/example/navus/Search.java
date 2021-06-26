package com.example.navus;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.os.Bundle;

import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import androidx.appcompat.app.AppCompatActivity;

import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SearchView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Search extends AppCompatActivity {
    private static final String TAG = "MyActivity";
    Map<String, Venue> venuedict = new HashMap<String, Venue>();
    Boolean issource = true; //flag to know which input user is in
    ArrayList<String> venue_list = new ArrayList<String>(); //store the venue names for the list
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
        //ArrayAdapter<String> arrayAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, venue_list);
        CustomArrayAdapter arrayAdapter = new CustomArrayAdapter(this, venue_list);
        listView.setAdapter(arrayAdapter);

        if (LocationEnabled==true){
            sourceedittext.setText(getString(R.string.your_location));
            sourceedittext.setTextColor(getResources().getColor(R.color.lightblue));
            validsource = true;
            sourceedittext.setBackgroundTintList(ColorStateList.valueOf(Color.GREEN)); //set the underline to be green
            destinationedittext.requestFocus(); //open the keyboard
            issource = false;
        }else{
            sourceedittext.setBackgroundTintList(ColorStateList.valueOf(Color.RED)); //set the underline to be red
            sourceedittext.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0,0 , 0); //hide the cross icon
            sourceedittext.requestFocus(); //open the keyboard
            issource = true;
        }
        destinationedittext.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0,0 , 0); //hide the cross icon

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
                listView.setSelection(0); //scroll to top
            }
        });

        sourceedittext.addTextChangedListener(new TextWatcher(){

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                issource = true;

                //show the cross button else hide it
                if (!sourceedittext.getText().toString().equals(""))
                    sourceedittext.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.cross, 0);
                else
                    sourceedittext.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0,0 , 0);

                String searchterm = sourceedittext.getText().toString();
                if (searchterm.toUpperCase().equals(getString(R.string.your_location)) && LocationEnabled){
                    sourceedittext.setTextColor(getResources().getColor(R.color.lightblue)); //reset back to black font unless it is your location
                    validsource = true; //set flag to true
                    sourceedittext.setBackgroundTintList(ColorStateList.valueOf(Color.GREEN)); //set the underline to be green
                    returntomap();
                }else{
                    sourceedittext.setTextColor(getResources().getColor(R.color.text_default)); //reset back default color

                    if (venuedict.containsKey(searchterm)){
                        sourceedittext.setBackgroundTintList(ColorStateList.valueOf(Color.GREEN)); //set the underline to be green if name exists
                        validsource = true; //set flag to true
                        returntomap();
                    }else{
                        sourceedittext.setBackgroundTintList(ColorStateList.valueOf(Color.RED)); //set the underline to be red
                        validsource = false;
                    }
                }
                //populate list view
                populatelist(searchterm);
                arrayAdapter.notifyDataSetChanged();//refresh listview
                listView.setSelection(0); //scroll to top
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });


        sourceedittext.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if(event.getAction() == MotionEvent.ACTION_UP && sourceedittext.getCompoundDrawables()[2] != null) {
                    if(event.getRawX() >= (sourceedittext.getRight() - sourceedittext.getCompoundDrawables()[2].getBounds().width())) {
                        // clear text and hide icon
                        sourceedittext.setText("");
                        sourceedittext.setCompoundDrawables(null,null, null, null);
                        sourceedittext.requestFocus();
                        return true;
                    }
                }
                return false;
            }
        });

        destinationedittext.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                issource = false;
                //populate list view
                populatelist(destinationedittext.getText().toString());
                arrayAdapter.notifyDataSetChanged();//refresh listview
                listView.setSelection(0); //scroll to top
            }
        });

        destinationedittext.addTextChangedListener(new TextWatcher(){

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                issource = false;
                //show the cross button else hide it
                if (!destinationedittext.getText().toString().equals(""))
                    destinationedittext.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.cross, 0);
                else
                    destinationedittext.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0,0 , 0);

                String searchterm = destinationedittext.getText().toString();
                if (venuedict.containsKey(searchterm)){
                    destinationedittext.setBackgroundTintList(ColorStateList.valueOf(Color.GREEN)); //set the underline to be green if name exists
                    validdestination = true; //set flag to true
                    returntomap();
                }else{
                    destinationedittext.setBackgroundTintList(ColorStateList.valueOf(Color.RED)); //set the underline to be red
                    validdestination = false;
                }
                //populate list view
                populatelist(searchterm);
                arrayAdapter.notifyDataSetChanged();//refresh listview
                listView.setSelection(0); //scroll to top
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });

        destinationedittext.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if(event.getAction() == MotionEvent.ACTION_UP && destinationedittext.getCompoundDrawables()[2] != null) {
                    if(event.getRawX() >= (destinationedittext.getRight() - destinationedittext.getCompoundDrawables()[2].getBounds().width())) {
                        // clear text and hide icon
                        destinationedittext.setText("");
                        destinationedittext.setCompoundDrawables(null,null, null, null);
                        destinationedittext.requestFocus();
                        return true;
                    }
                }
                return false;
            }
        });



        listView.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String venueselected = (String) listView.getItemAtPosition(position);
                if (issource){
                    sourceedittext.setText(venueselected);
                    sourceedittext.setSelection(sourceedittext.getText().length());//set cursor to end
                    destinationedittext.requestFocus();
                    issource=false;
                }else{
                    destinationedittext.setText(venueselected);
                    destinationedittext.setSelection(destinationedittext.getText().length());//set cursor to end
                    sourceedittext.requestFocus();
                    issource=true;
                }
            }
        });

    }

    void populatelist(String search){
        Map<String, String> favourites = new HashMap<String, String>(); //using map so it is easier to find if a value exists rather than looping through

        venue_list.clear();
        if (LocationEnabled && issource){ //add your location option if user enabled location
            venue_list.add(getString(R.string.your_location));
        }

        if (search.equals("")){ //empty search, add favourites on top
            SharedPreferences sharedpreferences = getSharedPreferences("MySharedPreference", MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedpreferences.edit();
            String favouritestring = sharedpreferences.getString("Favourites", "");

            if (!favouritestring.equals("")) { //if favourites exists
                for (String fav: favouritestring.split(",")){ //populate the favourites
                    favourites.put(fav, "");//empty string as placeholder
                    venue_list.add(fav); //add favourites first
                }
            }
            for (String key : venuedict.keySet()) {
                if (!favourites.containsKey(key)){ //if venue is not in favourites list then add it
                    venue_list.add(key);
                }
            }

        }else{
            for (String key : venuedict.keySet()) {
                if (key.toLowerCase().replaceAll("\\s+","").contains(search.toString().toLowerCase().replaceAll("\\s+",""))) { //ignore spaces
                    venue_list.add(key);
                }
            }
        }
        //sort the list
        if (LocationEnabled && issource){
           // Collections.sort(venue_list.subList(1 + favourites.size(), venue_list.size())); //sort the list except YOUR LOCATION and favourites at the top

            Collections.sort(venue_list.subList(1 + favourites.size(), venue_list.size()), new AlphanumComparator(String.CASE_INSENSITIVE_ORDER));
        }else{
            //Collections.sort(venue_list.subList(favourites.size(), venue_list.size())); //sort the list except favourites at the top
            Collections.sort(venue_list.subList(favourites.size(), venue_list.size()), new AlphanumComparator(String.CASE_INSENSITIVE_ORDER));
        }
    }

    void returntomap(){
        if (validsource && validdestination){
            Intent mainIntent = new Intent(Search.this,MapsActivity.class);
            String source = sourceedittext.getText().toString();
            String destination = destinationedittext.getText().toString();
            if (!source.equals(getString(R.string.your_location))){ //if not your location then include source information
                mainIntent.putExtra("Source", source);
            }
            mainIntent.putExtra("Destination", destination);
            setResult(RESULT_OK, mainIntent);
            finish();
        }
    }
}

