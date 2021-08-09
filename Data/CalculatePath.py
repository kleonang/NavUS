# INSTALL DEPENDENCIES FIRST
# pip3 install aiohttp
# pip3 install firebase-admin
# pip3 install Flask
# pip3 install holidays

import asyncio
import datetime
import heapq
import json
import math
import os
import time

import aiohttp
import firebase_admin
import holidays

from collections import deque
from firebase_admin import credentials, db
from flask import Flask

# DECLARE GLOBAL VARIABLES
app = Flask(__name__)

route_dict = {}  # Key: Bus Service, Value: [(Waypoint, TravelTime, Direction)]
venue_dict = {}  # Key: Venue, Value: (Latitude, Longitude, IsBusStop)
bus_stop_coordinates = {}  # Key: Bus Stop, Value: (Latitude, Longitude)
bus_stop_dict = {}  # Key: Bus Stop, Value: {NextBusAlias, Services}
bus_operating_hours_dict = {}  # Key: Bus Service, Value: Daily Operating Hours
bus_arrival_time_dict = {}  # Key: Bus Stop, Value: Arrival time for services
directions = []  # [Bus Stops with attached directions]
bus_terminal_stop = {}  # Key: Bus Service, Value: Last Bus Stop

# DECLARE GLOBAL CONSTANTS
NUM_SOURCES = 3
NUM_DESTS = 3
MAX_BUS_STOPS = 12
MAX_NUM_TRANSITS = 1
MAX_ROUTES_TO_RETURN = 5  # Maximum no of routes to return
MAX_DIST = 0.0015  # Constant for euclidean distance


def get_walking_time(lat1, long1, lat2, long2):
    """Returns walking time in mins between 2 sets of coordinates."""
    p = math.pi / 180
    a = 0.5 - math.cos((lat2 - lat1) * p) / 2 + math.cos(lat1 * p) * \
        math.cos(lat2 * p) * (1 - math.cos((long2 - long1) * p)) / 2
    distance = 12742 * math.asin(math.sqrt(a))
    return math.ceil(distance / (4 / 60))  # Assume walking speed is 4km/h


def get_time():
    """Returns the current time."""
    return datetime.datetime.now()


def check_operating_services():
    """Checks and updates the current operating services based on the
    bus operating timings in database.
    """
    operating_services = []
    now = get_time()  # Time now
    day_number = now.weekday()
    day_to_check = ""

    if holidays.Singapore().get(now.strftime('%Y-%m-%d')) \
            is not None or day_number == 6:  # Public Holiday / Sunday
        day_to_check = "SundaysandPH"
    elif day_number == 5:  # Saturday
        day_to_check = "Saturdays"
    else:  # Weekday
        day_to_check = "Weekdays"

    for service in bus_operating_hours_dict.keys():
        start = bus_operating_hours_dict[service][day_to_check]["Start"]
        end = bus_operating_hours_dict[service][day_to_check]["End"]

        if start != "-" and end != "-":  # Ensure start and end are not null
            start_time = datetime.time(
                hour=int(start[0:2]), minute=int(start[2:4]))
            end_time = datetime.time(hour=int(end[0:2]), minute=int(end[2:4]))
            if start_time <= now.time() <= end_time:
                # Service is operating
                operating_services.append(service)

    return operating_services


