package com.example.navus;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;


import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;

public class CustomArrayAdapter extends ArrayAdapter<String> {
    private final Context context;
    private final ArrayList<String> values;
    private Map<String, String> busstops = new HashMap<String, String>();
    private Deque<String> favourites = new LinkedList<>();
    private Deque<String> history = new LinkedList<>();

    public CustomArrayAdapter(Context context, ArrayList<String> values) {
        super(context, -1, values);
        this.context = context;
        this.values = values;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View rowView = inflater.inflate(R.layout.custom_list, parent, false);

        getdatafromsql();
        TextView textView = (TextView) rowView.findViewById(R.id.listviewtext);
        ImageView imageView = (ImageView) rowView.findViewById(R.id.favouriteicon);
        ImageView busicon = (ImageView) rowView.findViewById(R.id.busstopicon);
        SharedPreferences sharedpreferences = context.getSharedPreferences("MySharedPreference", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedpreferences.edit();
        String favouritestring = sharedpreferences.getString("Favourites", "");
        //Get search history
        String searchhistory = sharedpreferences.getString("SearchHistory", "");

        if (busstops.containsKey(values.get(position))){
            busicon.setImageResource(R.drawable.appicon);
        }


        if (!favouritestring.equals("")) { //if favourites exists
            for (String fav: favouritestring.split(",")){ //populate the favourites
                if (!favourites.contains(fav)) //ensure it is not in favourites
                    favourites.add(fav);
            }
        }

        if (!searchhistory.equals("")) { //if userhistory exists
            for (String hist: searchhistory.split(",")){ //populate the history
                if (!favourites.contains(hist) && !history.contains(hist)){ //ensure it is not in favourites and history
                    history.add(hist);
                }
            }
        }

        textView.setText(values.get(position));
        if (favourites.contains(values.get(position))){ //already in favourites, set yellow star
            imageView.setImageResource(R.drawable.yellowstar);
        }else if(history.contains(values.get(position))){ //in user's history, set clock
            imageView.setImageResource(R.drawable.clock);
        }else if (!values.get(position).equals(getContext().getResources().getString(R.string.your_location))){ //ensure it is not YOUR LOCATION
            imageView.setImageResource(R.drawable.star); //defaults to empty star
        }

        imageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String location = values.get(position);
                if (favourites.contains(location)){ //user decides to remove favourite
                    if (history.contains(location)) //in user's history
                        imageView.setImageResource(R.drawable.clock);
                    else
                        imageView.setImageResource(R.drawable.star);
                    favourites.remove(location); //delete key
                    Toast.makeText(context.getApplicationContext(), context.getResources().getString(R.string.favourites_removed, location), Toast.LENGTH_SHORT).show();

                }else if (!location.equals(getContext().getResources().getString(R.string.your_location))){//user adds to favourite, ensure it is not YOUR LOCATION
                    imageView.setImageResource(R.drawable.yellowstar);
                    if (!favourites.contains(location)) //ensure it is not in favourites
                        favourites.add(location);
                    Toast.makeText(context.getApplicationContext(),context.getResources().getString(R.string.favourites_added, location), Toast.LENGTH_SHORT).show();
                }

                String favouritestring = "";

                Iterator itr = favourites.iterator();//loop through all the favourites
                while (itr.hasNext()) {
                    if (favouritestring.equals("")){ //first element
                        favouritestring = String.valueOf(itr.next());
                    }else{ //need to add comma
                        favouritestring += "," + itr.next();
                    }
                }

                editor.putString("Favourites", favouritestring);
                editor.apply(); //save data
            }
        });

        return rowView;
    }

    public void getdatafromsql() {
        DBHelper myhelper = new DBHelper(getContext());
        SQLiteDatabase mydatabase = myhelper.getWritableDatabase();
        Cursor cursor = mydatabase.rawQuery("SELECT * FROM Venues", null);
        if (cursor.moveToFirst()) {
            do {
                if (cursor.getString(3).equals("true")) { //if is busstop
                    busstops.put(cursor.getString(0), "");
                }
            } while (cursor.moveToNext());
        }
        cursor.close();
        mydatabase.close();
    }
}
