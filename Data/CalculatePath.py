# INSTALL DEPENDENCIES FIRST
# pip3 install aiohttp
# pip3 install firebase-admin
# pip3 install Flask
# pip3 install holidays

import asyncio
import copy
import datetime
import heapq
import json
import math
import os
import time

import aiohttp
import firebase_admin
import holidays

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
stops_with_repeated_nodes = []  # [(Bus Stop, Service), ...]
directions = []  # [Bus Stops with attached directions]

# DECLARE NUMBER OF SOURCE-DESTINATION COMBINATIONS
num_sources = 3
num_dests = 3

# DELIMITER TO APPEND ID TO REPEATED NODES
DELIMITER = "_"


def get_walking_time(lat1, long1, lat2, long2):
    """Returns walking time in mins between 2 sets of coordinates."""
    p = math.pi / 180
    a = 0.5 - math.cos((lat2 - lat1) * p) / 2 + math.cos(lat1 * p) * \
        math.cos(lat2 * p) * (1 - math.cos((long2 - long1) * p)) / 2
    distance = 12742 * math.asin(math.sqrt(a))
    return math.ceil(distance / (4 / 60))  # assume walking speed is 4km/h


def get_time():
    """Returns the current time."""
    return datetime.datetime.now()


def check_operating_services():
    """Checks and updates the current operating services based on the
    bus operating timings in database.
    """
    operating_services = []
    now = get_time()  # time now
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
            if start_time <= now.time() and now.time() <= end_time:
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

    for i in range(3):  # retry to connect 3 times if connection fails
        try:
            async with session.get(api_url
                                   + bus_stop_dict[bus_stop]["NextBusAlias"],
                                   timeout=5) as response:
                if response.status == 200:
                    # set query time
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
                        # to capitalise D1 (To BIZ 2) etc and remove spaces
                        bus_service = bus_route[
                            "name"].upper().replace(" ", "")
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
            "arrivalTime": "-1", "nextArrivalTime": "-1"}
    return


async def get_arrival_times(stops_to_query):
    """Get live bus arrival times asynchronously"""
    async with aiohttp.ClientSession() as session:
        tasks = []
        for stop in stops_to_query:
            tasks.append(
                asyncio.ensure_future(fetch_arrival_times(session, stop)))
        await asyncio.gather(*tasks)


class Node:
    """Node class to store bus stops"""
    def __new__(cls, name, svc):
        self = super().__new__(cls)
        self.name = name
        self.service = svc
        self.adjacent = {}
        self.dist = math.inf
        self.edgecount = 0
        self.visited = False
        self.prev = None
        return self

    def __getnewargs__(self):
        return (self.name, self.service)

    def add_neighbour(self, neighbour, weight):
        self.adjacent[neighbour] = weight

    def get_neighbours(self):
        return self.adjacent.keys()

    def get_service(self):
        return self.service

    def get_name(self):
        return self.name

    def get_weight(self, neighbour):
        return self.adjacent[neighbour]

    def set_dist(self, dist):
        self.dist = dist

    def get_dist(self):
        return self.dist

    def set_edgecount(self, edgecount):
        self.edgecount = edgecount

    def get_edgecount(self):
        return self.edgecount

    def set_prev(self, prev):
        self.prev = prev

    def visit(self):
        self.visited = True

    def is_visited(self):
        return self.visited

    def __eq__(self, other):
        if isinstance(other, Node):
            return other.name == self.name and other.service == self.service
        return False

    def __hash__(self):
        return hash((self.name, self.service))

    def __lt__(self, other):
        if self.dist == other.dist:
            return self.edgecount < other.edgecount
        else:
            return self.dist < other.dist

    def __str__(self):
        return "( " + str(self.name) + ", " + str(self.service) + " ) " \
            + "is adjacent to: " \
            + str([(x.name, x.service) for x in self.adjacent])