# REALTIME DATA
async def fetch_arrival_times(session, bus_stop):
    """Fetches live bus arrival times from NextBus API."""
    if get_time() - bus_arrival_time_dict[bus_stop]["QueryTime"] \
            < datetime.timedelta(minutes=1):  # less than 1 min
        # Do nothing if bus stop is cached and updated <1 min ago
        return

    for i in range(3):  # Retry to connect 3 times if connection fails
        try:
            async with session.get(api_url
                                   + bus_stop_dict[bus_stop]["NextBusAlias"],
                                   timeout=5) as response:
                if response.status == 200:
                    # Set query time
                    bus_arrival_time_dict[bus_stop]["QueryTime"] = get_time()
                    data = await response.json(content_type='text/html')
                    for bus_route in data["ShuttleServiceResult"]["shuttles"]:
                        # Handle Arr case
                        if bus_route["arrivalTime"] == "Arr":
                            bus_route["arrivalTime"] = "0"
                        if bus_route["nextArrivalTime"] == "Arr":
                            bus_route["nextArrivalTime"] = "0"
                        # Handle negative case
                        if bus_route["arrivalTime"] != "-" \
                                and int(bus_route["arrivalTime"]) < 0:
                            bus_route["arrivalTime"] = "-"
                        if bus_route["nextArrivalTime"] != "-" \
                                and int(bus_route["nextArrivalTime"]) < 0:
                            bus_route["nextArrivalTime"] = "-"
                        # To capitalise D1 (To BIZ 2) etc and remove spaces
                        bus_service = bus_route[
                            "name"].upper().replace(" ", "")
                        # Disambiguate D1 shuttles at COM 2
                        if bus_stop == "COM 2" and bus_service == "D1":
                            direction = bus_route["busstopcode"].split("-")[-1]
                            direction = "UTOWN" if direction == "UT" \
                                else direction
                            bus_service += "(TO" + direction + ")"
                        # Insert arrival timings into bus_arrival_time_dict
                        bus_arrival_time_dict[bus_stop][bus_service] = {
                            "arrivalTime": bus_route["arrivalTime"],
                            "nextArrivalTime": bus_route["nextArrivalTime"]}
                    return
                else:
                    print("Cannot fetch arrival times. HTTP response status: "
                          + str(response.status))
                    continue
        except Exception as e:
            print("Unable to fetch arrival times for " + bus_stop
                  + " on attempt " + str(i) + ". Exception is " + str(e))
            time.sleep(0.5)

    # Failed to connect to NextBus API.
    # Return "-1" for all arrival timings at this bus stop.
    bus_arrival_time_dict[bus_stop]["QueryTime"] = get_time()
    services = bus_stop_dict[bus_stop]["Services"]
    for service in services:
        bus_arrival_time_dict[bus_stop][service.upper().replace(" ", "")] = {
            "arrivalTime": "5", "nextArrivalTime": "20"}
    return


async def get_arrival_times(stops_to_query):
    """Get live bus arrival times asynchronously"""
    async with aiohttp.ClientSession() as session:
        tasks = []
        for stop in stops_to_query:
            tasks.append(
                asyncio.ensure_future(fetch_arrival_times(session, stop)))
        await asyncio.gather(*tasks)


def euclidean_distance(this_lat, this_long, other_lat, other_long):
    """Calculates euclidean distance between two points"""
    return math.sqrt(math.pow(this_lat - other_lat, 2)
                     + math.pow(this_long - other_long, 2))


def construct_graph(operating_services):
    """Construct the Graph"""
    # Key: Bus Stop, Value: {Next Bus Stop, Time, Direction, Service}
    bus_service_dict = {}
    # Stores the graph
    graph = {}
    # Indicates if the graph exists
    valid_graph = False

    # Initialise all bus stops as empty list
    for bus_stop in bus_stop_dict:
        graph[bus_stop] = []
        bus_service_dict[bus_stop] = []

    for service in route_dict:
        # Ensure services are operating
        if service in operating_services:
            for i in range(0, len(route_dict[service])):
                current_bus_stop, timing, direction = route_dict[service][i]
                if i != len(route_dict[service]) - 1:
                    next_bus_stop_name, next_time, next_direction = route_dict[
                        service][i + 1]
                    # Add the edges, ensure it does not already exist
                    if next_bus_stop_name not in graph[current_bus_stop]:
                        graph[current_bus_stop].append(next_bus_stop_name)
                        valid_graph = True
                    bus_service_dict[current_bus_stop].append(
                        (next_bus_stop_name, timing, direction, service))
    return bus_service_dict, graph, valid_graph


def check_visited(path, node):
    """Checks if the path contains node"""
    # If more than 10 bus stops, skip it
    return len(path) > MAX_BUS_STOPS or node in path


def get_all_paths(graph, source, destination):
    """Returns all possible paths from source to destination"""
    q = deque()
    path_list = []
    path = [source]

    q.append(path.copy())

    while q:
        path = q.popleft()
        last_node = path[len(path) - 1]

        if last_node == destination:
            path_list.append(path)

        for neighbour in graph[last_node]:
            if not check_visited(path, neighbour):
                new_path = path.copy()
                new_path.append(neighbour)
                q.append(new_path)
    return path_list


