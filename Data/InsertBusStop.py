import firebase_admin
from firebase_admin import credentials
from firebase_admin import db
import json
import datetime


# Fetch the service account key JSON file contents
cred = credentials.Certificate("firebase.json")

#read firebaseurl
f=open("firebaseurl.txt", "r")
firebaseurl = f.read()
f.close()


# Initialize the app with a None auth variable, limiting the server's access
firebase_admin.initialize_app(cred, {
    'databaseURL': firebaseurl
})

ref = db.reference("/BusStops")
with open("BusStop.json", "r") as f:
	file_contents = json.load(f)
ref.set(file_contents)

#update last update time
ref = db.reference("/LastUpdated")
lastupdated = json.loads("{\"1\": {\"UpdatedDate\": \""+ str(datetime.datetime.now())  +"\"}}")
ref.set(lastupdated)