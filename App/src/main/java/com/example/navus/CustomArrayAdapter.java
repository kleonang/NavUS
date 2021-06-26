package com.example.navus;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class CustomArrayAdapter extends ArrayAdapter<String> {
    private final Context context;
    private final ArrayList<String> values;
    private Map<String, String> favourites = new HashMap<String, String>(); //using map so it is easier to find if a value exists rather than looping through

    public CustomArrayAdapter(Context context, ArrayList<String> values) {
        super(context, -1, values);
        this.context = context;
        this.values = values;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View rowView = inflater.inflate(R.layout.custom_list, parent, false);
        TextView textView = (TextView) rowView.findViewById(R.id.listviewtext);
        ImageView imageView = (ImageView) rowView.findViewById(R.id.favouriteicon);

        SharedPreferences sharedpreferences = context.getSharedPreferences("MySharedPreference", context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedpreferences.edit();
        String favouritestring = sharedpreferences.getString("Favourites", "");

        if (!favouritestring.equals("")) { //if favourites exists
            for (String fav: favouritestring.split(",")){ //populate the favourites
                favourites.put(fav, "");//empty string as placeholder
            }
        }

        textView.setText(values.get(position));
        if (favourites.containsKey(values.get(position))){ //already in favourites, set yellow star
            imageView.setImageResource(R.drawable.yellowstar);
        }else if (!values.get(position).equals(getContext().getResources().getString(R.string.your_location))){ //ensure it is not YOUR LOCATION
            imageView.setImageResource(R.drawable.star); //defaults to empty star
        }

        imageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String location = values.get(position);
                if (favourites.containsKey(location)){ //user decides to remove favourite
                    imageView.setImageResource(R.drawable.star);
                    favourites.remove(location); //delete key

                }else if (!location.equals(getContext().getResources().getString(R.string.your_location))){//user adds to favourite, ensure it is not YOUR LOCATION
                    imageView.setImageResource(R.drawable.yellowstar);
                    favourites.put(location, ""); //empty string as placeholder
                }

                String favouritestring = "";
                for (String fav: favourites.keySet()){ //loop through all the keys
                    if (favouritestring.equals("")){ //first element
                        favouritestring = fav;
                    }else{ //need to add comma
                        favouritestring += "," + fav;
                    }
                }
                editor.putString("Favourites", favouritestring);
                editor.commit(); //save data
            }
        });

        return rowView;
    }
}
