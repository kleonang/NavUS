# install dependencies first
# pip3 install firebase-admin
# pip3 install Flask

import firebase_admin
from firebase_admin import credentials
from firebase_admin import db
import json, requests
import math
import heapq
from flask import Flask
app = Flask(__name__)

routedict={} # Stores bus route as key and value as list of pairs
venuedict={} # Stores all venue names including Bus Stops as key and latitude, longitude as pairs
busstopdict={} # Stores all the bus stop name, NextBusAlias and bus services

# Check if firebase app is already initialised to prevent initialisation error
if not firebase_admin._apps:
    # Fetch the service account key JSON file contents
    cred = credentials.Certificate("firebase.json")

    # Read firebaseurl
    f=open("firebaseurl.txt", "r")
    firebaseurl = f.read()
    f.close()
    
    # Initialize the app with a None auth variable, limiting the server's access
    firebase_admin.initialize_app(cred, {
        'databaseURL': firebaseurl
    })

ref = db.reference("/BusRoutes") # access /BusRoutes
json_array = ref.get() # returns array of json
for busroute in json_array: # loop through each item
	routedict[busroute]=[] # initalise as empty list | busroute = A1, A2,...
	if busroute is not None: # ensure busroute exists
		for busstop in json_array[busroute]: # loop through every busstop in each route
			if busstop is not None: # ensure busstop exists
				routedict[busroute].append((busstop["Name"], busstop["Time"])) # busstop["Name"] returns name of bustop, busstop["Time"] returns time taken

ref = db.reference("/Venues") # access /Venues
json_array = ref.get() # returns array of json
for venue in json_array: # loop through each item
	if venue is not None: # ensure venue exists
		venuedict[venue["Name"]] = ((venue["Latitude"], venue["Longitude"], venue["IsBusStop"])) # add latitude, longitude, isbusstop as a tuple
		# if busstops is needed, if venue["IsBusStop"]=="true": add to busstop list

ref = db.reference("/BusStops") # access /BusStops
json_array = ref.get() # returns array of json
for busstop in json_array: # loop through each item
	if busstop is not None: # ensure busstop exists
		busstopdict[busstop["Name"]] = {} # initalise as dictionary
		busstopdict[busstop["Name"]]["NextBusAlias"] = busstop["NextBusAlias"] # assign NextBusAlias
		busstopdict[busstop["Name"]]["Services"] = busstop["Services"] # assign Services

# =============================================================================
# REALTIME DATA
#
# #function to get arrival time of buses
# def getarrivaltime(busstop):
# 	reply={} #empty dict to store data to be returned
# 	response = requests.get("https://better-nextbus.appspot.com/ShuttleService?busstopname=" + nextbusdict[busstop]) #make a request to the url
# 	if response.status_code == 200: #request successful
# 		data = json.loads(response.text)
# 		for busroute in data["ShuttleServiceResult"]["shuttles"]:
# 			reply[busroute["name"]] = busroute["arrivalTime"]
# 	return reply
# 
# #Example on how to use the function getarrivaltime
# reply = getarrivaltime("COM 2") 
# print("Arrival Timing at COM 2 | A1:", reply["A1"], " A2", reply["A2"], " D1(To BIZ2):", reply["D1(To BIZ2)"], " D1(To UTown)", reply["D1(To UTown)"])
# =============================================================================

# Node class to store bus stops
class Node:
    def __init__(self, name, svc):
        self.name = name
        self.adjacent = {}
        self.service = svc
        self.dist = math.inf
        self.visited = False
        self.prev = None
    
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

    def set_prev(self, prev):
        self.prev = prev
    
    def visit(self):
        self.visited = True
    
    def __eq__(self, other):
        if isinstance(other, Node):
            return other.name == self.name and other.service == self.service
        return False
    
    def __hash__(self):
        return hash((self.name, self.service))
    
    def __lt__(self, other):
        if self.dist == other.dist:
            return self.service == other.service
        else:
            return self.dist < other.dist
    
    def __str__(self):
        return "( " + str(self.name) + ", " + str(self.service) + " ) is adjacent to: " \
            + str([(x.name, x.service) for x in self.adjacent])

# Graph class represents the map of bus stops
# In this graph, bus stops are vertices and directed edges go from one vertex
# to another if a bus route travels between their respective bus stops 
class Graph:
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
    
    def add_directed_edge(self, frm, to, cost, svc):
        self.nodes[(frm, svc)].add_neighbour(self.nodes[(to, svc)], cost)
    
    def add_undirected_edge(self, frm, to, cost, frm_svc, to_svc):
        self.nodes[(frm, frm_svc)].add_neighbour(self.nodes[(to, to_svc)], cost)
        self.nodes[(to, to_svc)].add_neighbour(self.nodes[(frm, frm_svc)], cost)
        
    def get_nodes(self):
        return self.nodes.keys()

    def set_prev(self, curr):
        self.previous = curr

    def get_prev(self):
        return self.previous

