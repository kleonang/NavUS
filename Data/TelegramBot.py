#pip3 install python-telegram-bot

import logging, requests, json, difflib

from telegram.ext import Updater, CommandHandler, MessageHandler, Filters, ConversationHandler
from telegram import InlineKeyboardButton, InlineKeyboardMarkup, ReplyKeyboardMarkup, KeyboardButton
import firebase_admin
from firebase_admin import credentials
from firebase_admin import db
import datetime
from telegram.ext.dispatcher import run_async

venuelist = [] #to store all the venue names
busstoplist = [] #to store all the bus stop names

datadict = {} # to store source and destination as tuple OR ("QUERY", BusStopname), userid as key
routinginfodict = {} #to store routing information in case user wants more routes, userid as key
serverurl = "http://127.0.0.1:5000"

#function to get routing information
def getroute(update, userid):
    source, destination = datadict[userid] #get user's source and destination from datadict
    del datadict[userid] #remove user from datadict

    if type(source) is tuple: #if source is tuple means user send GPS location
        response = requests.get(serverurl + "/getpath/" + str(source[0]) + "/" + str(source[1]) + "/" + destination) #make a request to the url using coordinates

    else:
        response = requests.get(serverurl + "/getpath/" + source + "/" + destination) #make a request to the url


    if response.status_code == 200: #request successful
        jsonobj = json.loads(response.text)
        routinginfodict[userid] = {}
        routinginfodict[userid]["JSON"] = jsonobj
        routinginfodict[userid]["RouteIDToSend"] = "0"
        routinginfodict[userid]["RequestTime"] = datetime.datetime.now()
        
        if type(source) is tuple:
            routinginfodict[userid]["Source"] = "your location"
        else:
            routinginfodict[userid]["Source"] = source

        routinginfodict[userid]["Destination"] = destination


        reply = getinstructions(userid) #always return the first route


    else: #error accessing server
        reply="There's a issue accessing the server, please try again later." #string to store data to be returned

    return reply


#function to get arrival time of buses
def getarrival(update, userid):
    busstop = datadict[userid][1]
    del datadict[userid] #remove user from datadict
    response = requests.get(serverurl + "/getarrivaltimings/" + busstop) #make a request to the url

    reply = "<b>" + busstop + "</b>\n"
    if response.status_code == 200: #request successful
        jsonobj = json.loads(response.text)
        for service in jsonobj:
            if jsonobj[service]["arrivalTime"] == "-": #service is not in operation
                reply += "<i>" + service + "</i>: -\n\n"
            elif jsonobj[service]["arrivalTime"] == "0": #service is arriving
                reply += "<i>" + service + ":</i>\nArriving: Now\n" + "Subsequent: " + jsonobj[service]["nextArrivalTime"] + " mins\n\n"
            else:
                reply += "<i>" + service + ":</i>\nArriving: " + jsonobj[service]["arrivalTime"] + " mins\n" + "Subsequent: " + jsonobj[service]["nextArrivalTime"] + " mins\n\n"

    else: #error accessing server
        reply="There's a issue accessing the server, please try again later." #string to store data to be returned

    return reply

