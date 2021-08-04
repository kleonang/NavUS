# NavUS
![NavUS Logo v1_rounded_tiny](https://user-images.githubusercontent.com/35778042/127007786-5d7f2ac2-790e-42e5-99e4-d206766bc86b.png)
## Table of contents
- [Motivation](#motivation)
- [User Stories](#user-stories)
- [Demonstration](#demonstration)
- [Features](#features)
  - [Android App](#android-app)
  - [Telegram Bot](#telegram-bot)
- [UI Screenshots](#ui-screenshots)
  - [Android UI](#android-ui)
  - [Telegram UI](#telegram-ui)
- [Implementation Overview](#implementation-overview)
  - [Firebase Data Structure](#firebase-data-structure)
  - [Flask URL Parameters](#flask-url-parameters)
    - [getpath](#getpath)
    - [getarrivaltimings](#getarrivaltimings)
- [Testing](#testing)
- [Software Engineering Practices](#software-engineering-practices)
  -  [Separation of Working and Production Copies of Code](#separation-of-working-and-production-copies-of-code)
  -  [Adherance to Established Code Style](#adherance-to-established-code-style)
  -  [Error Handling](#error-handling)
- [Comparisons with Existing Applications](#comparisons-with-existing-applications)
- [Technical Stack](#technical-stack)
  -  [Python Libraries](#python-libraries)
- [Limitations](#limitations)
- [Project Timeline](#project-timeline)
- [Setup Instructions](#setup-instructions)
  - [Python3 Scripts](#python3-scripts)
  - [JSON Files](#json-files)

## Motivation
When you’re trying to look for a classroom and you entered the venue in Google maps, there are no suitable results returned. Currently, students have to manually look up the location of their classrooms/lecture theatres on NUSMods, then look up the individual routes of the NUS ISB to get to their intended destination (either through the NextBus app or static ISB map). This project aims to minimise the lookup time. Users just need to input their current location (or send their location via GPS) and their destination location within the NUS campus (e.g. I3-Auditorium to FoS S12 Bldg) and NavUS should provide step-by-step instructions to get from their current location to their destination. NavUS uses live bus timing data to calculate the estimated travel time required and recommends a route with its bus travel time to the destination’s nearest bus stop.

## User Stories
1. As a **student** in NUS, I want to be able to find the location of my lessons quickly.
2. As a **student** in NUS, I want to be able to get the bus arrival timings for each bus stop.
3. As a **visitor** to NUS, I want to be able to locate nearby amenities even without prior knowledge of the NUS campus layout.
4. As an NUS teaching **staff**, I want to know how to get from one lecture location to my next tutorial location across campus using the ISB.

## Demonstration
Telegram           |  Android App
:-------------------------:|:-------------------------:
<img src="https://user-images.githubusercontent.com/35805635/125159244-d5a44500-e1a8-11eb-8335-9d144b399543.png" width="360" height="740"> |  <img src="https://user-images.githubusercontent.com/35805635/125159252-e785e800-e1a8-11eb-8ff3-948d27325c65.png" width="360" height="740">

## Features
- ### Android App ([download link](https://github.com/alvintan01/NavUS/raw/main/App/NavUS.apk))
  - **Background Notification** that guides users when NavUS is running in the background
  - **Dark mode** support
  - **Estimated Time of Arrival (ETA)** taking into account bus arrival/transit times
  - **Favourites** list for users to store frequently visited locations
  - **Google Maps** interface to show the route, with auto pan and polylines that guides users based on his current location
  - **Multiple-route recommendation** for user to choose their preferred route
  - **Real-time bus arrival information** at bus stops
  - **Recent searches** list that shows the last 5 searches
  - **Satellite view** for users to quickly identify landmarks
  - **Tutorial** guides users on how to interact with the application
  - **Interactive UI** for users to enter their source and destination, as well as browse through the detailed navigation directions

- ### Telegram Bot ([@NavUSBot](https://t.me/NavUSBot))
  - **Estimated Time of Arrival (ETA)** taking into account bus arrival/transit times
  - **Messaging UI** for users to enter their source and destination, as well as provide detailed steps to their destination
  - **Multiple-route recommendation** for users to choose their preferred route
  - **Real-time bus arrival information** at bus stops
  - **Venue suggestions** when users enter a typo in the source/destination

## UI Screenshots
### Android UI
Screen Name                | Demonstration            | Description
:-------------------------:|:-------------------------|:-------------------------
Splash                     | <img src="https://github.com/alvintan01/NavUS/blob/main/Gif/SplashScreen.gif"> | <ul><li>Screen shown when user launches the application</li><li>Bus arrival timings at nearest bus stop will be shown</li></ul>
Main                        | <img src="https://github.com/alvintan01/NavUS/blob/main/Gif/Main.gif"> | <ul><li>All bus stops around NUS are shown to the user</li><li>Users can tap on any bus stop to view its name, location and bus arrival timings</li><li>Satellite view helps users to easily identify landmarks</li></ul>
Search                      | <img src="https://github.com/alvintan01/NavUS/blob/main/Gif/Search.gif"> | <ul><li>Source is populated with user's location if Location Services was enabled</li><li>Users can type in part of the destination and get text suggestions for the full destination name</li><li>Users can tap on a star icon to add the corresponding venue to the favourites list</li><li>Favourites will be shown at the top the next time the user searches for a destination</li><li>Recent top 5 search histroy will be shown under the user's favourites</li><li>A bus icon indicates the location is a bus stop</li></ul>
Route Selection             | <img src="https://github.com/alvintan01/NavUS/blob/main/Gif/Route Selection.gif"> | <ul><li>Users can make use of the left and right arrow keys to select their route</li><li>ETA gives users an estimate of what time they will arrive at their destination</li><li>Number of transits indicates the number of times the user must switch buses throughout the route</li></ul>
Route Navigation            | <img src="https://github.com/alvintan01/NavUS/blob/main/Gif/Route Navigation.gif"> | <ul><li>Users will be shown there current location with a green pin</li><li>Users will be asked to head to their source bus stop</li><li>Arrival timing of buses will be shown to the user</li><li>Users will be informed where to alight for transits with a blue pin as well as the arrival timing of the transit bus service (if applicable)</li><li>Users will be notified of the bus stop to alight at with a red pin</li><li>Users will be shown the location of their destination with a red pin and instructed to walk to their destination</li><li>ETA from the previous screen will be displayed throughout the route (when auto pan is enabled)</li><li>Left and right arrow keys allows user to view the details of their route in advance</li><li>Auto pan follows the user's location and shows the user the next bus stop they are approaching</li><li>Background service allows users to use other applications on their mobile while NavUS will continue to guide them based on the user's location</li><li>A stop button is included on the notification to allow users to quickly end the navigation</li><li>Text will flash and phone vibrates to alert user if NavUS is in the foreground and user is near a transit stop</li></ul>
Tutorial                | <img src="https://github.com/alvintan01/NavUS/blob/main/Gif/Tutorial.gif"> | <ul><li>Users are guided on how to interact with the application during the first launch</li></ul>

### Telegram UI
Command Name               | Demonstration            | Description
:-------------------------:|:-------------------------|:-------------------------
/cancel                  | <img src="https://github.com/alvintan01/NavUS/blob/main/Gif/Cancel.gif"> | <ul><li>Allows the user to re-enter his source</li><ul>
/help                    | <img src="https://github.com/alvintan01/NavUS/blob/main/Gif/Help.gif"> | <ul><li>Shows all commands of the NavUS Telegram Bot</li><ul>
/more                    | <img src="https://github.com/alvintan01/NavUS/blob/main/Gif/More.gif"> | <ul><li>Shows alternative routes if queried within 1 min to ensure routes are still accurate</li><ul>
/query                   | <img src="https://github.com/alvintan01/NavUS/blob/main/Gif/Query.gif"> | <ul><li>Allows the user to query bus arrival timings</li><li>Returns a list of all the bus stops for the user to select</li><li>Suggests close matches to user's input in case of typos</li><li>Returns the user the arrival timings of all bus services at that bus stop</li><ul>
/start                   | <img src="https://github.com/alvintan01/NavUS/blob/main/Gif/Start.gif"> | <ul><li>Allows the user to start communicating with the NavUS Telegram Bot</li><li>Requests the user to send his source via GPS or text</li><li>Requests the user to send his destination via text</li><li>Suggests close matches to user's input in case of typos for both source and destination</li><li>Returns the user the best suggested route with bus arrival timings at source and transits bus stop as well as ETA</li></ul>

  
## Implementation Overview
The **Flask Server** will share a common database on Firebase with the Telegram Bot as well as the Android app and provide the functionality of computing the route from the user’s location (or selected source) to the destination.

The **Android App** provides a visual interface for users to enter their destination and suggests a route based on their current location using the **3 nearest bus stops**.

The **Telegram Bot** provides a chat-like interface for users to query the route to their destination.
![NavUS Implementation](https://user-images.githubusercontent.com/35805635/123807030-16d36400-d922-11eb-92a8-57c6214f3b6b.png)
The figure above illustrates how the backend of NavUS is implemented. Firebase stores most of the data in our application which includes the BusOperatingHours, BusRoutes, BusStops, LastUpdated and Venues.
  
The Android application checks if the venue information in Firebase was updated since the last time the application was launched and updates it if necessary. Once the user enters a source and destination it will send a request to the Flask server to get the routing information as well as the bus arrival timings. Once the routing information in JSON format is received, it will then send a request to Googles directions API to get the polyline to be displayed on the map. If the user taps on a bus stop icon, a request will also be sent to the Flask server to retrieve the bus arrival timings.
  
The Telegram bot is hosted using Python which will help to reply to the user's queries as well as prompt the user for their source/destination/bus stop name to query bus arrival timings. It would also suggest venue names if the user entered an invalid venue. Once it receives a valid query, it will request the Flask server to get the information returned in JSON format. It would then help to format the data received into a text message and replies it to the user.

<img width="1346" alt="NavUS Graph_Flattened" src="https://user-images.githubusercontent.com/35778042/128189096-ad53110c-8bc8-4a55-8c9e-eedfa726cacf.png">
_Note: Edges are weighted with static travel times._
  
The above chart illustrates the graph model currently used for calculating the shortest time taken from a given source and destination. It is a graph of all bus stops as vertices, with directed edges connecting two vertices if there is at least one ISB service that goes from one bus stop to the other. A modified Breadth First Search (BFS) algorithm is run on the graph when a user queries our server with a source and destination, finding all possible routes within a specified maximum number of stops. Then, BFS is run a second time to get the possible services serving the generated routes. The resulting routes are then passed through our filtering algorithms to remove duplicated or similar routes. Finally, we integrate the live bus arrival timings from the NextBus API to give multiple complete route recommendations to the user.

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
    - Services (String: JSONArray)
- LastUpdated
  - 1
    -   UpdatedDate (String: datetime)
- Venues
  - ID
    - IsBusStop (String: boolean)
    - Latitude (String)
    - Longitude (String)
    - Name (String)

### Flask URL Parameters
  #### getpath
  - getpath/&lt;source&gt;/&lt;destination&gt;
  - getpath/&lt;source&gt;/&lt;destinationlatitude&gt;/&lt;destinationlongitude&gt;
  - getpath/&lt;sourcelatitude&gt;/&lt;sourcelongitude&gt;/&lt;destination&gt;/&lt;destinationlongitude&gt;
 
  where source and destination are names found in `Venue List.json`. Returns the result in the following JSON format.
  - ID (String ID of the route starting from 0)
    - Route (Array of the waypoints)
      - Name (String)
      - Service (String)
      - Latitude (String)
      - Longitude (String)
      - IsBusStop (String true/false)
      - BusArrivalTime (String in HH:MM am/pm, '-' if unknown)
      - BusArrivalTimeMins (String mins, '-' if unknown)
    - ETA (String in HH:MM am/pm, '-' if unknown)
    - TravelTime (String mins, '-' if unknown)
  
  #### getarrivaltimings
  - getarrivaltimings/&lt;bus stop name&gt;
  
  where bus stop name are found in `Venue List.json with IsBusStop == 'true'`. Returns the result in the following JSON array.
  - Service (String)
    - arrivalTime (String, '-' if unknown/Not operating)
    - nextArrivalTime (String, '-' if unknown/Not operating)
  
## Testing
Currently, we have utilised a script to test all possible combinations of sources and destinations to ensure that our server is capable of handling simultaneous requests and without running into errors. We have also fixed bugs found in the Android application and improved the UI based on initial feedback. 
  
We first released the Android application to a select group of beta testers who can provide feedback with their usage experience. We have also rolled out the Android application and Telegram bot to more users via publicity in Telegram groups for testing by using the application as part of their daily commute around NUS. We are collecting feedback based on these user experiences via [an online feedback form](https://docs.google.com/forms/d/e/1FAIpQLSeitP9waxrhQDNsSO33p7dvhls3KbV5JX-eTABu_K0h-tslXA/viewform?usp=sf_link).

## Software Engineering Practices
### Separation of Working and Production Copies of Code
A working copy of our entire codebase is kept in a shared Google Drive folder that is only accessible to the developers. Similar to GitHub's version history, Google Drive has the functionality to update a file to a new version while keeping all past iterations of the file. This helps us keep track of changes to our code and enables us to easily revert to previous versions if needed. 

A production copy (that can be run by the end-user) is maintained on GitHub and is publicly accessible. An advantage of separating our working and production codebase is keeping public and private files securely segregated. This ensures that essential private information like API keys are only accessible to NavUS developers via Google Drive while the majority of our code is kept open-source on GitHub. 
  
### Adherence to Established Code Style
For our main server-run Python scripts, `CalculatePath.py` and `TelegramBot.py`, we ensured that the code conforms to the [PEP 8 Style Guide](https://www.python.org/dev/peps/pep-0008/). This makes the code for these essential files more readable and enables other software developers from outside the NavUS development team to comprehend our code more easily. In the above Python scripts, we adhered to the naming conventions, styles as well as formatting recommendations of code and comments in PEP 8.
  
### Error Handling
For all our applications, we have ensured that they run as intended and inform the user of ongoing processes as far as possible. For example, a loading circle animation would play when the Android application is fetching data from our server to let the user know that the application is currently performing tasks. Also, the application would pause the tutorial if the user entered a source and destination with no route. The tutorial will resume from where it left off when a route can be found. If the user has no internet access, the application would alert the user that it cannot connect to the route server. 
  
On the server end, we ensured that routes can still be generated even when the NextBus API is down. However, the estimated time of arrival would not be provided in this case. In the event that our server is down, our Telegram bot would alert the user that it is unable to connect to the route server.
  
We have also made two servers available for calculating routes: one each on Google Cloud and PythonAnywhere. This additional layer of redundancy enables us to redirect queries from one server to the other in case of server-side maintenance activities beyond our control.

## Comparisons with Existing Applications
**NUS NextBus** app provides us with the bus routes and arrival times of buses but assumes you know the closest bus stop to your destination. Also, there is no functionality to enter a bus stop and get directions from your location to your intended destination.

**NUSMODS** provides us with the location of classrooms but not the routing information to get there.

## Technical Stack
- Android Studio
- Firebase
- Flask
- Google Cloud
- Google Maps API
- NextBus API
- PythonAnywhere
- Telegram API

### Python Libraries
- `asyncio` and `aiohttp`
  - Enables querying the NextBus API for bus arrival timings asynchronously (up to 7x speedup compared to synchronous requests)
- `copy`
  - Enables creation of deep copies of entire (custom) Graph structures with attached references to their Nodes
- `datetime`
  - Enables checking of current time as well as performing comparisons of time values
- `firebase_admin`
  - Enables fetching of data from our Firebase server
- `flask`
  - Enables `CalculatePath.py` to be run as a Python web application
- `heapq`
  - Enables creation of priority queue central to Dijkstra's Algorithm, as well as miscellaneous ranking operations
- `holidays`
  - Enables fetching of Singapore public holidays to get the corresponding operating timings for bus services
- `json`
  - Enables parsing of replies from API queries and return of recommended routes to our web server
- `math`
  - Enables performing standard mathematical operations
- `os` and `time`
  - Enables setting the server time to Singapore Time (UTC +8) for standardisation

  
## Limitations
Currently, our application only allows routes covered by the NUS Internal Bus Shuttle (ISB). It does not include routes covered by public buses. Also, the Android application only runs on Android devices, as such iOS users can only use the Telegram Bot.

## Project Timeline
Week           |  Task
:-------------------------:|:-------------------------:
4 (31/5 - 6/6)             | Graph Modelling
5 (7/6 - 13/6)             | Integrate real-time bus arrival timings to graph
6 (14/6 - 20/6)            | Design Android UI
7 (21/6 - 27/6)            | Refine and improve Android UI
8 (28/6 - 4/7)             | Implement Telegram bot
9 (5/7 - 11/7)             | Refine Telegram bot features
10 (12/7 - 18/7)           | Testing and debugging
11 (19/7 - 25/7)           | Further testing and debugging, while polishing the applications as a whole

## Setup Instructions
The `Data` directory contains 4 `JSON` and 4 `Python3` files used to populate Firebase, 1 `Python3` file for the logic of the path calculation, 1 `Python3` file for the logic of the Telegram Bot, as well as 1 `Python3` file used to collect venue data. Before running the scripts, ensure that `firebase.json` is replaced with yours downloaded from the `Firebase Console`. Also, edit the file `firebaseurl.txt` to your Firebase's URL from the `Firebase Console`. For Telegram Bot edit the file `telegramapikey.txt` with yours from `BotFather` and the file `server_url.txt` with the URL of your server running `CalculatePath.py`.

- ### `Python3` Scripts
  - `InsertBusOperatingHours.py` is used to populate Firebase at the `/BusOperatingHours` reference. It contains all the bus operating hours on *Weekdays*, *Saturdays*, and *Sundays and Public Holidays*.
  - `InsertBusRoutes.py` is used to populate Firebase at the `/BusRoutes` reference. It contains all the bus *routes* as well as the *time taken* to travel to the next bus stop.
  - `InsertBusStops.py` is used to populate Firebase at the `/BusStops` reference. It contains all the bus stop's *Name*, *NextBusAlias* (used to query for bus arrival timings) and *Services* (used to determine the bus services at each bus stop).
  - `InsertVenue.py` is used to populate Firebase at the `/Venues` reference. It contains all the venue's *Name*, *Latitude*, *Longitude* and *IsBusStop* (used to determine if a venue is a bus stop).
  - `CalculatePath.py` constructs a graph with vertices representing a unique bus stop and service combination and weighted edges representing the travel time between vertices. Dijkstra's Algorithm is used to calculate the shortest path from the given source to the given destination, and the `getpath` method returns this information in `JSON` format running on Flask. The `getarrivaltimings` method returns the bus arrival timings in `JSON` format.
  - `TelegramBot.py` helps to run the Telegram Bot to get the user's input to query the bus arrival timings as well as routing information from `CalculatePath.py`. It then receives the `JSON` data from Flask and formats it into a text message to reply to the user.
  - `VenueFinder.py` queries the NUSMods API for the list of lesson venues in a given Academic Year and Semester, then scrapes the NUSMods website to get the coordinates (latitude and longitude) of all available venues.

- ### `JSON` Files
  - `BusOperatingHours.json` contains the bus *operating hours*.
  - `BusRoutes.json` contains the bus *routing information* and *time taken* to each stop.
  - `BusStop.json` contains the bus stop's *Name*, *NextBusAlias* and *Services*.
  - `Venue List.json` contains the venue's *Name*, *Latitude*, *Longitude* and *IsBusStop*.

The `App` directory contains the code for the `Android` application. Update `App\src\main\java\com\example\navus\APIKeys.java` with your Firebase's URL and `App\src\debug\res\values\google_maps_api.xml` with your Google map API key. Add `google_services.json` downloaded from Firebase Console to the `App` directory.

The `APK` file of the latest version of NavUS can be downloaded [here](https://github.com/alvintan01/NavUS/raw/main/App/NavUS.apk). The Android application is currently free to download and use from GitHub, all that we ask is that you help us improve the app by letting us know your user feedback via [this short feedback form](https://forms.gle/NfEULn6eEUViywUy9). 
  
Feel free to contact [Alvin](https://github.com/alvintan01) or [Kleon](https://github.com/kleonang) if you have any feedback or suggestions to improve NavUS.
