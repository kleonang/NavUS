# Install dependencies first
# pip3 install firebase-admin
# pip3 install Flask
# pip3 install holidays
# pip3 install requests

import firebase_admin
from firebase_admin import credentials
from firebase_admin import db
import json, requests
import math
import heapq
import datetime
import holidays
import copy
import os
import time
from time import sleep
from flask import Flask
app = Flask(__name__)

os.environ["TZ"] = "Asia/Singapore"
time.tzset()

def getwalkingtime(lat1, lon1, lat2, lon2):
    p = math.pi/180
    a = 0.5 - math.cos((lat2-lat1)*p)/2 + math.cos(lat1*p) * math.cos(lat2*p) * (1-math.cos((lon2-lon1)*p))/2
    distance = 12742 * math.asin(math.sqrt(a))
    return math.ceil(distance/(4/60)) #assume walking speed is 4km/h

def gettime():
	return datetime.datetime.now()

def checkoperatingservices():
	operatingservices = []
	now = gettime() #time now
	daynumber = now.weekday()
	daytocheck = ""

	if holidays.Singapore().get(now.strftime('%Y-%m-%d')) is not None or daynumber==6: #public holiday/Sunday
		daytocheck = "SundaysandPH"
	elif daynumber == 5: #saturday
		daytocheck = "Saturdays"
	else: #weekday
		daytocheck = "Weekdays"

	for service in busoperatinghoursdict.keys():
		start = busoperatinghoursdict[service][daytocheck]["Start"]
		end = busoperatinghoursdict[service][daytocheck]["End"]

		if not start=="-" and not end == "-": #ensure start and end are not null
			starttime = datetime.time(hour=int(start[0:2]), minute=int(start[2:4]))
			endtime = datetime.time(hour=int(end[0:2]), minute=int(end[2:4]))
			if starttime <= now.time() and now.time() <= endtime: #service is operating
				operatingservices.append(service)
	return operatingservices


# REALTIME DATA
#function to get arrival time of buses
def getarrivaltime(busstop, service):
	for i in range(3): #retry 3 times
		try:
			if busstop in busarrivaltimedict: #cached, so check query time
				if gettime() - busarrivaltimedict[busstop]["QueryTime"] < datetime.timedelta(minutes=1): #less than 1 min
					return busarrivaltimedict[busstop][service]

			busarrivaltimedict[busstop] = {} #initialise as empty dict
			busarrivaltimedict[busstop]["QueryTime"] = gettime() #set query time
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
		except Exception as e:
			print("Error getting bus arrival timings at " + busstop + ", service " + service + ". Exception is " + str(e))
			sleep(1) #to delay retry

	#return as - to prevent crash
	return {"arrivalTime": "-", "nextArrivalTime": "-"}

# Node class to store bus stops
class Node:
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

	def __eq__(self, other):
		if isinstance(other, Graph):
			return self.nodes == other.nodes and self.num_vertices == other.num_vertices
		return False

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
		repeatedbusstops = [ (x,y,z) for x, y, z in routedict[service] if x.split(delimiter)[0]  == name_split[0] ]
		if len(repeatedbusstops) > 1: #there are duplicate bus stops
			busstop, time, direction = repeatedbusstops[int(name_split[1])] #if id=0, return the first bus stop, if id=1, return the second duplicate bus stop
			if direction != "-": #direction is not blank
				service += " " + direction

		path[i] = (name_split[0], service)

	return path