class Graph:
    """Graph class represents the map of bus stops.
    In this graph, bus stops are vertices and directed edges go from one vertex
    to another if a bus route travels between their respective bus stops.
    """

    def __init__(self):
        self.nodes = {}
        self.num_vertices = 0

    def __iter__(self):
        return iter(self.nodes.values())

    def add_node(self, name, service):
        self.num_vertices += 1
        new_busstop = Node(name, service)
        self.nodes[(name, service)] = new_busstop
        return new_busstop

    def get_node(self, name, service):
        if (name, service) in self.nodes:
            return self.nodes[(name, service)]
        else:
            return None

    def add_directed_edge(self, frm, to, cost, frm_svc, to_svc):
        self.nodes[(frm, frm_svc)].add_neighbour(
            self.nodes[(to, to_svc)], cost)

    def add_undirected_edge(self, frm, to, cost, frm_svc, to_svc):
        self.nodes[(frm, frm_svc)].add_neighbour(
            self.nodes[(to, to_svc)], cost)
        self.nodes[(to, to_svc)].add_neighbour(
            self.nodes[(frm, frm_svc)], cost)

    def get_nodes(self):
        return self.nodes.keys()

    def set_prev(self, curr):
        self.previous = curr

    def get_prev(self):
        return self.previous

    def __eq__(self, other):
        if isinstance(other, Graph):
            return self.nodes == other.nodes \
                and self.num_vertices == other.num_vertices
        return False


def update_shortest_path(node, path):
    """Traces back the shortest path from the given Node to the start Node"""
    if node.prev:
        path.append((node.prev.get_name(), node.prev.get_service()))
        update_shortest_path(node.prev, path)
    return


def trace_path(node):
    """Traces the path from a given node using update_shortest_path
    and handles post-processing of routes.
    """
    # Reconstruct path
    path = [(node.get_name(), node.get_service())]
    update_shortest_path(node, path)
    path.reverse()

    # Handle unnecessary changes at source node
    while len(path) > 1 and path[0][0].split(DELIMITER)[
            0] == path[1][0].split(DELIMITER)[0]:
        del path[0]

    # Handle unnecessary changes at end node
    while len(path) > 1 \
            and path[-1][0].split(DELIMITER)[0] \
            == path[-2][0].split(DELIMITER)[0]:
        del path[-1]

    # Handling of directions at COM 2 and UTown
    for i in range(len(path)):
        name_split = path[i][0].split(DELIMITER)
        service = path[i][1]
        repeated_bus_stops = [(x, y, z) for x, y, z in route_dict[service]
                              if x.split(DELIMITER)[0] == name_split[0]]
        if len(repeated_bus_stops) > 1:  # there are duplicate bus stops
            # if id == 0, return the first bus stop,
            # if id == 1, return the second duplicate bus stop
            bus_stop, time, direction = repeated_bus_stops[int(name_split[1])]
            if direction != "-":  # direction is not blank
                service += " " + direction

        path[i] = (name_split[0], service)

    return path


def dijkstra(graph, source, dest_name):
    """Dijkstra's Algorithm
    Finds shortest path from source to destination.

    The argument source is a Node object,
    dest_name is a string representing the destination.
    """
    # Set the distance for the start node to zero
    source.set_dist(0)
    # Put start node into the priority queue
    pq = [source]
    heapq.heapify(pq)
    # Generate all paths ending at dest_name and append to result_list
    result_list = []
    while len(pq):
        # Pop the Node with smallest dist from the priority queue
        current = heapq.heappop(pq)
        current.visit()
        # Stop if destination is reached
        if current.get_name().split(DELIMITER)[0] == dest_name:
            route = {}
            traced_path = trace_path(current)
            route["Route"] = traced_path
            route["RouteLength"] = len(traced_path)
            route["TravelTime"] = current.get_dist()
            if route not in result_list:
                result_list.append(route)
        # Iterate through neighbours of current Node
        for nxt in current.adjacent:
            # If next Node is visited, skip it
            if nxt.is_visited():
                continue
            # Else relax next Node
            new_dist = current.get_dist() + current.get_weight(nxt)
            if nxt.get_dist() > new_dist:
                nxt.set_dist(new_dist)
                nxt.set_edgecount(current.get_edgecount() + 1)
                nxt.set_prev(current)
                if nxt not in pq:
                    # Insert nxt into priority queue
                    heapq.heappush(pq, nxt)
    if result_list == []:
        return None
    result_list = sorted(result_list, key=lambda d: d["RouteLength"])
    result_list = sorted(result_list, key=lambda d: d["TravelTime"])
    return result_list


