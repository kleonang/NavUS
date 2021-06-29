# NavUS <img src="https://user-images.githubusercontent.com/35805635/120774197-a3257d80-c554-11eb-804c-579cd979efe1.png" width="80" height="80">


## Motivation
When you’re trying to look for a classroom and you entered the venue in Google maps, there are no suitable results returned. Currently, students have to manually look up the location of their classrooms/lecture theatres on NUSMods, then look up the individual routes of the NUS ISB to get to their intended destination (either through the NextBus app or static ISB map). This project aims to minimise the lookup time. Users just need to input their current location/via GPS and their destination location within campus (e.g. I3-Auditorium to FoS S12 Bldg) and NavUS should provide step-by-step instructions to get from their current location to their destination. NavUS uses live bus timing data to calculate the estimated travel time required and recommends a route with its bus travel time to the destination’s nearest bus stop.

## User Stories
1. As a student in NUS, I want to be able to find the location of my lessons quickly.
2. As a student in NUS, I want to be able to get the bus arrival timings for each bus stop.
3. As a visitor to NUS, I want to be able to locate nearby amenities even without prior knowledge of the NUS campus layout.
4. As an NUS teaching staff, I want to know how to get from one lecture location to my next tutorial location across campus using the ISB.

## Demonstration
Telegram           |  Android App
:-------------------------:|:-------------------------:
<img src="https://user-images.githubusercontent.com/35805635/119498649-6d380a80-bd98-11eb-9992-95b0dc00a5a2.png" width="350" height="600"> |  <img src="https://user-images.githubusercontent.com/35805635/123387498-6d5f3c00-d5ca-11eb-8852-bc17b2470ae9.png" width="350" height="600">


## Features
- **Android App**
  - **Dark mode** support
  - **Estimated Time of Arrival (ETA)** taking into account bus arrival/transit times
  - **Favourites** list for user to store his frequently visited locations
  - **Google Maps** interface to show the route
  - **Multiple routes** suggestions for user to choose from
  - **Real time** bus arrival information at source and transit bus stops
  - **Satelite view** for user to quickly identify landmarks
  - **UI** for user to enter his source and destination, as well as detailed steps to his destination

- **Telegram Bot**
  - **Estimated Time of Arrival (ETA)** taking into account bus arrival/transit times
  - **Messaging UI** for user to enter his source and destination, as well as detailed steps to his destination
  - **Multiple routes** suggestions for user to choose from
  - **Suggestions** when user enters a typo in the source/destination
  - **Real time** bus arrival information at source and transit bus stops

## UI Screenshots
### Android UI
Screen Name                | Demonstration            | Description
:-------------------------:|:-------------------------|:-------------------------
Splash                     | <img src="https://user-images.githubusercontent.com/35805635/123784797-64dc6d80-d90a-11eb-890b-69084903e2ca.gif"> | <ul><li>Screen shown when user launches the application</li></ul>
Main                        | <img src="https://user-images.githubusercontent.com/35805635/123784805-673ec780-d90a-11eb-8760-d8bd121b23df.gif"> | <ul><li>All bus stops around NUS are shown to the user</li><li>Users can tap on any of the bus stop to view the location and name of that bus stop</li></ul>
Search                      | <img src="https://user-images.githubusercontent.com/35805635/123784462-0a431180-d90a-11eb-89e1-a926509e48aa.gif"> | <ul><li>Source is populated with user's location if he enabled location services</li><li>Users can type in part of his destination and be suggested with his destination name</li><li>Users can tap on star icon to add the venue to his favourites</li><li>Favourites will be shown at the top the next time when the user searches for his destination</li></ul>
Route Selection             | <img src="https://user-images.githubusercontent.com/35805635/123786796-aff78000-d90c-11eb-8229-ba570de2f2b4.gif"> | <ul><li>Users can make use of the left and right arrow keys to select their route</li><li>ETA gives the user an estimate of what time he will arrive at his destination</li><li>Number of transits informs the number of times the user must switch buses throughout his route</li></ul>
Route                       | <img src="https://user-images.githubusercontent.com/35805635/123789878-38c3eb00-d910-11eb-9eb2-53797da049e7.gif"> | <ul><li>Users will be asked to head to their source bus stop</li><li>The arrival timing of the buses will be shown to the user</li><li>Users will be informed where to alight for transits via a blue pin as well as the arrival timing of the transit bus service if applicable</li><li>Users will be informed of the bus stop to alight at</li><li>Users will be shown the location of their destination via a red pin and instructed to walk to their destination</li><li>ETA gives the user an estimate of what time he will arrive at his destination</li></ul>


## Implementation Overview
The **Telegram Bot** provides a chat-like interface for users to query the route to their destination.

A **Flask Server** will share a common database on `firebase` with the Telegram Bot as well as the Android app and provide the functionality of computing the route from the user’s location to destination.