# Dijkstra's Algorithm
# The argument source is a Node object, dest_name is a string representing the destination
def dijkstra(graph, source, dest_name):
	#print("Source: " + source.get_name())
	#print("Dest: " + dest_name)
	# Set the distance for the start node to zero
	source.set_dist(0)
	# Put start node into the priority queue
	pq = [source]
	heapq.heapify(pq)
	# Generate all paths ending at dest_name and append to resultlist
	resultlist = []
	while len(pq):
		# Pop the Node with smallest dist from the priority queue
		current = heapq.heappop(pq)
		current.visit()
		# Stop if destination is reached
		if current.get_name().split(delimiter)[0] == dest_name:
			route = {}
			traced_path = trace_path(current)
			route["Route"] = traced_path
			route["RouteLength"] = len(traced_path)
			route["TravelTime"] = current.get_dist()
			if route not in resultlist:
				resultlist.append(route)
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
	if resultlist == []:
		return None
	resultlist = sorted(resultlist, key=lambda d: d["RouteLength"])
	resultlist = sorted(resultlist, key=lambda d: d["TravelTime"])
	return resultlist

# Calculates euclidean distance between two points
def euclidean_distance(thislat, thislong, otherlat, otherlong):
	return math.sqrt(math.pow(thislat - otherlat, 2) + math.pow(thislong - otherlong, 2))


# Construct the Master Graph
def constructmastergraph(operatingservices):
	# Construct Master Graph
	mastergraph = Graph()
	# Add vertices to graph
	for busroute in routedict:
		if busroute in operatingservices: # ensure bus service is operating
			busroute_len = len(routedict[busroute])
			for i in range(busroute_len):
				mastergraph.add_node(routedict[busroute][i][0], busroute)
	# Add directed edges to graph
	for busroute in routedict:
		if busroute in operatingservices: # ensure bus service is operating
			busroute_len = len(routedict[busroute])
			for i in range(busroute_len):
				if routedict[busroute][i][1] != 0:
						mastergraph.add_directed_edge(routedict[busroute][i][0], routedict[busroute][i+1][0],
													routedict[busroute][i][1], busroute, busroute)
	# Add undirected edges to graph
	for busstop in busstopdict:
		busstoptuples = []
		rawbusservices = busstopdict[busstop]["Services"] # may include repeated service for 2 directions
		for i in range(len(rawbusservices)):
			rawbusservices[i] = rawbusservices[i].split(" ")[0]
		busservices = list(set(rawbusservices)) # only unique services at each bus stop
		for service in busservices:
			if service in operatingservices: #ensure bus service is operating
				if (busstop, service) in stops_with_repeated_nodes:
					for i in range(2):
						busstoptuples.append((busstop + delimiter + str(i), service))
				else:
					busstoptuples.append((busstop, service))
		services_len = len(busstoptuples)
		for i in range(services_len):
			for j in range(i + 1, services_len):
				mastergraph.add_undirected_edge(busstoptuples[i][0], busstoptuples[j][0], \
											  0, busstoptuples[i][1], busstoptuples[j][1])

	#if mastergraph == Graph():
	#	print("No buses are currently in service.\n")
	#	return

	# Print graph statistics
	num_v = 0
	num_e = 0
	print('Graph data:')
	for v in mastergraph:
		num_v += 1
		for w in v.get_neighbours():
			num_e += 1
	#print("Graph vertex count: " + str(graph.num_vertices)) # to verify number of unique vertices
	print("Number of vertices: " + str(num_v))
	print("Number of edges: " + str(num_e) + "\n")
	return mastergraph


# COMMENT OUT NEXT LINE FOR OFFLINE TESTING
@app.route('/getpath/<source>/<destination>') # access at 127.0.0.1:5000/getpath/your_source/your_destination
def getpath(source, destination):
	slat = venuedict[source][0]
	slong = venuedict[source][1]
	dlat = venuedict[destination][0]
	dlong = venuedict[destination][1]
	return getpathusingcoordinates(slat, slong, dlat, dlong)


# COMMENT OUT NEXT LINE FOR OFFLINE TESTING
@app.route('/getpath/<parameter1>/<parameter2>/<parameter3>')
def getpathusingonecoordinate(parameter1, parameter2, parameter3):
	try:
		float(parameter1) #source was sent as coordinates
		slat = parameter1
		slong = parameter2
		dlat = venuedict[parameter3][0]
		dlong = venuedict[parameter3][1]

	except ValueError: #destination was sent as coordinates
		slat = venuedict[parameter1][0]
		slong = venuedict[parameter1][1]
		dlat = parameter2
		dlong = parameter3

	return getpathusingcoordinates(slat, slong, dlat, dlong)