def euclidean_distance(this_lat, this_long, other_lat, other_long):
    """Calculates euclidean distance between two points"""
    return math.sqrt(math.pow(this_lat - other_lat, 2)
                     + math.pow(this_long - other_long, 2))


def construct_master_graph(operating_services):
    """Construct the Master Graph to be copied by other functions"""
    # Construct Master Graph
    master_graph = Graph()
    # Add vertices to graph
    for bus_route in route_dict:
        if bus_route in operating_services:  # ensure bus service is operating
            bus_route_len = len(route_dict[bus_route])
            for i in range(bus_route_len):
                master_graph.add_node(route_dict[bus_route][i][0], bus_route)
    # Add directed edges to graph
    for bus_route in route_dict:
        if bus_route in operating_services:  # ensure bus service is operating
            bus_route_len = len(route_dict[bus_route])
            for i in range(bus_route_len):
                if route_dict[bus_route][i][1] != 0:
                    master_graph.add_directed_edge(
                        route_dict[bus_route][i][0],
                        route_dict[bus_route][i + 1][0],
                        route_dict[bus_route][i][1],
                        bus_route,
                        bus_route)
    # Add undirected edges to graph
    for bus_stop in bus_stop_dict:
        bus_stop_tuples = []
        # may include repeated service for 2 directions
        raw_bus_services = bus_stop_dict[bus_stop]["Services"]
        for i in range(len(raw_bus_services)):
            raw_bus_services[i] = raw_bus_services[i].split(" ")[0]
        # only unique services at each bus stop
        busservices = list(set(raw_bus_services))
        for service in busservices:
            # ensure bus service is operating
            if service in operating_services:
                if (bus_stop, service) in stops_with_repeated_nodes:
                    for i in range(2):
                        bus_stop_tuples.append(
                            (bus_stop + DELIMITER + str(i), service))
                else:
                    bus_stop_tuples.append((bus_stop, service))
        services_len = len(bus_stop_tuples)
        for i in range(services_len):
            for j in range(i + 1, services_len):
                master_graph.add_undirected_edge(
                    bus_stop_tuples[i][0],
                    bus_stop_tuples[j][0],
                    0,
                    bus_stop_tuples[i][1],
                    bus_stop_tuples[j][1])

    # Print graph statistics
    num_v = 0
    num_e = 0
    print('Graph data:')
    for v in master_graph:
        num_v += 1
        for w in v.get_neighbours():
            num_e += 1
    # print("Graph vertex count: " + str(graph.num_vertices))
    # to verify number of unique vertices
    print("Number of vertices: " + str(num_v))
    print("Number of edges: " + str(num_e) + "\n")
    return master_graph


# COMMENT OUT NEXT LINE FOR OFFLINE TESTING
@app.route('/getpath/<source>/<destination>')
def get_path(source, destination):
    """Get path from source to destination.
    Access at 127.0.0.1:5000/getpath/your_source/your_destination
    """
    source_lat = venue_dict[source][0]
    source_long = venue_dict[source][1]
    dest_lat = venue_dict[destination][0]
    dest_long = venue_dict[destination][1]
    return get_path_using_coordinates(source_lat, source_long,
                                      dest_lat, dest_long)


# COMMENT OUT NEXT LINE FOR OFFLINE TESTING
@app.route('/getpath/<parameter1>/<parameter2>/<parameter3>')
def get_path_using_one_coordinate(parameter1, parameter2, parameter3):
    """Get path from source to destination,
    where only either source or destination was passed as coordinates.
    Access at 127.0.0.1:5000/getpath/parameter1/parameter2/parameter3
    """
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


