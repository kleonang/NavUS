# GETS LATEST VENUE INFORMATION FROM NUSMODS GITHUB AND EXPORTS TO EXCEL FILE
import urllib.request
import json
import pandas as pd

data_url = "https://raw.githubusercontent.com/nusmodifications/nusmods/" \
    + "master/website/src/data/venues.json"

with urllib.request.urlopen(data_url) as url:
    data = json.loads(url.read().decode())
    column_names = ["NAME", "LATITUDE", "LONGITUDE"]
    df = pd.DataFrame(columns=column_names)

    for venue in data:
        try:
            long = data[venue]["location"]["x"]
            lat = data[venue]["location"]["y"]
            new_row = {"NAME": venue.upper(),
                       "LATITUDE": lat,
                       "LONGITUDE": long}
            df = df.append(new_row, ignore_index=True)
        except KeyError:
            print(venue + " has no location coordinates available.")
            continue
    df.sort_values(by=["NAME"], inplace=True, ignore_index=True)
    # Export to Excel file
    df.to_excel("Venues_updated.xlsx")