def add_transits(path):
    """Adds duplicate transit bus stops"""
    formatted_path = []
    for i in range(0, len(path)):
        bus_stop, timing, direction, service = path[i]

        if i != 0:
            prev_bus_stop, prev_time, prev_direction, prev_serv = path[i - 1]

            if service != prev_serv:
                # Check for terminal bus stop
                if bus_terminal_stop[prev_serv] == prev_bus_stop:
                    formatted_path.append(
                        (prev_bus_stop, 0, prev_direction, prev_serv))
                    formatted_path.append(
                        (bus_stop, 0, prev_direction, prev_serv))
                else:
                    formatted_path.append(
                        (bus_stop, 0, prev_direction, prev_serv))

            else:
                # Check for terminal bus stop and
                # ensure it is not the second bus stop
                if bus_terminal_stop[prev_serv] == prev_bus_stop and i != 1:
                    formatted_path.append(
                        (prev_bus_stop, 0, prev_direction, prev_serv))

        formatted_path.append((bus_stop, timing, direction, service))
    return formatted_path


def get_path_with_service(path_list, bus_service_dict):
    """Returns paths with the bus services"""
    path_list_with_services = []

    for path in path_list:
        q = deque()
        path_with_service = [(path[0], "", "", "")]

        q.append(path_with_service.copy())

        while q:
            path_with_service = q.popleft()

            num_transits = -1
            prev_serv = ""
            # Count number of transits
            for bus_stop, timing, direction, service in path_with_service:
                # Skip the last bus stop which has no service
                if service == "":
                    continue
                if prev_serv != service:
                    # Check for terminal bus stop
                    if bus_terminal_stop[service] == bus_stop:
                        num_transits += 2
                    else:
                        num_transits += 1
                else:
                    # Check for terminal bus stop
                    if bus_terminal_stop[service] == bus_stop:
                        num_transits += 1

                prev_serv = service

            # Ignore routes with more than max no of transits
            if num_transits > MAX_NUM_TRANSITS:
                continue

            index = len(path_with_service) - 1

            current_node = path[index]

            if len(path_with_service) == len(path):  # Reached destination
                prev_serv = path_with_service[-1][3]
                # Add destination name
                path_with_service.append(
                    (path[len(path_with_service) - 1], 0, "-", prev_serv))
                # Remove first index as it was a placeholder
                path_list_with_services.append(path_with_service[1:])
                continue

            for neighbour, timing, direction, service \
                    in bus_service_dict[current_node]:
                if neighbour == path[index + 1]:
                    new_path_with_service = path_with_service.copy()
                    new_path_with_service.append(
                        (current_node, timing, direction, service))
                    q.append(new_path_with_service)
    return path_list_with_services


# COMMENT OUT NEXT LINE FOR OFFLINE TESTING
@app.route('/getpath/<source>/<destination>')
def get_path(source, destination):
    """Get path from source to destination.
    Access at 127.0.0.1:5000/getpath/your_source/your_destination
    """
    try:
        source_lat = venue_dict[source][0]
        source_long = venue_dict[source][1]
        dest_lat = venue_dict[destination][0]
        dest_long = venue_dict[destination][1]
        return get_path_using_coordinates(source_lat, source_long,
                                          dest_lat, dest_long)
    except Exception:  # An invalid venue was given
        return "Invalid Venue!"


# COMMENT OUT NEXT LINE FOR OFFLINE TESTING
@app.route('/getpath/<parameter1>/<parameter2>/<parameter3>')
def get_path_using_one_coordinate(parameter1, parameter2, parameter3):
    """Get path from source to destination,
    where only either source or destination was passed as coordinates.
    Access at 127.0.0.1:5000/getpath/parameter1/parameter2/parameter3
    """
    try:
        try:
            float(parameter1)  # source was sent as coordinates
            source_lat = parameter1
            source_long = parameter2
            dest_lat = venue_dict[parameter3][0]
            dest_long = venue_dict[parameter3][1]

        except ValueError:  # destination was sent as coordinates
            source_lat = venue_dict[parameter1][0]
            source_long = venue_dict[parameter1][1]
            dest_lat = parameter2
            dest_long = parameter3

        return get_path_using_coordinates(source_lat, source_long,
                                          dest_lat, dest_long)
    except Exception:  # An invalid venue was given/wrong parameters
        return "Invalid Venue/URL Parameters!"


