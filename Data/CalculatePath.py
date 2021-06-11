# Install dependencies first
# pip3 install firebase-admin
# pip3 install Flask

import firebase_admin
from firebase_admin import credentials
from firebase_admin import db
import json, requests
import math
import heapq
import datetime
from flask import Flask
app = Flask(__name__)

routedict={} # Stores bus route as key and value as list of pairs
venuedict={} # Stores all venue names including Bus Stops as key and latitude, longitude as pairs
busstopcoordinates={} # Stores coordinates of all bus stops
busstopdict={} # Stores all the bus stop name, NextBusAlias and bus services
busarrivaltimedict = {} #acts as a cache for each request, i.e. it will be cleared after every request

#read apiurl
f = open("nextbusurl.txt", "r")
apiurl = f.read()
f.close()

# Check if firebase app is already initialised to prevent initialisation error
if not firebase_admin._apps:
	# Fetch the service account key JSON file contents
	cred = credentials.Certificate("firebase.json")

	# Read firebaseurl
	f = open("firebaseurl.txt", "r")
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

# Store coordinates of bus stops in busstopcoordinates
for venue in venuedict:
	if venuedict[venue][2] == 'true':
		busstopcoordinates[venue] = (float(venuedict[venue][0]), float(venuedict[venue][1]))

# Delimiter to append id to repeated nodes
delimiter = "_"

# Function to modify names with given id
def append_id(tup, id_num):
	name = tup[0]
	time = tup[1]
	return (name + delimiter + str(id_num), time)

stops_with_repeated_nodes = [("COM 2", "D1"), ("KENT RIDGE BUS TERMINAL (KR BUS TERMINAL)", "C"), \
							 ("PRINCE GEORGE'S PARK (PGP)", "A1"), ("UNIVERSITY TOWN (UTOWN)", "C")]

# Split repeated nodes
# A1: PGP
routedict["A1"][0] = append_id(routedict["A1"][0], 0)
routedict["A1"][-1] = append_id(routedict["A1"][-1], 1)
# C: KR Bus Terminal
routedict["C"][0] = append_id(routedict["C"][0], 0)
routedict["C"][-1] = append_id(routedict["C"][-1], 1)
# C: UTown
routedict["C"][4] = append_id(routedict["C"][4], 0)
routedict["C"][11] = append_id(routedict["C"][11], 1)
# D1: COM 2
routedict["D1"][2] = append_id(routedict["D1"][2], 0)
routedict["D1"][12] = append_id(routedict["D1"][12], 1)

# REALTIME DATA
#function to get arrival time of buses
def getarrivaltime(busstop, service):
	for i in range(3): #retry 3 times
		try:
			if busstop not in busarrivaltimedict: #not cached, so query
				busarrivaltimedict[busstop] = {} #initialise as empty dict
				response = requests.get(apiurl + busstopdict[busstop]["NextBusAlias"], timeout=10) #make a request to the url
				if response.status_code == 200: #request successful
					data = json.loads(response.text)
					for busroute in data["ShuttleServiceResult"]["shuttles"]:
						#handle Arr case
						if busroute["arrivalTime"] == "Arr":
							busroute["arrivalTime"] = "1"
						if busroute["nextArrivalTime"] == "Arr":
							busroute["nextArrivalTime"] = "1"

						busservice = busroute["name"].upper().replace(" ", "")#to capitalise D1 (To BIZ 2) etc and remove spaces
						busarrivaltimedict[busstop][busservice] = {"arrivalTime": busroute["arrivalTime"], "nextArrivalTime": busroute["nextArrivalTime"]}
			return busarrivaltimedict[busstop][service]
		except:
			print("Error getting bus arrival timings!")
	#return as - to prevent crash
	return {"arrivalTime": "-", "nextArrivalTime": "-"}

# Node class to store bus stops
class Node:
	def __init__(self, name, svc):
		self.name = name
		self.adjacent = {}
		self.service = svc
		self.dist = math.inf
		self.edgecount = 0
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
	
	def add_directed_edge(self, frm, to, cost, frm_svc, to_svc):
		self.nodes[(frm, frm_svc)].add_neighbour(self.nodes[(to, to_svc)], cost)
	
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