# LATITUDE AND LONGITUDE VERSION (3 nearest bus stops to given lat/long)
# COMMENT OUT NEXT LINE FOR OFFLINE TESTING
@app.route('/getpath/<sourcelat>/<sourcelong>/<destlat>/<destlong>') # access at 127.0.0.1:5000/getpath/sourcelat/sourcelong/destlat/destlong
def getpathusingcoordinates(sourcelat, sourcelong, destlat, destlong):
	# Construct graph and get operatingservices
	operatingservices = checkoperatingservices()

	data = {} #initialise as empty dictionary for data to be returned
	data[0] = {}
	data[0]['Route'] = []
	data[0]['ETA'] = ""

	mastergraph = constructmastergraph(operatingservices)

	if mastergraph == Graph():
		return json.dumps(data)

	sourcelat = float(sourcelat)
	sourcelong = float(sourcelong)
	destlat = float(destlat)
	destlong = float(destlong)

	sources = []
	dests = []
	sources_pq = [] # priority queue for sources based on dist from source coordinates
	heapq.heapify(sources_pq)
	dests_pq = [] # priority queue for destinations based on dist from dest coordinates
	heapq.heapify(dests_pq)

	# Stores all possible routes
	pathlist = []

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
			graph = copy.deepcopy(mastergraph)
			# Get first available service at source bus stop
			all_services = set(busstopdict[source]["Services"])
			available_services = set(operatingservices).intersection(all_services)
			if available_services == set():
				print("There are currently no services operating at " + source + ".")
				continue
			first_service = list(available_services)[0]
			src = graph.get_node(source, first_service)
			# Call Dijkstra's Algorithm
			pathdicts = dijkstra(graph, src, destination)

			if pathdicts == None:
				print("There is no valid route at the current time.")
				continue
			else:
				for p in pathdicts:
					p["Source"] = source
					p["Destination"] = destination
					pathlist.append(p)

	# Filter out paths in pathlist where specified destination is in the path and is not the last stop
	# (only works if specified destination is a bus stop)
	if (destlat, destlong) in busstopcoordinates.values():
		destname = list(busstopcoordinates.keys())[list(busstopcoordinates.values()).index((destlat, destlong))]
		pathlist = [p for p in pathlist if len([wp for wp in p["Route"] if wp[0] == destname and wp != p["Route"][-1]]) == 0]
	print("Pathlist contains " + str(len(pathlist)) + " paths:")
	print(pathlist)
	print()

	allroutelist = [] #to store the all route information, and total travel time in a tuple
	for path in pathlist:
		print("Source: " + path["Source"] + ", Destination: " + path["Destination"])
		print("Route waypoints:")
		print(path["Route"])
		print("Total route time: " + str(path["TravelTime"]) + " mins\n")

		route = [] #to store the routing information for each path
		addtraveltime = False #flag for special handling when calculating travel time where bus service is looping i.e. for A1 PGP->KRMRT
		etavalid = True #to know if eta is valid as sometimes ETA for third subsequent bus is not available
		totaltime = 0 #to store total travel time for each route
		rawpath = path["Route"]

		for i in range(len(rawpath)): #iterate through the bus stops of every path
			busstop, service = rawpath[i]
			rawservice = service.split(" ")[0] #service.split as we only want D1 and remove (TO BIZ2)
			servicewithoutspaces = service.upper().replace(" ", "") #to capitalise D1 (To BIZ 2) etc and remove spaces
			busarrivaltime = "-" #to store bus arrival information

			if i == 0: #source bus stop
				#add walking time to source bus stop
				walkingtime = getwalkingtime(sourcelat, sourcelong, float(venuedict[busstop][0]), float(venuedict[busstop][1]))
				totaltime += walkingtime
				print("Walking time to " + busstop +" is", str(walkingtime))


			if (i==0 or busstop==rawpath[i-1][0]) and len(rawpath) > 1: # if source bus stop or current busstop name is the same as the previous, transfer needed
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
						print("Missed both next and subsequent bus! Service " + service + " next bus is arriving in " + bustimings["arrivalTime"] + " mins and subsequent bus is arriving in " + bustimings["nextArrivalTime"]  +" mins! No ETA available.\n")
						etavalid = False #invalidate ETA

			elif i==len(rawpath)-1: #destination bus stop
				#add walking time to destination
				walkingtime = getwalkingtime(float(venuedict[busstop][0]), float(venuedict[busstop][1]), destlat, destlong)
				totaltime += walkingtime
				print("Walking time to destination is", str(walkingtime))

			#store bus stop name, service, latitude, longitude, isbusstop, arrivaltimings in route
			currentwaypoint = {}
			currentwaypoint["Name"] = busstop
			currentwaypoint["Service"] = service
			currentwaypoint["Latitude"] = venuedict[busstop][0]
			currentwaypoint["Longitude"] = venuedict[busstop][1]
			currentwaypoint["IsBusStop"] = venuedict[busstop][2]

			if i==0 or busarrivaltime=="-": #source bus stop return in mins/no arrival timing at transit bus stop
				currentwaypoint["BusArrivalTime"] = busarrivaltime
			else: #return transit stops in HH:MM am/pm
				busarrival = gettime() + datetime.timedelta(minutes = int(busarrivaltime))
				currentwaypoint["BusArrivalTime"] = busarrival.strftime("%-I:%M %p")

			route.append(currentwaypoint)

			if i!=len(rawpath)-1: #if last bus stop, need to skip adding the last busstop as we are at destination already
				if busstop!=rawpath[i-1][0] or addtraveltime: #if we are at transit bus stop do not add duplicate unless the transit stop is the last stop of a route
					for bs, time, direction in routedict[rawservice]: #to get the travel time from one bus stop to another
						if bs == busstop:#found the busstop
							#print(busstop, time)
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
					allroutelist.append((route, 100000+totaltime)) #since there's no ETA just initialise to a huge number



	allroutelist = sorted(allroutelist, key=lambda x: x[1]) #sort all routes by time


	if len(allroutelist) != 0: #if there's a route found
		sourcedestlist = [] #list for matching source and destination
		destlist = [] #list for matching destination
		sourcelist = [] #list for matching source
		otherlist = [] #list for does not match any
		alreadyrecommendedwalk = False #flag to know if the user was already recommended to walk
		busroutes = [] #list to store all the bus routes
		shortesttraveltime = allroutelist[0][1] #shortest travel time
		maxtimedifference = 5

		for i in range(len(allroutelist)):
			route, totaltime = allroutelist[i]
			routesourcelat = float(route[0]["Latitude"])
			routesourcelong = float(route[0]["Longitude"])
			routedestlat = float(route[-1]["Latitude"])
			routedestlong = float(route[-1]["Longitude"])
			busstoplist = [] #to store the bus stops for the current route

			for j in range(len(route)):
				busstopname = route[j]["Name"]
				if busstopname not in busstoplist: #ensure no duplicate bus stops during transit
					busstoplist.append(busstopname)

			if busstoplist in busroutes: #exact same route
				continue #skip this route

			if(len(route) == 1):
				if not alreadyrecommendedwalk: #ensure that user is only recommended to walk once
					sourcedestlist.append(allroutelist[i]) #priortise walk so add to sourcedestlist
					alreadyrecommendedwalk = True

			#check if route is within 5 mins and matches source and dest
			elif totaltime <= shortesttraveltime + maxtimedifference and routesourcelat == sourcelat and routesourcelong == sourcelong and routedestlat == destlat and routedestlong == destlong:
				sourcedestlist.append(allroutelist[i])

			#propose the route with exact destination
			elif totaltime <= shortesttraveltime + maxtimedifference and routedestlat == destlat and routedestlong == destlong:
				destlist.append(allroutelist[i])

			#propose the route with exact source
			elif totaltime <= shortesttraveltime + maxtimedifference and routesourcelat == sourcelat and routesourcelong == sourcelong:
				sourcelist.append(allroutelist[i])

			else: #more than 5mins/does not match source/dest
				otherlist.append(allroutelist[i])

			busroutes.append(busstoplist) #add current route to the list

		print("Shortest travel time: ", shortesttraveltime)
		#join all the lists
		sortedroutelist = [*sourcedestlist, *destlist, *sourcelist, *otherlist]

		#format data in JSON
		for i in range(min(len(sortedroutelist), 5)): # modify to select top x routes
			route, totaltime = sortedroutelist[i]
			data[i] = {}
			data[i]['Route'] = route

			if totaltime > 100000: #this is the route with no ETA
				data[i]['ETA'] = "-"
			else:
				eta = gettime() + datetime.timedelta(minutes = totaltime)
				data[i]['ETA'] = eta.strftime("%-I:%M %p")



	print("No of HTTP Requests: ", len(busarrivaltimedict))
	allroutelist.clear() #clear route list for next query
	pathlist.clear() #clear path list for next query

	json_data = json.dumps(data) # create a json object
	return json_data # return the json object