def getinstructions(userid):
    busservicelist = []
    stopslist = []
    sourcebusstop = ""

    #get user's source and destination
    source = routinginfodict[userid]["Source"] 
    destination = routinginfodict[userid]["Destination"] 
    timerequested = routinginfodict[userid]["RequestTime"]
    jsonobj = routinginfodict[userid]["JSON"]
    routeno = routinginfodict[userid]["RouteIDToSend"]

    if datetime.datetime.now() - timerequested > datetime.timedelta(minutes=1): #more than 1 min
        del routinginfodict[userid]
        return "Sorry, the data is out of date."


    ETA = jsonobj[routeno]["ETA"]
    TravelTime = str(jsonobj[routeno]["TravelTime"])

    if ETA=="-": #no ETA
        ETA = "Not available as there is no information on the third and subsequent buses."

    if TravelTime=="-":
        TravelTime = "Travel Time Unavailable"
    else:
        TravelTime += " mins"

    if len(jsonobj[routeno]["Route"])==0:
        reply = "No route found."

    elif len(jsonobj[routeno]["Route"])==1: #within walking distance
        reply = "You are within walking distance to your destination."

    else:
        for i in range(len(jsonobj[routeno]["Route"])):
            currentbusstopname = jsonobj[routeno]["Route"][i]["Name"]
            currentservice = jsonobj[routeno]["Route"][i]["Service"]
            busarrivaltime = jsonobj[routeno]["Route"][i]["BusArrivalTime"]
            busarrivaltimemins = jsonobj[routeno]["Route"][i]["BusArrivalTimeMins"]
            busservicelist.append(currentservice.split(" ")[0]) #remove (To BIZ 2) etc

            #first bus stop
            if i == 0:
                #user's source is not the same as the bus stop name
                if source != currentbusstopname:
                    stopslist.append("∙<b>Head to " + currentbusstopname + "</b>\n")

                stopslist.append("∙" + currentbusstopname + " <b>[Board service " + currentservice + " arriving in " + busarrivaltimemins + " mins (" + busarrivaltime + ")]</b>\n")
                sourcebusstop = currentbusstopname
            #last bus stop
            elif i == len(jsonobj[routeno]["Route"])-1:
                stopslist.append("∙" + currentbusstopname + " <b>[Alight here]</b>\n")
                #user's destination is not the last bus stop
                if destination != currentbusstopname:
                    stopslist.append("∙<b>Walk from " + currentbusstopname + " to " + destination + "</b>\n")

            #transfer required if both bus stops have the same name
            elif currentbusstopname==jsonobj[routeno]["Route"][i-1]["Name"]:
                stopslist.pop() #remove previous entry
                stopslist.append("∙" + currentbusstopname + " <b>[Transfer from " + jsonobj[routeno]["Route"][i-1]["Service"] + " to " + currentservice + ". Arriving in " + busarrivaltimemins + " mins (" + busarrivaltime +")]</b>\n")
            else:
                stopslist.append("∙" + currentbusstopname + "\n")


        #removce duplicates
        busservicelist = list(dict.fromkeys(busservicelist))
        busservicestring = ""
        #to print the bus services needed nicely
        for i in range(len(busservicelist)):
            #first bus service
            if i == 0: 
                busservicestring += busservicelist[i]
            #last bus serviceAA
            elif i==len(busservicelist)-1:
                busservicestring += " and " + busservicelist[i]
            else:
                busservicestring += ", " + busservicelist[i]


        if (routeno=="0"): #first route
            reply = "<b>Route " + str(int(routeno) + 1) + " of " + str(len(jsonobj)) + ": " + TravelTime + "</b>\nThe fastest way to get from " + source + " to " + destination + " is via ISB service " + busservicestring + ".\n" + ''.join(stopslist) + "Estimated arrival time: <i>" + ETA + "</i>"
        else:  #an alternative way
            reply = "<b>Route " + str(int(routeno) + 1) + " of " + str(len(jsonobj)) + ": " + TravelTime + "</b>\nAn alternative way to get from " + source + " to " + destination + " is via ISB service " + busservicestring + ".\n" + ''.join(stopslist) + "Estimated arrival time: <i>" + ETA + "</i>"


    routinginfodict[userid]["RouteIDToSend"] = str(int(routeno) + 1) #increment ID

    if int(routeno) < len(jsonobj) - 1: #still have routes
        reply += "\nType /more for other alternative routes."
        
    if int(routeno) == len(jsonobj) - 1:  #all routes has been sent to user, delete from dictionary
    
        del routinginfodict[userid]


    return reply


# Enable logging
logging.basicConfig(format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
                    level=logging.INFO)

logger = logging.getLogger(__name__)


# Define a few command handlers. These usually take the two arguments update and
# context. Error handlers also receive the raised TelegramError object in error.
def start(update, context):
    """Send a message when the command /start is issued."""
    userid = update.message.from_user["id"]
    if userid in datadict:
        del datadict[userid] #remove user from datadict
    keyboard = [[KeyboardButton(text="Send location using GPS",  request_location=True)]]
    update.message.reply_text("Welcome to NavUS! Send me your current location (GPS location is also accepted).\nSend /query to query bus arrival timings.\nSend /help to view my commands.", reply_markup = ReplyKeyboardMarkup(keyboard, one_time_keyboard=True))

def cancel(update, context):
    userid = update.message.from_user["id"]
    if userid in datadict:
        del datadict[userid] #remove user from datadict
        update.message.reply_text("Got it! Your request has been cancelled.")
    else:
        update.message.reply_text("You have no outstanding requests.")

def more(update, context):
    userid = update.message.from_user["id"]
    if userid in routinginfodict:
        reply = getinstructions(userid)
        update.message.reply_text(reply, parse_mode="HTML")
    else:
        update.message.reply_text("You have no outstanding requests.")


def help(update, context):
    """Send a message when the command /help is issued."""
    update.message.reply_text("Send me your current location (GPS location is also accepted) followed by your destination and I'll tell you how to get there!\nSend /cancel to re-enter your source.\nSend /more to view alternative routes.\nSend /query to query bus arrival timings.\nSend /start to start your route query.")


