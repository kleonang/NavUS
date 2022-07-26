package com.example.navus;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.os.Bundle;


import androidx.appcompat.app.AppCompatActivity;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.BounceInterpolator;
import android.view.animation.TranslateAnimation;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import tourguide.tourguide.Overlay;
import tourguide.tourguide.Pointer;
import tourguide.tourguide.ToolTip;
import tourguide.tourguide.TourGuide;

public class Search extends AppCompatActivity {
    private static final String TAG = "MyActivity";
    Map<String, Venue> venuedict = new HashMap<String, Venue>();
    Boolean issource = true; //flag to know which input user is in
    ArrayList<String> venue_list = new ArrayList<String>(); //store the venue names for the list
    Boolean LocationEnabled = false;
    Boolean validsource = false; //flag to know if source is valid
    Boolean validdestination = false; //flag to know if destination is valid
    EditText sourceedittext, destinationedittext;
    SharedPreferences.Editor editor;
    Deque<String> history = new LinkedList<>();
    Deque<String> favourites = new LinkedList<>();
    TourGuide mTourGuideHandler;
    int guideid = 0;

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

        if (guideid==1) {
            guideuser(guideid);
            destinationedittext.requestFocus();//so that tooltip will close when user taps on source
        }

        View.OnTouchListener source_on_touch_listener = new View.OnTouchListener() {
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
        };

        View.OnTouchListener destination_on_touch_listener = new View.OnTouchListener() {
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
        };


        sourceedittext.setOnTouchListener(source_on_touch_listener);
        destinationedittext.setOnTouchListener(destination_on_touch_listener);

        sourceedittext.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (guideid==1) {
                    mTourGuideHandler.cleanUp(); //clear source explanation
                    sourceedittext.setOnTouchListener(source_on_touch_listener);//reset to allow the "x" button to work
                    destinationedittext.setOnTouchListener(destination_on_touch_listener);
                    guideid++;
                    guideuser(guideid);
                }
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


        destinationedittext.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (guideid==2) {
                    mTourGuideHandler.cleanUp(); //clear destination explanation
                    sourceedittext.setOnTouchListener(source_on_touch_listener);//reset to allow the "x" button to work
                    destinationedittext.setOnTouchListener(destination_on_touch_listener);
                    guideid++;
                    editor.putInt("GuideID", guideid);
                    editor.commit(); //save data
                }
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
                    if (!validsource)
                        sourceedittext.requestFocus();
                    issource=true;
                }
            }
        });

    }

    void populatelist(String search){
        venue_list.clear();
        if (LocationEnabled && issource){ //add your location option if user enabled location
            venue_list.add(getString(R.string.your_location));
        }

        if (search.equals("")){ //empty search, add favourites on top
            SharedPreferences sharedpreferences = getSharedPreferences("MySharedPreference", MODE_PRIVATE);
            editor = sharedpreferences.edit();
            String favouritestring = sharedpreferences.getString("Favourites", "");
            if (guideid==0) //first launch
                guideid = sharedpreferences.getInt("GuideID", 1);

            if (!favouritestring.equals("")) { //if favourites exists
                favourites.clear(); //need to clear as method is called a few times
                for (String fav: favouritestring.split(",")){ //populate the favourites
                    favourites.add(fav);
                    venue_list.add(fav); //add favourites first
                }
            }
            //Get search history
            String searchhistory = sharedpreferences.getString("SearchHistory", "");

            if (!searchhistory.equals("")) { //if userhistory exists
                history.clear(); //need to clear as method is called a few times
                for (String hist: searchhistory.split(",")){ //populate the history
                    if (!favourites.contains(hist)){ //ensure it is not in favourites
                        history.add(hist);
                        venue_list.add(hist); //add history after favourites
                    }
                }
            }

            for (String key : venuedict.keySet()) {
                if (!favourites.contains(key) && !history.contains(key)){ //if venue is not in favourites list and history then add it
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
             //sort the list except YOUR LOCATION and favourites and history at the top
            if (search.equals(""))
                Collections.sort(venue_list.subList(1 + favourites.size() + history.size(), venue_list.size()), new CustomCompare(String.CASE_INSENSITIVE_ORDER));
            else
                Collections.sort(venue_list.subList(1, venue_list.size()), new CustomCompare(String.CASE_INSENSITIVE_ORDER));
        }else{
            //sort the list except favourites and history at the top
            if (search.equals(""))
                Collections.sort(venue_list.subList(favourites.size() + history.size(), venue_list.size()), new CustomCompare(String.CASE_INSENSITIVE_ORDER));
            else
                Collections.sort(venue_list, new CustomCompare(String.CASE_INSENSITIVE_ORDER));
        }
    }

    void returntomap(){
        if (validsource && validdestination){
            Intent mainIntent = new Intent(Search.this,MapsActivity.class);
            String source = sourceedittext.getText().toString();
            String destination = destinationedittext.getText().toString();
            if (!source.equals(getString(R.string.your_location))){ //if not your location then include source information
                mainIntent.putExtra("Source", source);
                //add to search history, remove first if exists
                if (history.contains(source))
                    history.remove(source);
                history.addFirst(source); //add to front
            }
            //remove first if exists
            if (history.contains(destination))
                history.remove(destination);
            history.addFirst(destination); //add to front


            String searchhistory = "";

            int historysize = history.size();

            for (int i=0; i<Math.min(historysize,5); i++){ //keep only up to recent 5 searches
                if (searchhistory.equals("")) { //first element
                    searchhistory = history.getFirst();
                }else {
                    searchhistory += "," + history.getFirst(); //else need add comma
                }
                history.removeFirst();
            }

            editor.putString("SearchHistory", searchhistory);
            editor.commit(); //save data

            mainIntent.putExtra("Destination", destination);
            mainIntent.putExtra("GuideID", guideid); //pass back id so maps activity knows if to continue with the tutorial
            setResult(RESULT_OK, mainIntent);
            finish();
        }
    }

    public void guideuser(int id){
        Animation animation = new TranslateAnimation(0f, 0f, 200f, 0f);
        animation.setDuration(1000);
        animation.setFillAfter(true);
        animation.setInterpolator(new BounceInterpolator());

        ToolTip toolTip = new ToolTip()
                .setTextColor(getResources().getColor(R.color.text_default))
                .setBackgroundColor(getResources().getColor(R.color.white))
                .setShadow(true)
                .setGravity(Gravity.BOTTOM | Gravity.BOTTOM)
                .setEnterAnimation(animation);

        mTourGuideHandler = TourGuide.init(this).with(TourGuide.Technique.CLICK);

        //mTourGuideHandler.setOverlay(new Overlay().disableClick(false));
        Pointer pointer = new Pointer();
        pointer.setColor(getResources().getColor(R.color.red));
        mTourGuideHandler.setPointer(pointer);

        if (id==1){ //for source
            mTourGuideHandler.setToolTip(toolTip.setTitle(getString(R.string.source)).setDescription(getString(R.string.source_tap)));
            mTourGuideHandler.playOn(sourceedittext);
        }else if(id==2){ //for destination
            mTourGuideHandler.setToolTip(toolTip.setTitle(getString(R.string.destination)).setDescription(getString(R.string.destination_tap)));
            mTourGuideHandler.playOn(destinationedittext);
        }
    }
}