routedict={} # Stores bus route as key and value as list of pairs
venuedict={} # Stores all venue names including Bus Stops as key and latitude, longitude as pairs
busstopcoordinates={} # Stores coordinates of all bus stops
busstopdict={} # Stores all the bus stop name, NextBusAlias and bus services
busoperatinghoursdict = {} #stores all the operating hours of the buses
busarrivaltimedict = {} #acts as a cache for each request, i.e. it will be cleared after every request
stops_with_repeated_nodes = [] #to store bus stops with repeatednodes

# DECLARE NUMBER OF SOURCE-DESTINATION COMBINATIONS
numsources = 3
numdests = 3

# Delimiter to append id to repeated nodes
delimiter = "_"


# Read apiurl
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
				noofrepeat = len([ (x,y,z) for x, y, z in routedict[busroute] if x.split(delimiter)[0]  == busstop["Name"] ])
				if noofrepeat > 0: #if bus stop already exists in the current route
					#append _0 to existing one
					if noofrepeat==1:
						for i in range(len(routedict[busroute])):
							if routedict[busroute][i][0] == busstop["Name"]:
								routedict[busroute][i] = (busstop["Name"] + delimiter + str(0), routedict[busroute][i][1], routedict[busroute][i][2])
								break

					#append _number to new one
					routedict[busroute].append((busstop["Name"] + delimiter + str(noofrepeat), busstop["Time"], busstop["Direction"]))

					#add to stops with repeated nodes
					if (busstop["Name"], busroute) not in stops_with_repeated_nodes:
						stops_with_repeated_nodes.append((busstop["Name"], busroute))
				else:
					routedict[busroute].append((busstop["Name"], busstop["Time"], busstop["Direction"])) # busstop["Name"] returns name of bustop, busstop["Time"] returns time taken, busstop["Direction"] returns direction i.e. (To BIZ 2) etc

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

ref = db.reference("/BusOperatingHours") # access /BusOperatingHours
json_array = ref.get() # returns array of json
for service in json_array: # loop through each item
	if service is not None: # ensure bus service exists
		busoperatinghoursdict[service] = {} # initalise as dictionary
		for day in json_array[service]: #to get the service operating hours
			busoperatinghoursdict[service][day] = {} # initalise as dictionary
			for startend in json_array[service][day]: #to get the start and end timings
				busoperatinghoursdict[service][day][startend] = json_array[service][day][startend] #store to busoperatinghoursdict


# Store coordinates of bus stops in busstopcoordinates
for venue in venuedict:
	if venuedict[venue][2] == 'true':
		busstopcoordinates[venue] = (float(venuedict[venue][0]), float(venuedict[venue][1]))

if __name__ == "__main__":
	app.run()
