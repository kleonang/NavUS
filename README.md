# NavUS #

The `Data` directory contains 3 `JSON` and 3 `Python3` files used to populate Firebase, as well as 1 `Python3` file for the logic of the path calculation. Before running the scripts, ensure that `firebase.json` is replaced with yours downloaded from the `Firebase Console`. Also, edit the file `firebaseurl.txt` to your Firebase's URL from the `Firebase Console`.

- `Python3 Scripts`
  - `InsertBusRoutes.py` is used to populate Firebase at the `/BusRoutes` reference. It contains all the bus *routes* as well as the *time taken* to travel to the next busstop.
  - `InsertBusStops.py` is used to populate Firebase at the `/BusStops` reference. It contains all the bus stop's *Name*, *NextBusAlias* (used to query for bus arrival timings) and *Services* (used to determine the bus services at each bus stop).
  - `InsertVenue.py` is used to populate Firebase at the `/Venues` reference. It contains all the venue's *Name*, *Latitude*, *Longitude* and *IsBusStop* (used to determine if a venue is a bus stop).
  - `CalculatePath.py` constructs a graph with vertices representing a unique busstop and service combination and weighted edges representing the travel time between vertices. Dijkstra's Algorithm is used to calculate the shortest path from the given source to the given destination, and the `getpath` method returns this information in `JSON` format.

- `JSON Files`
  - `BusRoutes.json` contains the bus *routing information* and *time taken* to each stop.
  - `BusStop.json` contains the bus stop's *Name*, *NextBusAlias* and *Services*.
  - `Venue List.json` contains the venue's *Name*, *Latitude*, *Longitude* and *IsBusStop*.