# COMMENT OUT NEXT LINE FOR OFFLINE TESTING
@app.route('/getpath/<source_lat>/<source_long>/<dest_lat>/<dest_long>')
def get_path_using_coordinates(source_lat, source_long, dest_lat, dest_long):
    """Get path from source to destination,
    where both source and destination were passed as coordinates.
    Access at 127.0.0.1:5000/getpath/source_lat/source_long/dest_lat/dest_long
    """
    # Construct graph and get operating_services
    operating_services = check_operating_services()

    data = {}  # Initialise as empty dictionary for data to be returned

    bus_service_dict, graph, valid_graph = construct_graph(operating_services)

    if not valid_graph:  # No services operating
        return json.dumps(data)

    source_lat = float(source_lat)
    source_long = float(source_long)
    dest_lat = float(dest_lat)
    dest_long = float(dest_long)

    sources = []
    dests = []
    sources_dist = []
    dests_dist = []
    # Priority queue for sources based on dist from source coordinates
    sources_pq = []
    heapq.heapify(sources_pq)
    # Priority queue for destinations based on dist from dest coordinates
    dests_pq = []
    heapq.heapify(dests_pq)

    for stop in bus_stop_coordinates:
        dist_to_source = euclidean_distance(
            source_lat,
            source_long,
            bus_stop_coordinates[stop][0],
            bus_stop_coordinates[stop][1])
        heapq.heappush(sources_pq, (dist_to_source, stop))
        dist_to_dest = euclidean_distance(
            dest_lat,
            dest_long,
            bus_stop_coordinates[stop][0],
            bus_stop_coordinates[stop][1])
        heapq.heappush(dests_pq, (dist_to_dest, stop))

    for i in range(NUM_SOURCES):
        s = heapq.heappop(sources_pq)
        sources.append(s[1])
        sources_dist.append(s[0])

    for j in range(NUM_DESTS):
        d = heapq.heappop(dests_pq)
        dests.append(d[1])
        dests_dist.append(d[0])

    # Stores all the path after filtered
    path_list = []
    # Stores all (route information, total travel time)
    all_route_list = []
    # Stores all the paths after removing paths containing the user's closest
    # bus stop at source/destination as waypoint
    filtered_all_path = []
    unique_path = {}  # To store the least number of transits for each path

    for destination in dests:
        for source in sources:

            # Get all possible paths from source to destination
            all_paths = get_all_paths(graph, source, destination)

            for path in all_paths:
                # Flag to know if path contains source or destination
                remove_path = False
                for i in range(len(path)):
                    # Remove all paths containing the user's surrounding bus
                    # stop that is not the first bus stop for the source,
                    # and remove all paths containing the bus stop surrounding
                    # the destination that is not the last bus stop.
                    # Neighbouring stops must be within the first 2/last 2
                    if (i != 0 and path[i] == sources[0]) \
                        or (i != len(path) - 1 and path[i] == dests[0]) \
                        or (i != 0 and i != 1 and path[i] in sources[1:]) \
                        or (i != len(path) - 1 and i != len(path) - 2
                            and path[i] in dests[1:]):
                        remove_path = True
                        break

                if not remove_path:
                    # Add path to filtered path if it does not contain
                    # source and destination as waypoint
                    filtered_all_path.append(path)

            # Add the services to the path
            for path in get_path_with_service(filtered_all_path,
                                              bus_service_dict):
                # Add transit bus stop information
                path_with_transits = add_transits(path)

                # Stores bus stops as a string
                path_string = ""
                # Counts the number of transits
                num_transits = 0
                # Stores all the service changes
                transit_service = ""
                # Stores the ID in the path of the transit stop
                transit_stop_id = 0

                for i in range(len(path_with_transits)):
                    bus_stop, timing, direction, service \
                        = path_with_transits[i]

                    if i != 0:
                        prev_bus_stop, prev_time, prev_direction, prev_serv \
                            = path_with_transits[i - 1]
                        if bus_stop == prev_bus_stop:
                            num_transits += 1  # Count number of transits
                            transit_service += prev_serv + service
                            transit_stop_id = i
                        else:
                            # Add the other bus stop without duplicates
                            path_string += bus_stop
                    else:
                        path_string += bus_stop  # Add the source bus stop

                # Filter the paths again only taking the least transits paths
                if path_string in unique_path:
                    # Found a route with less transits
                    if num_transits < unique_path[
                            path_string]["NoofTransits"]:
                        # Save as new path
                        unique_path[path_string]["Path"].clear()
                        unique_path[path_string]["NoofTransits"] = num_transits
                        unique_path[path_string]["Path"].append(
                            (path_with_transits,
                             transit_service,
                             transit_stop_id))

                    # Found another path with same number of transits
                    elif num_transits == unique_path[
                            path_string]["NoofTransits"]:
                        add = True
                        # Ensure same path with the exact service is skipped
                        if (path_with_transits,
                            transit_service,
                            transit_stop_id) \
                                in unique_path[path_string]["Path"]:
                            continue

                        # Do not include path if it is changing
                        # between the same 2 services
                        for i in range(len(unique_path[path_string]["Path"])):
                            path, ts, ts_id = unique_path[
                                path_string]["Path"][i]

                            if transit_service == ts and transit_service != "":
                                # Pick the route that stays on the same service
                                # as far as possible
                                if transit_stop_id > ts_id:
                                    unique_path[path_string]["Path"][i] = (
                                        path_with_transits,
                                        transit_service,
                                        transit_stop_id)

                                add = False
                                break

                        if add:
                            # Append newpath to list
                            unique_path[path_string]["Path"].append(
                                (path_with_transits,
                                 transit_service,
                                 transit_stop_id))

                else:
                    unique_path[path_string] = {}
                    unique_path[path_string]["NoofTransits"] = num_transits
                    # Initialise as empty list so that there could be multiple
                    # routes with the same path
                    unique_path[path_string]["Path"] = []
                    unique_path[path_string]["Path"].append(
                        (path_with_transits, transit_service, transit_stop_id))

    keys = list(unique_path.keys())
    sub_routes = []
    # Remove paths that are substrings which matches the closest bus stop for
    # source and destination
    for i in range(len(unique_path)):
        for j in range(len(unique_path)):
            if i != j \
                    and keys[i].find(keys[j]) != -1 \
                    and unique_path[keys[i]]["Path"][0] == sources[0] \
                    and unique_path[keys[i]]["Path"][-1] == dests[0]:
                sub_routes.append(keys[j])

    # Add the unique path with the least transits to path_list
    # without the sub routes
    for key in unique_path:
        if key not in sub_routes:
            for path in unique_path[key]["Path"]:
                path_list.append(path[0])

    # ASYNC WORKFLOW
    # 1. Get set of all bus stops to be queried
    # 2. Query NextBus API for all bus stops in set asynchronously into dict
    # 3. After awaiting all http queries, in loop below, all data to be
    #    queried from dict

    stops_to_query = set()
    for path in path_list:
        for i in range(len(path) - 1):
            if len(path) > 1 and (i == 0 or path[i][0] == path[i + 1][0]):
                stops_to_query.add(path[i][0])

    stops_to_query = list(stops_to_query)
    asyncio.run(get_arrival_times(stops_to_query))

    for path in path_list:
        print("Source: ", path[0][0], "Destination: ", path[len(path) - 1][0])
        print(path)

        # Stores the routing information for each path
        route = []
        # True if ETA for third subsequent bus is not needed
        eta_valid = True
        # Stores total travel time for each route
        total_time = 0
        # Stores the number of transits, -1 as source bus stop will be counted
        num_transits = -1

        for i in range(len(path)):
            bus_stop, timing, direction, service = path[i]
            # Append direction to service if applicable
            if direction != "-":
                service += " " + direction

            service_without_spaces = service.upper().replace(" ", "")
            bus_arrival_time = "-"  # To store bus arrival information

            if i == 0:  # Source bus stop
                # Add walking time to source bus stop
                walking_time = get_walking_time(
                    source_lat, source_long, float(
                        venue_dict[bus_stop][0]), float(
                        venue_dict[bus_stop][1]))
                total_time += walking_time
                print("Walking time to " + bus_stop + " is", str(walking_time))

            # If source bus stop or current bus stop name is the same as the
            # previous (transfer needed)
            if (i == 0 or bus_stop == path[i - 1][0]) \
                    and len(path) > 1:

                num_transits += 1
                # contains both arrivalTime and nextArrivalTime
                bus_timings = bus_arrival_time_dict[
                    bus_stop][service_without_spaces]
                if bus_timings["arrivalTime"] == "-":  # service not operating
                    print("Service " + service + " "
                          + "is currently not operating at " + bus_stop + "\n")
                    break  # skip this route

                else:  # service is operating
                    if total_time < int(bus_timings["arrivalTime"]):
                        bus_arrival_time = bus_timings["arrivalTime"]
                        # subtract current total time for net waiting time
                        total_time += int(bus_arrival_time) - total_time
                        print("Service " + service + " is arriving in "
                              + bus_arrival_time + " mins at " + bus_stop)

                    # cannot catch the next bus, count subsequent bus instead
                    elif bus_timings["nextArrivalTime"] != "-" \
                        and total_time < int(
                            bus_timings["nextArrivalTime"]):
                        bus_arrival_time = bus_timings["nextArrivalTime"]
                        # must subtract current total time for net waiting time
                        total_time += int(bus_arrival_time) - total_time
                        print("Missed! Subsequent service "
                              + service
                              + " is arriving in " + bus_arrival_time
                              + " mins at " + bus_stop)

                    else:
                        # cannot estimate ETA as data for third and
                        # subsequent buses are not available
                        print("Missed both next and subsequent bus! Service "
                              + service + " next bus is arriving in "
                              + bus_timings["arrivalTime"]
                              + " mins and subsequent bus is arriving in "
                              + bus_timings["nextArrivalTime"]
                              + " mins! No ETA available.\n")
                        eta_valid = False  # invalidate ETA

            elif i == len(path) - 1:  # destination bus stop
                # Add walking time to destination
                walking_time = get_walking_time(
                    float(venue_dict[bus_stop][0]),
                    float(venue_dict[bus_stop][1]),
                    dest_lat,
                    dest_long)
                total_time += walking_time
                print("Walking time to destination is", str(walking_time))

            # Store bus stop name, service, latitude, longitude, isbusstop,
            # arrivaltimings in route
            current_waypoint = {"Name": bus_stop, "Service": service,
                                "Latitude": venue_dict[bus_stop][0],
                                "Longitude": venue_dict[bus_stop][1],
                                "IsBusStop": venue_dict[bus_stop][2]}

            if bus_arrival_time == "-":
                current_waypoint["BusArrivalTime"] = "-"
                current_waypoint["BusArrivalTimeMins"] = "-"
            else:  # return arrival timings in HH:MM am/pm
                bus_arrival = get_time() + datetime.timedelta(
                    minutes=int(bus_arrival_time))
                current_waypoint["BusArrivalTime"] = bus_arrival.strftime(
                    "%-I:%M %p")
                current_waypoint["BusArrivalTimeMins"] = bus_arrival_time

            route.append(current_waypoint)

            # If last bus stop, skip adding it as we have reached destination
            if i != len(path) - 1:
                total_time += timing

            else:
                # Calculation is complete
                # i.e. did not break halfway due to service not operating
                print("Estimated travel time: " + str(total_time) + "\n")
                if eta_valid:
                    all_route_list.append((route, total_time, num_transits))
                else:
                    # since there's no ETA just initialise to a huge number
                    all_route_list.append(
                        (route, 100000 + total_time, num_transits))

    if len(all_route_list) != 0:  # If there's a route found
        # Flag to know if the user was already recommended to walk
        already_recommended_walk = False
        sorted_route_dict = {"00": []}

        for i in range(len(all_route_list)):
            route, total_time, num_transits = all_route_list[i]
            route_source = route[0]["Name"]
            route_dest = route[-1]["Name"]
            sources_index = sources.index(route_source)
            dests_index = dests.index(route_dest)

            # Index 00 means it is the closest source and destination bus stop,
            # index 22 means the furthest source and destination bus stop
            index = str(sources_index) + str(dests_index)

            if index not in sorted_route_dict:
                sorted_route_dict[index] = []  # Initialise as a list

            if len(route) == 1:
                if not already_recommended_walk:
                    # ensure that user is only recommended to walk once
                    # prioritise walk so add to "00"
                    sorted_route_dict["00"].append(all_route_list[i])
                    already_recommended_walk = True
            else:
                sorted_route_dict[index].append(all_route_list[i])

        # Join all the route to a list
        route_list = []

        for index in sorted(sorted_route_dict.keys()):
            for path in sorted_route_dict[index]:
                route, total_time, num_transits = path
                route_source = route[0]["Name"]
                route_dest = route[-1]["Name"]
                sources_index = sources.index(route_source)
                dests_index = dests.index(route_dest)

                # Skip routes containing bus stops too far from user's source
                # and destination if enough routes already exist
                if (sources_dist[sources_index] > MAX_DIST
                        or dests_dist[dests_index] > MAX_DIST) \
                        and len(route_list) > 3:
                    continue

                route_list.append((path, total_time, index, num_transits))

        sorted_route_list = []
        fastest_route_time = 0

        # Sort by time then index then no of transits
        for path, total_time, index, num_transits \
                in sorted(route_list, key=lambda x: (x[1], x[2], x[3])):
            if total_time > 100000:
                total_time = total_time - 100000  # Get the static travel time

            if fastest_route_time == 0:
                # Store the fastest route time, minimum is 10 mins
                fastest_route_time = max(10, total_time)

            add_counter = 0  # To check if route should be added

            # Remove paths that goes past closest destination
            for added_path in sorted_route_list:
                # Loop through the shortest of both routes
                for i in range(min(len(added_path[0]), len(path[0]))):
                    # Check if the bus stops are the same
                    if added_path[0][i] != path[0][i]:
                        # If different, increment counter
                        add_counter += 1
                        break

            # If counter == len(sorted_route_list), no paths were matched
            if add_counter == len(sorted_route_list):
                # Add the path if within 2 times of fastest travel time
                if total_time < fastest_route_time * 2:
                    sorted_route_list.append(path)

        # Format data in JSON
        for i in range(min(len(sorted_route_list), MAX_ROUTES_TO_RETURN)):
            # modify parameter to select top x routes instead
            route, total_time, num_transits = sorted_route_list[i]
            data[i] = {}
            data[i]['Route'] = route

            if total_time > 100000:  # this is the route with no ETA
                data[i]['ETA'] = "-"
                data[i]['TravelTime'] = "-"
            else:
                eta = get_time() + datetime.timedelta(minutes=total_time)
                data[i]['ETA'] = eta.strftime("%-I:%M %p")
                data[i]['TravelTime'] = total_time

    json_data = json.dumps(data)  # create a json object
    return json_data  # return the json object