# Traces back the shortest path from the given Node to the start Node
def update_shortest_path(node, path):
    if node.prev:
        path.append((node.prev.get_name(), node.prev.get_service()))
        update_shortest_path(node.prev, path)
    return

# Dijkstra's Algorithm
# The argument source is a Node object, dest_name is a string representing the destination
def dijkstra(graph, source, dest_name):
    # Set the distance for the start node to zero 
    source.set_dist(0)
    # Put start node into the priority queue
    pq = [source]
    heapq.heapify(pq)

    while len(pq):
        # Pop the Node with smallest dist from the priority queue
        current = heapq.heappop(pq)
        current.visit()
        # Stop if destination is reached
        if current.get_name() == dest_name:
            return current
        # Iterate through neighbours of current Node
        for nxt in current.adjacent:
            # If next Node is visited, skip it
            if nxt.visited:
                continue
            # Else relax next Node
            new_dist = current.get_dist() + current.get_weight(nxt)
            if nxt.get_dist() > new_dist:
                nxt.set_dist(new_dist)
                nxt.set_prev(current)
                # Insert nxt into priority queue
                heapq.heappush(pq, nxt)

@app.route('/getpath/<source>/<destination>') # access at 127.0.0.1:5000/getpath/your_source/your_destination
def getpath(source, destination):
    # Construct Graph
    graph = Graph()
    # Add vertices to graph
    for busroute in routedict:
        busroute_len = len(routedict[busroute])
        for i in range(busroute_len):
            graph.add_node(routedict[busroute][i][0], busroute)
    # Add directed edges to graph
    for busroute in routedict:
        busroute_len = len(routedict[busroute])
        for i in range(busroute_len):
            if routedict[busroute][i][1] != 0:
                    graph.add_directed_edge(routedict[busroute][i][0], routedict[busroute][i+1][0], 
                                            routedict[busroute][i][1], busroute)
    # Add undirected edges to graph
    for busstop in busstopdict:
        busservices = busstopdict[busstop]["Services"]
        services_len = len(busservices)
        for i in range(services_len):
            for j in range(i + 1, services_len):
                graph.add_undirected_edge(busstop, busstop, 0, busservices[i], busservices[j])
    
    # Print graph statistics
    num_e = 0
    print('Graph data:')
    for v in graph:
        for w in v.get_neighbours():
            num_e += 1
    print("Number of vertices: " + str(graph.num_vertices))
    print("Number of edges: " + str(num_e) + "\n")
    
    # Call Dijkstra's Algorithm
    first_service = busstopdict[source]["Services"][0]
    src = graph.get_node(source, first_service)
    dest = dijkstra(graph, src, destination)
    path = [(dest.get_name(), dest.get_service())]
    update_shortest_path(dest, path)
    path.reverse()
    # Handle unnecessary changes at source node
    while path[0][0] == path[1][0]:
        del path[0]
    # Handle COM2 D1 direction
    for i in range(len(path)):
        if path[i][0] == "COM 2" and path[i][1] == "D1" and i != len(path) - 1:
            if path[i+1][0] == "BIZ 2" and path[i+1][1] == "D1":
                path[i] = (path[i][0], "D1 (To BIZ 2)")
            elif path[i+1][0] == "VENTUS (OPP LT13)" and path[i+1][1] == "D1":
                path[i] = (path[i][0], "D1 (To UTown)")
    print("Source: " + source + ", Destination: " + destination + "\n")
    print("Recommended route:")
    print(path)
    print("\n" + "Total route time: " + str(dest.get_dist()) + " mins\n")
    
    data = {}

    waypointslist = [] # list to store all waypoints (busstops)

    for pair in path:
        busstop = pair[0]    	
        currentwaypoint = {}
        currentwaypoint["Name"] = busstop
        currentwaypoint["Service"] = pair[1]
        currentwaypoint["Latitude"] = venuedict[busstop][0]
        currentwaypoint["Longitude"] = venuedict[busstop][1]
        currentwaypoint["IsBusStop"] = venuedict[busstop][2]
        waypointslist.append(currentwaypoint) # add the waypoints to the list
        
    data['Waypoints'] = waypointslist
    json_data = json.dumps(data) # create a json object
    return json_data # return the json object

# FOR OFFLINE TESTING
# getpath("KENT RIDGE MRT (KR MRT)", "COM 2")

if __name__ == '__main__':
	app.run(host="0.0.0.0")