# COMMENT OUT NEXT LINE FOR OFFLINE TESTING
@app.route('/getpath/<source_lat>/<source_long>/<dest_lat>/<dest_long>')
def get_path_using_coordinates(source_lat, source_long, dest_lat, dest_long):
    """Get path from source to destination,
    where both source and destination were passed as coordinates.
    Access at 127.0.0.1:5000/getpath/source_lat/source_long/dest_lat/dest_long
    """
    # Construct graph and get operating_services
    operating_services = check_operating_services()

    data = {}  # initialise as empty dictionary for data to be returned

    master_graph = construct_master_graph(operating_services)

    if master_graph == Graph():
        return json.dumps(data)

    source_lat = float(source_lat)
    source_long = float(source_long)
    dest_lat = float(dest_lat)
    dest_long = float(dest_long)

    sources = []
    dests = []
    # Priority queue for sources based on dist from source coordinates
    sources_pq = []
    heapq.heapify(sources_pq)
    # Priority queue for destinations based on dist from dest coordinates
    dests_pq = []
    heapq.heapify(dests_pq)

    # Stores all possible routes
    path_list = []

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

    for i in range(num_sources):
        s = heapq.heappop(sources_pq)
        sources.append(s[1])
    for j in range(num_dests):
        d = heapq.heappop(dests_pq)
        dests.append(d[1])

    for destination in dests:
        for source in sources:
            graph = copy.deepcopy(master_graph)
            # Get first available service at source bus stop
            # may include repeated service for 2 directions
            raw_bus_services = bus_stop_dict[source]["Services"]
            for i in range(len(raw_bus_services)):
                raw_bus_services[i] = raw_bus_services[i].split(" ")[0]
            # only unique services at each bus stop
            all_services = set(raw_bus_services)
            available_services = set(
                operating_services).intersection(all_services)
            if available_services == set():
                print("There are currently no services operating at "
                      + source + ".")
                continue

            first_service = list(available_services)[0]
            # Continue if trivial case of source and destination being the same
            if source == destination:
                p = {}
                p["Route"] = [(source, first_service)]
                p["RouteLength"] = 1
                p["TravelTime"] = 0
                p["Source"] = source
                p["Destination"] = destination
                path_list.append(p)
                continue
            src = graph.get_node(source, first_service) \
                if (source, first_service) not in stops_with_repeated_nodes \
                else graph.get_node(source + DELIMITER + "0", first_service)
            # Call Dijkstra's Algorithm
            path_dicts = dijkstra(graph, src, destination)
            if path_dicts is None:
                print("There is no valid route at the current time.")
                continue
            else:
                for p in path_dicts:
                    p["Source"] = source
                    p["Destination"] = destination
                    path_list.append(p)

    # Filtering not useful supersets and subsets of routes
    # (only works if destination is a bus stop)
    if (dest_lat, dest_long) in bus_stop_coordinates.values():
        dest_name = list(
            bus_stop_coordinates.keys())[
            list(
                bus_stop_coordinates.values()).index(
                (dest_lat, dest_long))]
        # Filter out paths in path_list where specified destination is in the
        # path and is not the last stop (supersets)
        path_list = [p for p in path_list
                     if len(
                         [wp for wp in p["Route"]
                          if wp[0] == dest_name
                          and wp != p["Route"][-1]]) == 0]
        # Filter out paths in path_list which are subsets of other paths ending
        # in the specified destination
        filtered_path_list = path_list.copy()
        for p1 in path_list:
            r1 = p1["Route"]
            for p2 in path_list:
                r2 = p2["Route"]
                if r1 != r2 and r1[0] == r2[0] and r2[-1][0] == dest_name \
                        and set(r1).issubset(set(r2)):
                    filtered_path_list.remove(p1)
        path_list = filtered_path_list
    print("Pathlist contains " + str(len(path_list)) + " paths:")
    print(path_list)

    all_route_list = []  # to store all (route information, total travel time)

    # ASYNC WORKFLOW
    # 1. Get set of all bus stops to be queried
    # 2. Query NextBus API for all bus stops in set asynchronously into dict
    # 3. After awaiting all http queries, in loop below, all data to be
    #    queried from dict

    stops_to_query = set()

    for path in path_list:
        route = path["Route"]
        for i in range(len(route) - 1):
            if len(route) > 1 and (i == 0 or route[i][0] == route[i + 1][0]):
                stops_to_query.add(route[i][0])
    stops_to_query = list(stops_to_query)
    asyncio.run(get_arrival_times(stops_to_query))

    for path in path_list:
        print("Source: " + path["Source"] + ", "
              + "Destination: " + path["Destination"])
        print("Route waypoints:")
        print(path["Route"])
        print("Total route time: " + str(path["TravelTime"]) + " mins\n")

        route = []  # Stores the routing information for each path
        # flag for special handling when calculating travel time where bus
        # service is looping i.e. for A1 PGP->KRMRT
        add_travel_time = False
        eta_valid = True  # True if ETA for third subsequent bus is not needed
        total_time = 0  # to store total travel time for each route
        raw_path = path["Route"]

        for i in range(
                len(raw_path)):  # iterate through the bus stops of every path
            bus_stop, service = raw_path[i]
            # service.split as we only want D1 and remove (TO BIZ2)
            raw_service = service.split(" ")[0]
            # to capitalise D1 (To BIZ 2) etc and remove spaces
            service_without_spaces = service.upper().replace(" ", "")
            bus_arrival_time = "-"  # to store bus arrival information

            if i == 0:  # source bus stop
                # add walking time to source bus stop
                walking_time = get_walking_time(
                    source_lat, source_long, float(
                        venue_dict[bus_stop][0]), float(
                        venue_dict[bus_stop][1]))
                total_time += walking_time
                print("Walking time to " + bus_stop + " is", str(walking_time))

            # if source bus stop or current bus stop name is the same as the
            # previous (transfer needed)
            if (i == 0 or bus_stop == raw_path[i - 1][0]) \
                    and len(raw_path) > 1:
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

            elif i == len(raw_path) - 1:  # destination bus stop
                # add walking time to destination
                walking_time = get_walking_time(
                    float(venue_dict[bus_stop][0]),
                    float(venue_dict[bus_stop][1]),
                    dest_lat,
                    dest_long)
                total_time += walking_time
                print("Walking time to destination is", str(walking_time))

            # store bus stop name, service, latitude, longitude, isbusstop,
            # arrivaltimings in route
            current_waypoint = {}
            current_waypoint["Name"] = bus_stop
            current_waypoint["Service"] = service
            current_waypoint["Latitude"] = venue_dict[bus_stop][0]
            current_waypoint["Longitude"] = venue_dict[bus_stop][1]
            current_waypoint["IsBusStop"] = venue_dict[bus_stop][2]

            if bus_arrival_time == "-":
                current_waypoint["BusArrivalTime"] = "-"
                current_waypoint["BusArrivalTimeMins"] = "-"
            else:  # return arrival timings in HH:MM am/pm
                busarrival = get_time() + datetime.timedelta(
                    minutes=int(bus_arrival_time))
                current_waypoint["BusArrivalTime"] = busarrival.strftime(
                    "%-I:%M %p")
                current_waypoint["BusArrivalTimeMins"] = bus_arrival_time

            route.append(current_waypoint)

            # If last bus stop, skip adding it as we have reached destination
            if i != len(raw_path) - 1:
                # If we are at transit bus stop do not add duplicate unless the
                # transit stop is the last stop of a route
                if bus_stop != raw_path[i - 1][0] or add_travel_time:
                    # To get the travel time from one bus stop to another
                    for bs, timing, direction in route_dict[raw_service]:
                        if bs == bus_stop:  # Found the bus stop
                            if int(timing) == 0:
                                # Special handling as end of bus route,
                                # need to add travel time from last bus stop
                                # in route to the next bus stop
                                add_travel_time = True
                            else:
                                total_time += int(timing)
                                add_travel_time = False
                            break

            else:
                # Calculation is complete
                # i.e. did not break halfway due to service not operating
                print("Estimated travel time: " + str(total_time) + "\n")
                if eta_valid:
                    all_route_list.append((route, total_time))
                else:
                    # since there's no ETA just initialise to a huge number
                    all_route_list.append((route, 100000 + total_time))

    # Sort all routes by time
    all_route_list = sorted(all_route_list, key=lambda x: x[1])

    if len(all_route_list) != 0:  # if there's a route found
        source_dest_list = []  # list for matching source and destination
        dest_list = []  # list for matching destination
        source_list = []  # list for matching source
        other_list = []  # list for does not match any
        # flag to know if the user was already recommended to walk
        already_recommended_walk = False
        bus_routes = []  # list to store all the bus routes
        shortest_travel_time = all_route_list[0][1]  # shortest travel time
        max_time_difference = 5

        for i in range(len(all_route_list)):
            route, total_time = all_route_list[i]
            route_source_lat = float(route[0]["Latitude"])
            route_source_long = float(route[0]["Longitude"])
            route_dest_lat = float(route[-1]["Latitude"])
            route_dest_long = float(route[-1]["Longitude"])
            bus_stop_list = []  # to store the bus stops for the current route

            for j in range(len(route)):
                bus_stop_name = route[j]["Name"]
                if bus_stop_name not in bus_stop_list:
                    # ensure no duplicate bus stops during transit
                    bus_stop_list.append(bus_stop_name)

            if bus_stop_list in bus_routes:  # exact same route
                continue  # skip this route

            if(len(route) == 1):
                if not already_recommended_walk:
                    # ensure that user is only recommended to walk once
                    # prioritise walk so add to source_dest_list
                    source_dest_list.append(all_route_list[i])
                    already_recommended_walk = True

            # check if route is within 5 mins and matches source and dest
            elif total_time <= shortest_travel_time + max_time_difference \
                    and route_source_lat == source_lat \
                    and route_source_long == source_long \
                    and route_dest_lat == dest_lat \
                    and route_dest_long == dest_long:
                source_dest_list.append(all_route_list[i])

            # propose the route with exact destination
            elif total_time <= shortest_travel_time + max_time_difference \
                    and route_dest_lat == dest_lat \
                    and route_dest_long == dest_long:
                dest_list.append(all_route_list[i])

            # propose the route with exact source
            elif total_time <= shortest_travel_time + max_time_difference \
                    and route_source_lat == source_lat \
                    and route_source_long == source_long:
                source_list.append(all_route_list[i])

            else:  # more than 5mins/does not match source/dest
                other_list.append(all_route_list[i])

            bus_routes.append(bus_stop_list)  # add current route to the list

        print("Shortest travel time: ", shortest_travel_time)
        # join all the lists
        sorted_route_list = [*source_dest_list, *dest_list,
                             *source_list, *other_list]

        # format data in JSON
        for i in range(min(len(sorted_route_list), 5)):
            # modify parameter to select top x routes instead
            route, total_time = sorted_route_list[i]
            data[i] = {}
            data[i]['Route'] = route

            if total_time > 100000:  # this is the route with no ETA
                data[i]['ETA'] = "-"
                data[i]['TravelTime'] = "-"
            else:
                eta = get_time() + datetime.timedelta(minutes=total_time)
                data[i]['ETA'] = eta.strftime("%-I:%M %p")
                data[i]['TravelTime'] = total_time

    all_route_list.clear()  # clear route list for next query
    path_list.clear()  # clear path list for next query

    json_data = json.dumps(data)  # create a json object
    return json_data  # return the json object