The **Android app** provides a visual interface for the user to enter his destination and suggests a route based on his current location **using the nearest 3 bus stops**.
![NavUS Implementation](https://user-images.githubusercontent.com/35805635/123807030-16d36400-d922-11eb-92a8-57c6214f3b6b.png)
The figure above illustrates how the backend of NavUS is implemented.


<img width="975" alt="NavUS Graph_static" src="https://user-images.githubusercontent.com/35778042/123673505-1fb92c80-d873-11eb-80ad-59da9ef6326b.png">
This chart illustrates the graph model currently used for calculating the shortest time taken from a given source and destination. The source in the given diagram is "KR MRT" on service D2, assuming that service D2 is in operation.


_Note: Unlabelled edges are weighted with static travel times._

### Firebase Data Structure
- BusOperatingHours
  - Service (A1, A2, ...)
    - Saturdays
      - Start (String)
      - End (String)
    - SundaysandPH
      - Start (String)
      - End (String)
    - Weekdays
      - Start (String)
      - End (String)
- BusRoutes
  - Service (A1, A2, ...)
    - ID
      - Direction (String)
      - Name (String)
      - Time (String)
- BusStops
  - ID
    - Name (String)
    - NextBusAlias (String)
    - Services (JSONArray String)
- LastUpdated
  - 1
    -   UpdatedDate (String datetime)
- Venues
  - ID
    - IsBusStop (String true/false)
    - Latitude (String)
    - Longitude (String)
    - Name (String)

## Testing
Currently, we had generated a script to test all the possible sources and destinations to ensure that our server is capable of handling simultaneous requests and does not run into any errors. We have also fixed bugs found in the Android application and improved the UI based on feedback. We plan to get more users to try out the application.

## Comparisons with existing applications
**NUS NextBus** app provides us with the bus routes and arrival times of buses but assumes you know the closest bus stop to your destination. Also, there is no functionality to enter a bus stop and be guided from your location.

**NUSMODS** provides us with the location of your classroom but not the routing information to there.

## Technical Stack
- Firebase
- Flask
- NUS NextBus API
- Telegram API
- Android
- Google Maps API

## Project Timeline
Week           |  Task
:-------------------------:|:-------------------------:
4 (31/5 - 6/6)             | Graph Modelling
5 (7/6 - 13/6)             | Integrate real-time bus arrival timings to graph
6 (14/6 - 20/6)            | Design Android UI
7 (21/6 - 27/6)            | Refine and improve Android UI
8 (28/6 - 4/7)             | Implement Telegram bot
9 (5/7 - 11/7)             | Refine Telegram bot
10 (12/7 - 18/7)           | Testing and debugging
11 (19/7 - 25/7)           | Further testing and debugging, while polishing the application as a whole

## Setup Instructions
The `Data` directory contains 4 `JSON` and 4 `Python3` files used to populate Firebase, as well as 1 `Python3` file for the logic of the path calculation. Before running the scripts, ensure that `firebase.json` is replaced with yours downloaded from the `Firebase Console`. Also, edit the file `firebaseurl.txt` to your Firebase's URL from the `Firebase Console`.

- ### `Python3 Scripts`
  - `InsertBusOperatingHours.py` is used to populate Firebase at the `/BusOperatingHours` reference. It contains all the bus operating hours on *Weekdays*, *Saturdays*, and *Sundays and Public Holidays*.
  - `InsertBusRoutes.py` is used to populate Firebase at the `/BusRoutes` reference. It contains all the bus *routes* as well as the *time taken* to travel to the next busstop.
  - `InsertBusStops.py` is used to populate Firebase at the `/BusStops` reference. It contains all the bus stop's *Name*, *NextBusAlias* (used to query for bus arrival timings) and *Services* (used to determine the bus services at each bus stop).
  - `InsertVenue.py` is used to populate Firebase at the `/Venues` reference. It contains all the venue's *Name*, *Latitude*, *Longitude* and *IsBusStop* (used to determine if a venue is a bus stop).
  - `CalculatePath.py` constructs a graph with vertices representing a unique busstop and service combination and weighted edges representing the travel time between vertices. Dijkstra's Algorithm is used to calculate the shortest path from the given source to the given destination, and the `getpath` method returns this information in `JSON` format.
  - `VenueFinder.py` queries the NUSMods API for the list of lesson venues in a given Academic Year and Semester, then scrapes the NUSMods website to get the coordinates (latitude and longitude) of all available venues.

- ### `JSON Files`
  - `BusOperatingHours.json` contains the bus *operating hours*.
  - `BusRoutes.json` contains the bus *routing information* and *time taken* to each stop.
  - `BusStop.json` contains the bus stop's *Name*, *NextBusAlias* and *Services*.
  - `Venue List.json` contains the venue's *Name*, *Latitude*, *Longitude* and *IsBusStop*.

The `App` directory contains the code for the `Android` application. Update `App\src\main\java\com\example\navus\APIKeys.java` with your Firebase's URL and `App\src\debug\res\values\google_maps_api.xml` with your Google map API key. Add `google_services.json` downloaded from Firebase Console to the `App` directory.

The `APK` file can be downloaded [here](https://github.com/alvintan01/NavUS/raw/main/App/NavUS.apk).