@app.route('/getarrivaltimings/<bus_stop>')
def get_arrival_timings(bus_stop):
    """Get arrival timings for services at specified bus stop.
    Access at 127.0.0.1:5000/getarrivaltimings/<bus_stop>
    """
    return_dict = {}
    asyncio.run(get_arrival_times([bus_stop]))
    for service in bus_arrival_time_dict[bus_stop]:
        for direction in directions:
            raw_direction = direction.upper().replace(" ", "")
            if raw_direction == service:
                return_dict[direction] = {}
                for arrival_times in bus_arrival_time_dict[bus_stop][service]:
                    # ensure arrival timings is not < 0 else return "-"
                    if bus_arrival_time_dict[bus_stop][service][
                            arrival_times] == "-" \
                        or int(bus_arrival_time_dict[bus_stop][service][
                            arrival_times]) < 0:
                        return_dict[direction][arrival_times] = "-"
                    else:
                        return_dict[direction][arrival_times] \
                            = bus_arrival_time_dict[bus_stop][
                                service][arrival_times]
    return return_dict


# Set time zone to Singapore Time
os.environ["TZ"] = "Asia/Singapore"
time.tzset()

# Read api url
f = open("nextbusurl.txt", "r")
api_url = f.read()
f.close()

# Check if firebase app is already initialised to prevent initialisation error
if not firebase_admin._apps:
    # Fetch the service account key JSON file contents
    cred = credentials.Certificate("firebase.json")

    # Read firebase url
    f = open("firebaseurl.txt", "r")
    firebase_url = f.read()
    f.close()

    # Initialise the app with a None auth variable, limiting the server's
    # access
    firebase_admin.initialize_app(cred, {'databaseURL': firebase_url})