@app.route('/getarrivaltimings/<bus_stop>')
def get_arrival_timings(bus_stop):
    """Get arrival timings for services at specified bus stop.
    Access at 127.0.0.1:5000/getarrivaltimings/<bus_stop>
    """
    returndict = {}
    asyncio.run(get_arrival_times([bus_stop]))
    for service in bus_arrival_time_dict[bus_stop]:
        for direction in directions:
            rawdirection = direction.upper().replace(" ", "")
            if rawdirection == service:
                returndict[direction] = {}
                for arrivaltimes in bus_arrival_time_dict[bus_stop][service]:
                    # ensure arrival timings is not < 0 else return "-"
                    if bus_arrival_time_dict[bus_stop][service][
                            arrivaltimes] == "-" \
                        or int(bus_arrival_time_dict[bus_stop][service][
                            arrivaltimes]) < 0:
                        returndict[direction][arrivaltimes] = "-"
                    else:
                        returndict[direction][arrivaltimes] \
                            = bus_arrival_time_dict[bus_stop][
                                service][arrivaltimes]

    return returndict


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

    # Initialize the app with a None auth variable, limiting the server's
    # access
    firebase_admin.initialize_app(cred, {
        'databaseURL': firebase_url
    })

# Import BusRoutes from database
ref = db.reference("/BusRoutes")  # access /BusRoutes
json_array = ref.get()  # returns array of json
for bus_route in json_array:  # loop through each item
    route_dict[bus_route] = []  # initalise as empty list
    # bus_route = A1, A2,...
    if bus_route is not None:  # ensure bus_route exists
        # loop through every bus stop in each route
        for bus_stop in json_array[bus_route]:
            if bus_stop is not None:  # ensure bus stop exists
                num_repeat = len([(x, y, z)
                                  for x, y, z in route_dict[bus_route]
                                  if x.split(DELIMITER)[
                                      0] == bus_stop["Name"]])
                if num_repeat > 0:
                    # if bus stop already exists in the current route,
                    # append _0 to existing one
                    if num_repeat == 1:
                        for i in range(len(route_dict[bus_route])):
                            if route_dict[bus_route][i][0] == bus_stop["Name"]:
                                route_dict[bus_route][i] = (
                                    bus_stop["Name"] + DELIMITER + str(0),
                                    route_dict[bus_route][i][1],
                                    route_dict[bus_route][i][2])
                                break

                    # append _number to new one
                    route_dict[bus_route].append(
                        (bus_stop["Name"] + DELIMITER + str(num_repeat),
                         bus_stop["Time"],
                         bus_stop["Direction"]))

                    # add to stops with repeated nodes
                    if (bus_stop["Name"], bus_route) \
                            not in stops_with_repeated_nodes:
                        stops_with_repeated_nodes.append(
                            (bus_stop["Name"], bus_route))

                else:
                    # bus_stop["Name"] returns name of bustop,
                    # bus_stop["Time"] returns time taken,
                    # bus_stop["Direction"] returns direction i.e. (To BIZ 2)
                    route_dict[bus_route].append(
                        (bus_stop["Name"], bus_stop["Time"],
                         bus_stop["Direction"]))