def trace_path(node):
	# Reconstruct path
	path = [(node.get_name(), node.get_service())]
	update_shortest_path(node, path)
	path.reverse()

	# Handle unnecessary changes at source node
	while len(path) > 1 and path[0][0].split(delimiter)[0] == path[1][0].split(delimiter)[0]:
		del path[0]
	
	# Handle unnecessary changes at end node
	while len(path) > 1 and path[-1][0].split(delimiter)[0] == path[-2][0].split(delimiter)[0]:
		del path[-1]
	
	# Manual handling of directions at COM2 and UTown
	for i in range(len(path)):
		name_split = path[i][0].split(delimiter)
		service = path[i][1]
		# Handle COM2 D1 direction
		if service == "D1" and name_split[0] == "COM 2":
			path[i] = ("COM 2", "D1 (To UTown)") if name_split[1] == 0 else ("COM 2", "D1 (To BIZ 2)")
		# Handle UTown C direction
		elif service == "C" and name_split[0] == "UNIVERSITY TOWN (UTOWN)":
			path[i] = ("UNIVERSITY TOWN (UTOWN)", "C (To FOS)") if name_split[1] == 0 else ("UNIVERSITY TOWN (UTOWN)", "C (To KRT)")
		else:
			path[i] = (name_split[0], service)
	
	return path

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
		if current.get_name().split(delimiter)[0] == dest_name:
			return current
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


# Calculates euclidean distance between two points
def euclidean_distance(thislat, thislong, otherlat, otherlong):
	return math.sqrt(math.pow(thislat - otherlat, 2) + math.pow(thislong - otherlong, 2))


# DECLARE NUMBER OF SOURCE-DESTINATION COMBINATIONS
numsources = 3
numdests = 3

# Stores all possible routes
pathlist = []

# COMMENT OUT NEXT LINE FOR OFFLINE TESTING
@app.route('/getpath/<source>/<destination>') # access at 127.0.0.1:5000/getpath/your_source/your_destination
def getpath(source, destination):
	slat = busstopcoordinates[source][0]
	slong = busstopcoordinates[source][1]
	dlat = busstopcoordinates[destination][0]
	dlong = busstopcoordinates[destination][1]
	return getpathusingcoordinates(slat, slong, dlat, dlong)

# COMMENT OUT NEXT LINE FOR OFFLINE TESTING
@app.route('/getpath/<parameter1>/<parameter2>/<parameter3>') 
def getpathusingonecoordinate(parameter1, parameter2, parameter3):
	try:
		float(parameter1) #source was sent as coordinates
		slat = parameter1
		slong = parameter2
		dlat = busstopcoordinates[parameter3][0]
		dlong = busstopcoordinates[parameter3][1]

	except ValueError: #destination was sent as coordinates
		slat = busstopcoordinates[parameter1][0]
		slong = busstopcoordinates[parameter1][1]
		dlat = parameter2
		dlong = parameter3

	return getpathusingcoordinates(slat, slong, dlat, dlong)

# LATITUDE AND LONGITUDE VERSION (3 nearest bus stops to given lat/long)
# COMMENT OUT NEXT LINE FOR OFFLINE TESTING
@app.route('/getpath/<sourcelat>/<sourcelong>/<destlat>/<destlong>') # access at 127.0.0.1:5000/getpath/sourcelat/sourcelong/destlat/destlong
def getpathusingcoordinates(sourcelat, sourcelong, destlat, destlong):
	sourcelat = float(sourcelat)
	sourcelong = float(sourcelong)
	destlat = float(destlat)
	destlong = float(destlong)
	
	sources = []
	dests = []
	sources_pq = []
	heapq.heapify(sources_pq)
	dests_pq = []
	heapq.heapify(dests_pq)
	
	for stop in busstopcoordinates:
		dist_to_source = euclidean_distance(sourcelat, sourcelong, busstopcoordinates[stop][0], busstopcoordinates[stop][1])
		heapq.heappush(sources_pq, (dist_to_source, stop))
		dist_to_dest = euclidean_distance(destlat, destlong, busstopcoordinates[stop][0], busstopcoordinates[stop][1])
		heapq.heappush(dests_pq, (dist_to_dest, stop))
	
	for i in range(numsources):
		s = heapq.heappop(sources_pq)
		sources.append(s[1])
	for j in range(numdests):
		d = heapq.heappop(dests_pq)
		dests.append(d[1])
	
	for destination in dests:
		for source in sources:
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
													routedict[busroute][i][1], busroute, busroute)
			# Add undirected edges to graph
			for busstop in busstopdict:
				busstoptuples = []
				rawbusservices = busstopdict[busstop]["Services"] # may include repeated service for 2 directions
				for i in range(len(rawbusservices)):
					rawbusservices[i] = rawbusservices[i].split(" ")[0]
				busservices = list(set(rawbusservices)) # only unique services at each bus stop
				for service in busservices:
					if (busstop, service) in stops_with_repeated_nodes:
						for i in range(2):
							busstoptuples.append((busstop + delimiter + str(i), service))
					else:
						busstoptuples.append((busstop, service))
				services_len = len(busstoptuples)
				for i in range(services_len):
					for j in range(i + 1, services_len):
						graph.add_undirected_edge(busstoptuples[i][0], busstoptuples[j][0], \
												  0, busstoptuples[i][1], busstoptuples[j][1])
			
		# =============================================================================
		#     # Add source supernode
		#     graph.add_node(source, "START")
		#     start_arr_times = getarrivaltime(source)
		#     for service in start_arr_times:
		#         timing = start_arr_times[service]
		#         if timing == "Arr":
		#             timing = 0 
		#         elif timing == "-" or int(timing) < 0:
		#             timing = math.inf
		#         else:
		#             timing = int(timing)
		#             
		#         if service == "D1(To UTown)":
		#             graph.add_directed_edge(source, source + delimiter + "0", timing, "START", "D1")
		#         elif service == "D1(To BIZ2)":
		#             graph.add_directed_edge(source, source + delimiter + "1", timing, "START", "D1")
		#         elif service == "C(To FOS)":
		#             graph.add_directed_edge(source, source + delimiter + "0", timing, "START", "C")
		#         elif service == "C(To KRT)":
		#             graph.add_directed_edge(source, source + delimiter + "1", timing, "START", "C")
		#         elif (source == "PRINCE GEORGE'S PARK (PGP)" and service == "A1") or \
		#              (source == "KENT RIDGE BUS TERMINAL (KR BUS TERMINAL)" and service == "C"):
		#             graph.add_directed_edge(source, source + delimiter + "0", timing, "START", service)
		#         else:
		#             graph.add_directed_edge(source, source, timing, "START", service)
		# =============================================================================
				