# Import BusRoutes from database
ref = db.reference("/BusRoutes")  # access /BusRoutes
json_array = ref.get()  # returns array of json
for bus_route in json_array:  # loop through each item
    route_dict[bus_route] = []  # initialise as empty list
    # bus_route = A1, A2,...
    if bus_route is not None:  # ensure bus_route exists
        last_bus_stop = ""
        # loop through every bus stop in each route
        for bus_stop in json_array[bus_route]:
            if bus_stop is not None:  # ensure bus stop exists
                # bus_stop["Name"] returns name of bus stop,
                # bus_stop["Time"] returns time taken,
                # bus_stop["Direction"] returns direction i.e. (To BIZ 2)
                route_dict[bus_route].append(
                    (bus_stop["Name"], bus_stop["Time"],
                     bus_stop["Direction"]))
                last_bus_stop = bus_stop["Name"]
        bus_terminal_stop[bus_route] = last_bus_stop


# Import Venues from database
ref = db.reference("/Venues")  # access /Venues
json_array = ref.get()  # returns array of json
for venue in json_array:  # loop through each item
    if venue is not None:  # ensure venue exists
        # add latitude, longitude, isbusstop as a tuple
        venue_dict[venue["Name"]] = (
            (venue["Latitude"], venue["Longitude"], venue["IsBusStop"]))

        # Store coordinates of bus stops in bus_stop_coordinates
        if venue["IsBusStop"] == 'true':
            bus_stop_coordinates[venue["Name"]] = (
                float(venue["Latitude"]),
                float(venue["Longitude"]))

