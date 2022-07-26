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

ref = db.reference("/BusOperatingHours")
with open("BusOperatingHours.json", "r") as f:
	file_contents = json.load(f)
ref.set(file_contents)