# =============================================================================
#             # Print graph statistics
#             num_v = 0
#             num_e = 0
#             print('Graph data:')
#             for v in graph:
#                 num_v += 1
#                 for w in v.get_neighbours():
#                     num_e += 1
#             #print("Graph vertex count: " + str(graph.num_vertices))
#             print("Number of vertices: " + str(num_v))
#             print("Number of edges: " + str(num_e) + "\n")
# =============================================================================
			
			# Call Dijkstra's Algorithm
			first_service = busstopdict[source]["Services"][0]
			src = graph.get_node(source, first_service)
			dest = dijkstra(graph, src, destination)
			if dest == None:
				print("There is no valid route at the current time.")
				return
			else:
				pathdict = {}
				path = trace_path(dest)
				pathdict["Source"] = source
				pathdict["Destination"] = destination
				pathdict["Route"] = path
				pathdict["TravelTime"] = dest.get_dist()
				pathlist.append(pathdict)
	
	allroutelist = [] #to store the all route information, and total travel time in a tuple

	for path in pathlist:
		print("Source: " + path["Source"] + ", Destination: " + path["Destination"])
		print("Recommended route:")
		print(path["Route"])
		print("Total route time: " + str(path["TravelTime"]) + " mins\n")

		route = [] #to store the routing information for each path
		addtraveltime = False #flag for special handling when calculating travel time
		etavalid = True #to know if eta is valid as sometimes ETA for third subsequent bus is not available
		totaltime = 0 #to store total travel time for each route
		rawpath = path["Route"]
		
		for i in range(len(rawpath)): #iterate through the bus stops of every path
			busstop, service = rawpath[i]
			rawservice = service.split(" ")[0] #service.split as we only want D1 and remove (TO BIZ2)
			servicewithoutspaces = service.upper().replace(" ", "") #to capitalise D1 (To BIZ 2) etc and remove spaces
			busarrivaltime = "-" #to store bus arrival information

			if i == 0: #source bus stop, query arrival time
				bustimings = getarrivaltime(busstop, servicewithoutspaces)

				busarrivaltime = bustimings["arrivalTime"]

				if busarrivaltime == "-" or int(busarrivaltime) < 0: #service is not operating
					print("Service " + service + " is currently not operating at " + busstop + "\n")
					break #skip this route
				else:
					totaltime += int(busarrivaltime) #add bus waiting time at source bus stop
					print("Service " + service + " is arriving in " + busarrivaltime + " mins at "+ busstop)

			elif busstop==rawpath[i-1][0]: #else if current busstop name is the same as the previous, transfer needed
				bustimings = getarrivaltime(busstop, servicewithoutspaces) #contains both arrivalTime and nextArrivalTime
				if bustimings["arrivalTime"] == "-" or int(bustimings["arrivalTime"]) < 0: #service is not operating
					print("Service " + service + " is currently not operating at " + busstop + "\n")
					break #skip this route
				else:
					if totaltime < int(bustimings["arrivalTime"]):
						busarrivaltime = bustimings["arrivalTime"]
						totaltime += int(busarrivaltime) - totaltime #must subtract current total time for net waiting time
						print("Service " + service + " is arriving in " + busarrivaltime + " mins at "+ busstop)

					elif totaltime < int(bustimings["nextArrivalTime"]): #cannot make it for the next bus, count subsequent bus instead
						busarrivaltime = bustimings["nextArrivalTime"]
						totaltime += int(busarrivaltime) - totaltime #must subtract current total time for net waiting time
						print("Missed! Subsequent service " + service + " is arriving in " + busarrivaltime + " mins at "+ busstop)

					else: #cannot estimate ETA as data for third subsequent bus is not available
						print("Missed both next and subsequent bus! No ETA available.\n")
						etavalid = False #invalidate ETA

			#store bus stop name, service, latitude, longitude, isbusstop, arrivaltimings in route
			currentwaypoint = {}
			currentwaypoint["Name"] = busstop
			currentwaypoint["Service"] = service
			currentwaypoint["Latitude"] = venuedict[busstop][0]
			currentwaypoint["Longitude"] = venuedict[busstop][1]
			currentwaypoint["IsBusStop"] = venuedict[busstop][2]
			if i==0 or busarrivaltime=="-": #source bus stop return in mins/no transit bus stop
				currentwaypoint["BusArrivalTime"] = busarrivaltime
			else: #return transit stops in HH:MM am/pm
				busarrival = datetime.datetime.now() + datetime.timedelta(minutes = int(busarrivaltime))
				currentwaypoint["BusArrivalTime"] = busarrival.strftime("%-I:%M %p")

			route.append(currentwaypoint)

			if i!=len(rawpath)-1: #if last bus stop, need to skip adding the last busstop as we are at destination already
				if busstop!=rawpath[i-1][0] or addtraveltime: #if we are at transit bus stop do not add duplicate unless the transit stop is the last stop of a route
					for bs, time in routedict[rawservice]: #to get the travel time from one bus stop to another
						if bs == busstop:#found the busstop
							print(busstop, time)
							if int(time) == 0: #special handling as end of bus route so need to still add travel time from last bus stop in route to next bus stop
								addtraveltime = True
							else:
								totaltime += int(time)
								addtraveltime = False
							break

			else: #means the calculation is complete, i.e. did not break halfway due to service not operating
				print("Estimated travel time: " + str(totaltime) + "\n")
				if etavalid:
					allroutelist.append((route, totaltime))
				else:
					allroutelist.append((route, math.inf)) #since there's no ETA just initialise to infinity


    
	data = {} #initialise as empty
	data['Route'] = []
	data['ETA'] = ""
	if len(allroutelist) != 0: #if there's a route found
		besttimetaken = math.inf #initialise to infinity
		bestroute = [] #initialise as a empty list

		for route, totaltime in allroutelist:
			if totaltime < besttimetaken:
				besttimetaken = totaltime
				bestroute = route

		# Store best route in JSON format
		data['Route'] = bestroute

		if math.isinf(besttimetaken): #this is the route with no ETA
			data['ETA'] = "-"
		else:
			eta = datetime.datetime.now() + datetime.timedelta(minutes = besttimetaken)
			data['ETA'] = eta.strftime("%-I:%M %p")

		print("The best route is:")
		print(bestroute)
	
	print("No of HTTP Requests: ", len(busarrivaltimedict))
	busarrivaltimedict.clear() #clear cache for bus arrival time
	allroutelist.clear() #clear route list for next query
	pathlist.clear() #clear path list for next query
    
	json_data = json.dumps(data) # create a json object
	return json_data # return the json object
		

# UNCOMMENT FOR OFFLINE TESTING
#getpathusingcoordinates(1.294823, 103.784387, 1.294316, 103.773756)
#getpath("KENT RIDGE MRT (KR MRT)", "COM 2")
#getpath("COM 2", "VENTUS (OPP LT13)")
#print(getarrivaltime("KENT RIDGE MRT (KR MRT)"))

# COMMENT OUT BELOW FOR OFFLINE TESTING
if __name__ == '__main__':
	app.run(host="0.0.0.0")

# Starting stops for all services
# KR Bus Terminal: B1, BTC1, C
# KR MRT: A1E
# Opp HSSML: B2, D1
# Opp TCOMS: D2
# OTH: BTC2
# PGP: A1, A2
# Ventus: A2E