# Import BusStops from database
ref = db.reference("/BusStops")  # access /BusStops
json_array = ref.get()  # returns array of json
for bus_stop in json_array:  # loop through each item
    if bus_stop is not None:  # ensure bus stop exists
        bus_stop_dict[bus_stop["Name"]] = {}  # initialise as dictionary
        # Assign NextBusAlias
        bus_stop_dict[bus_stop["Name"]][
            "NextBusAlias"] = bus_stop["NextBusAlias"]
        # Assign Services
        bus_stop_dict[bus_stop["Name"]]["Services"] = bus_stop["Services"]

        # Initialise bus_arrival_time_dict
        bus_arrival_time_dict[bus_stop["Name"]] = {}
        bus_arrival_time_dict[bus_stop["Name"]]["QueryTime"] = get_time() \
            - datetime.timedelta(minutes=1)  # initialise to 1 min ago
        for service in bus_stop["Services"]:
            bus_arrival_time_dict[bus_stop["Name"]][service.upper().replace(
                " ", "")] = {"arrivalTime": "-1", "nextArrivalTime": "-1"}

            # To get all possible directions
            if service not in directions:
                directions.append(service)

# Import BusOperatingHours from database
ref = db.reference("/BusOperatingHours")  # access /BusOperatingHours
json_array = ref.get()  # returns array of json
for service in json_array:  # loop through each item
    if service is not None:  # ensure bus service exists
        bus_operating_hours_dict[service] = {}  # initialise as dict
        for day in json_array[service]:  # to get the service operating hours
            bus_operating_hours_dict[service][day] = {}  # initialise as dict
            # to get the start and end timings
            for start_end in json_array[service][day]:
                # store to bus_operating_hours_dict
                bus_operating_hours_dict[service][day][
                    start_end] = json_array[service][day][start_end]

if __name__ == "__main__":
    app.run(host="0.0.0.0")