# Import Venues from database
ref = db.reference("/Venues")  # access /Venues
json_array = ref.get()  # returns array of json
for venue in json_array:  # loop through each item
    if venue is not None:  # ensure venue exists
        # add latitude, longitude, isbusstop as a tuple
        venue_dict[venue["Name"]] = (
            (venue["Latitude"], venue["Longitude"], venue["IsBusStop"]))
        # If busstops is needed, do
        # if venue["IsBusStop"] == "true":
        # add to bus_stop list

# Import BusStops from database
ref = db.reference("/BusStops")  # access /BusStops
json_array = ref.get()  # returns array of json
for bus_stop in json_array:  # loop through each item
    if bus_stop is not None:  # ensure bus stop exists
        bus_stop_dict[bus_stop["Name"]] = {}  # initalise as dictionary
        # assign NextBusAlias
        bus_stop_dict[bus_stop["Name"]][
            "NextBusAlias"] = bus_stop["NextBusAlias"]
        # assign Services
        bus_stop_dict[bus_stop["Name"]]["Services"] = bus_stop["Services"]

        # initialise bus_arrival_time_dict
        bus_arrival_time_dict[bus_stop["Name"]] = {}
        bus_arrival_time_dict[bus_stop["Name"]]["QueryTime"] = get_time() \
            - datetime.timedelta(minutes=1)  # initialise to 1 min ago
        for service in bus_stop["Services"]:
            bus_arrival_time_dict[bus_stop["Name"]][service.upper().replace(
                " ", "")] = {"arrivalTime": "-1", "nextArrivalTime": "-1"}

            # to get all possible directions
            if service not in directions:
                directions.append(service)

# Import BusOperatingHours from database
ref = db.reference("/BusOperatingHours")  # access /BusOperatingHours
json_array = ref.get()  # returns array of json
for service in json_array:  # loop through each item
    if service is not None:  # ensure bus service exists
        bus_operating_hours_dict[service] = {}  # initalise as dict
        for day in json_array[service]:  # to get the service operating hours
            bus_operating_hours_dict[service][day] = {}  # initalise as dict
            # to get the start and end timings
            for start_end in json_array[service][day]:
                # store to bus_operating_hours_dict
                bus_operating_hours_dict[service][day][
                    start_end] = json_array[service][day][start_end]

# Store coordinates of bus stops in bus_stop_coordinates
for venue in venue_dict:
    if venue_dict[venue][2] == 'true':
        bus_stop_coordinates[venue] = (
            float(venue_dict[venue][0]),
            float(venue_dict[venue][1]))

if __name__ == "__main__":
    app.run(host="0.0.0.0")