def getsourceanddestination(update, context):
    usermessage = update.message.text.upper()
    userid = update.message.from_user["id"]
    keyboard=[]

    #Check for query case
    if userid in datadict and datadict[userid][0]=="QUERY":
        if usermessage in busstoplist:
            datadict[userid]=("QUERY", usermessage) #add the queried bus stop name
            msg = update.message.reply_text("Getting arrival information...")
            reply = getarrival(update, userid)
            msg.edit_text(reply, parse_mode="HTML")
        else:
            for busstop in busstoplist:
                if usermessage.replace(" ","") in busstop.replace(" ", ""): #ignore spaces
                    keyboard.append([busstop])

            #still cannot find location, should be typo, use difflib
            if (len(keyboard) == 0):
                closematches = difflib.get_close_matches(usermessage, busstoplist)
                for busstop in closematches:
                    keyboard.append([busstop])

            if (len(keyboard) != 0):
                update.message.reply_text("Sorry, I didn't manage to locate your bus stop. Did you mean", reply_markup = ReplyKeyboardMarkup(keyboard, one_time_keyboard=True))
            else:
                update.message.reply_text("Sorry, I didn't manage to locate your bus stop.")
    else:
        if usermessage in venuelist: #the location exists in venuelist
            if userid in datadict: #user has already entered source
                    datadict[userid] = (datadict[userid][0], usermessage) #add destination
                    msg = update.message.reply_text("Calculating route...")
                    reply = getroute(update, userid)
                    msg.edit_text(reply, parse_mode="HTML")

            else: #user has just entered source
                datadict[userid] = (usermessage,"")
                update.message.reply_text("Got it! You're at "+ usermessage +". Now send me your destination. Enter /cancel to re-enter your source.")

        else: #usermessage is not a valid location
            #Do a substring search
            for venue in venuelist:
                if usermessage.replace(" ","") in venue.replace(" ", ""): #ignore spaces
                    keyboard.append([venue])

            #still cannot find location, should be typo, use difflib
            if (len(keyboard) == 0):
                closematches = difflib.get_close_matches(usermessage, venuelist)
                for venue in closematches:
                    keyboard.append([venue])

            if userid in datadict: #user has already entered source
                if (len(keyboard) != 0): #there are predictions
                    update.message.reply_text("Sorry, I didn't manage to locate your destination. Did you mean", reply_markup = ReplyKeyboardMarkup(keyboard, one_time_keyboard=True))
                else:
                    update.message.reply_text("Sorry, I didn't manage to locate your destination.")


            else: #user has just entered source
                if (len(keyboard) != 0): #there are predictions
                    update.message.reply_text("I didn't manage to locate your source. Did you mean", reply_markup = ReplyKeyboardMarkup(keyboard, one_time_keyboard=True))
                else:
                    update.message.reply_text("Sorry, I didn't manage to locate your source.")


def error(update, context):
    """Log Errors caused by Updates."""
    logger.warning('Update "%s" caused error "%s"', update, context.error)


def location(update, context):
    userid = update.message.from_user["id"]
    userlocation = (update.message.location)

    if userid in datadict: #user has already entered source
        if type(datadict[userid][0]) is tuple: #if source is tuple means user send GPS location
            update.message.reply_text("You have already sent me your location earlier. Enter /cancel to re-enter your source.")
        else:
            update.message.reply_text("You have already entered your source as " + datadict[userid][0] +". Enter /cancel to re-enter your source.")
    else:
        datadict[userid] = ((userlocation.latitude, userlocation.longitude),"") #store lat long as tuple
        update.message.reply_text("Got it! Now send me your destination. Enter /cancel to re-enter your source.")

def query(update, context):
    userid = update.message.from_user["id"]
    datadict[userid] = ("QUERY", "") #flag to know user asked for query

    keyboard = []
    for busstop in busstoplist:
        keyboard.append([busstop])
    update.message.reply_text("Got it! Select the bus stop. Enter /cancel to cancel your request.", reply_markup = ReplyKeyboardMarkup(keyboard, one_time_keyboard=True))


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

    ref = db.reference("/Venues") # access /Venues
    json_array = ref.get() # returns array of json
    for venue in json_array: # loop through each item
        if venue is not None: # ensure venue exists
            venuelist.append(venue["Name"]) # add to venue list
            if venue["IsBusStop"] == "true":
                busstoplist.append(venue["Name"]) #add bus stop to list

    #read Telegram Bot API key
    f=open("telegramapikey.txt", "r")
    telegramapikey = f.read()
    f.close()
    updater = Updater(telegramapikey, use_context=True)

    # Get the dispatcher to register handlers
    dp = updater.dispatcher

    # on different commands - answer in Telegram
    dp.add_handler(CommandHandler("cancel", cancel, run_async=True))
    dp.add_handler(CommandHandler("more", more, run_async=True))
    dp.add_handler(CommandHandler("start", start, run_async=True))
    dp.add_handler(CommandHandler("help", help, run_async=True))
    dp.add_handler(CommandHandler("query", query, run_async=True))

    #handle GPS location
    dp.add_handler(MessageHandler(Filters.location, location, run_async=True))

    # handle all other messages
    dp.add_handler(MessageHandler(Filters.text, getsourceanddestination, run_async=True))

    # log all errors
    dp.add_error_handler(error)

    # Start the Bot
    updater.start_polling()

    updater.idle()
