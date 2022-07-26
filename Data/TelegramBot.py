#!/usr/bin/env python3

# INSTALL DEPENDENCIES FIRST
# pip3 install firebase-admin
# pip3 install python-telegram-bot
# pip3 install requests

import datetime
import difflib
import json
import logging
import re
import firebase_admin
import requests

from firebase_admin import credentials
from firebase_admin import db
from telegram import ReplyKeyboardMarkup, KeyboardButton
from telegram.ext import Updater, CommandHandler, MessageHandler, Filters

# DECLARE GLOBAL VARIABLES
venue_list = []  # Stores all the venue names
bus_stop_list = []  # Stores all the bus stop names
# Stores source and destination as tuple
# OR ("QUERY", BusStopName), user_id as key
data_dict = {}
# Stores routing information in case user wants more routes, user_id as key
routing_info_dict = {}
# Set of user_ids of bot users
users = set()


def get_route(update, user_id):
    """Gets routing information."""
    # Get user's source and destination from data_dict
    source, destination = data_dict[user_id]
    del data_dict[user_id]  # remove user from data_dict
    try:
        if isinstance(source, tuple):
            # If source is tuple, indicates user sent GPS location.
            # Make a request to the url using coordinates.
            response = requests.get(server_url + "/getpath/"
                                    + str(source[0]) + "/"
                                    + str(source[1]) + "/"
                                    + destination)
        else:
            response = requests.get(server_url + "/getpath/"
                                    + source + "/"
                                    + destination)  # Make a request to the url

        if response.status_code == 200:  # Request successful
            json_object = json.loads(response.text)
            routing_info_dict[user_id] = {}
            routing_info_dict[user_id]["JSON"] = json_object
            routing_info_dict[user_id]["RouteIDToSend"] = "0"
            routing_info_dict[user_id]["RequestTime"] = datetime.datetime.now()

            if isinstance(source, tuple):
                routing_info_dict[user_id]["Source"] = "your location"
            else:
                routing_info_dict[user_id]["Source"] = source

            routing_info_dict[user_id]["Destination"] = destination

            reply = get_instructions(user_id)  # Always return the first route
        else:  # Error accessing server
            reply = "There's an issue accessing the server, " \
                + "please try again later."

    except Exception:
        reply = "There's an issue accessing the server, " \
            + "please try again later."

    # Reply string stores data to be returned
    return reply


def get_arrival(update, user_id):
    """Get arrival timings of buses."""
    bus_stop = data_dict[user_id][1]
    del data_dict[user_id]  # Remove user from data_dict
    try:
        response = requests.get(server_url + "/getarrivaltimings/"
                                + bus_stop)  # Make a request to the url

        reply = "<b>" + bus_stop + "</b>\n"
        if response.status_code == 200:  # Request successful
            json_object = json.loads(response.text)
            for service in json_object:
                if json_object[service]["arrivalTime"] == "-":
                    # Service is not in operation
                    reply += "<i>" + service + "</i>: -\n\n"
                elif json_object[service]["arrivalTime"] == "0":
                    # Service is arriving
                    reply += "<i>" + service + ":</i>\n" \
                        + "Arriving: Now\n" \
                        + "Subsequent: " \
                        + json_object[service]["nextArrivalTime"] \
                        + " mins\n\n"
                else:
                    reply += "<i>" + service + ":</i>\n" \
                        + "Arriving: " \
                        + json_object[service]["arrivalTime"] + " mins\n" \
                        + "Subsequent: " \
                        + json_object[service]["nextArrivalTime"] \
                        + " mins\n\n"
        else:  # Error accessing server
            # String to store data to be returned
            reply = "There's an issue accessing the server, " \
                + "please try again later."

    except Exception:
        reply = "There's an issue accessing the server, " \
            + "please try again later."

    return reply


def get_instructions(user_id):
    """Get route details and instructions."""
    bus_service_list = []
    stops_list = []
    # Get user's source and destination
    source = routing_info_dict[user_id]["Source"]
    destination = routing_info_dict[user_id]["Destination"]
    time_requested = routing_info_dict[user_id]["RequestTime"]
    json_object = routing_info_dict[user_id]["JSON"]
    route_num = routing_info_dict[user_id]["RouteIDToSend"]

    if datetime.datetime.now() \
            - time_requested > datetime.timedelta(minutes=1):
        # more than 1 min
        del routing_info_dict[user_id]
        return "Sorry, the data is out of date."

    if len(json_object) == 0:  # JSON response is {}
        return "No route found."

    eta = json_object[route_num]["ETA"]
    travel_time = str(json_object[route_num]["TravelTime"])

    if eta == "-":  # no ETA
        eta = "Not available as there is no information on the " \
            + "third and subsequent buses."

    if travel_time == "-":
        travel_time = "Travel Time Unavailable"
    else:
        travel_time += " mins"

    if len(json_object[route_num]["Route"]) == 1:  # Within walking distance
        reply = "You are within walking distance to your destination."

    else:
        for i in range(len(json_object[route_num]["Route"])):
            current_bus_stop_name = json_object[route_num]["Route"][i]["Name"]
            current_service = json_object[route_num]["Route"][i]["Service"]
            bus_arrival_time \
                = json_object[route_num]["Route"][i]["BusArrivalTime"]
            bus_arrival_time_mins \
                = json_object[route_num]["Route"][i]["BusArrivalTimeMins"]
            # Split on " " to remove (To BIZ 2) etc
            bus_service_list.append(current_service.split(" ")[0])

            # First bus stop
            if i == 0:
                # User's source is not the same as the bus stop name
                if source != current_bus_stop_name:
                    stops_list.append(
                        "∙<b>Head to " + current_bus_stop_name + "</b>\n")

                stops_list.append("∙" + current_bus_stop_name
                                  + " <b>[Board service " + current_service
                                  + " arriving in " + bus_arrival_time_mins
                                  + " mins (" + bus_arrival_time + ")]</b>\n")
            # Last bus stop
            elif i == len(json_object[route_num]["Route"]) - 1:
                stops_list.append("∙" + current_bus_stop_name
                                  + " <b>[Alight here]</b>\n")
                # User's destination is not the last bus stop
                if destination != current_bus_stop_name:
                    stops_list.append("∙<b>Walk from " + current_bus_stop_name
                                      + " to " + destination + "</b>\n")

            # Transfer required if both bus stops have the same name
            elif current_bus_stop_name \
                    == json_object[route_num]["Route"][i - 1]["Name"]:
                stops_list.pop()  # Remove previous entry
                stops_list.append(
                    "∙" + current_bus_stop_name
                    + " <b>[Transfer from "
                    + json_object[route_num]["Route"][i - 1]["Service"]
                    + " to " + current_service + ". Arriving in "
                    + bus_arrival_time_mins + " mins ("
                    + bus_arrival_time + ")]</b>\n")
            else:
                stops_list.append("∙" + current_bus_stop_name + "\n")

        # Remove duplicates
        bus_service_list = list(dict.fromkeys(bus_service_list))
        bus_service_string = ""
        # To print the bus services needed nicely
        for i in range(len(bus_service_list)):
            # First bus service
            if i == 0:
                bus_service_string += bus_service_list[i]
            # Last bus service
            elif i == len(bus_service_list) - 1:
                bus_service_string += " and " + bus_service_list[i]
            else:
                bus_service_string += ", " + bus_service_list[i]

        if route_num == "0":  # First route
            reply = "<b>Route " + str(int(route_num) + 1) + " of " \
                + str(len(json_object)) + ": " + travel_time \
                + "</b>\nThe fastest way to get from " + source \
                + " to " + destination + " is via ISB service " \
                + bus_service_string + ".\n" + ''.join(stops_list) \
                + "Estimated arrival time: <i>" + eta + "</i>"
        else:  # An alternative way
            reply = "<b>Route " + str(int(route_num) + 1) + " of " \
                + str(len(json_object)) + ": " + travel_time \
                + "</b>\nAn alternative way to get from " + source \
                + " to " + destination + " is via ISB service " \
                + bus_service_string + ".\n" + ''.join(stops_list) \
                + "Estimated arrival time: <i>" + eta + "</i>"

    # Increment ID
    routing_info_dict[user_id]["RouteIDToSend"] = str(int(route_num) + 1)

    if int(route_num) < len(json_object) - 1:
        # Still have routes
        reply += "\nType /more for other alternative routes."

    if int(route_num) == len(json_object) - 1:
        # All routes has been sent to user, delete from dictionary
        del routing_info_dict[user_id]

    return reply


# Define a few command handlers.
# These usually take the two arguments update and context.
# Error handlers also receive the raised TelegramError object in error.
def start(update, context):
    """Send a message when the command /start is issued."""
    user = update.message.from_user
    user_id = user["id"]
    users.add(user)
    if user_id in data_dict:
        del data_dict[user_id]  # remove user from data_dict
    keyboard = [[KeyboardButton(text="Send location using GPS",
                                request_location=True)]]
    update.message.reply_text(
        "Welcome to NavUS! Type in your current location "
        + "(GPS location is also accepted).\n"
        + "Send /query to query bus arrival timings.\n"
        + "Send /help to view my commands.",
        reply_markup=ReplyKeyboardMarkup(
            keyboard,
            one_time_keyboard=True))


def cancel(update, context):
    """Cancel route query when the command /cancel is issued."""
    user = update.message.from_user
    user_id = user["id"]
    users.add(user)
    if user_id in data_dict:
        del data_dict[user_id]  # remove user from data_dict
        update.message.reply_text("Got it! Your request has been cancelled.")
    else:
        update.message.reply_text("You have no outstanding requests.")


def more(update, context):
    """Send more routes when the command /more is issued."""
    user = update.message.from_user
    user_id = user["id"]
    users.add(user)
    if user_id in routing_info_dict:
        reply = get_instructions(user_id)
        update.message.reply_text(reply, parse_mode="HTML")
    else:
        update.message.reply_text("You have no outstanding requests.")


def help(update, context):
    """Send a help message when the command /help is issued."""
    update.message.reply_text(
        "Type in your current location (GPS location is also accepted) "
        + "followed by your destination and I'll tell you how to get there!\n"
        + "Send /cancel to re-enter your source.\n"
        + "Send /more to view alternative routes.\n"
        + "Send /query to query bus arrival timings.\n"
        + "Send /start to start your route query.")


def get_source_and_destination(update, context):
    """Get source and destination from user on Telegram."""
    user_message = update.message.text.upper()
    user = update.message.from_user
    user_id = user["id"]
    users.add(user)
    keyboard = []

    # Check for query case
    if user_id in data_dict and data_dict[user_id][0] == "QUERY":
        valid = False
        # check for valid input
        for bus_stop in bus_stop_list:
            alias = ""  # to store alias like KRMRT
            # To store names with brackets like KENT RIDGE MRT
            bus_stop_name = ""
            match = re.search(r"(.+) \((.+)\)", bus_stop)
            if match:
                bus_stop_name = match.group(1)
                alias = match.group(2)
            msg = user_message.replace(" ", "")
            if user_message == bus_stop \
                    or msg == bus_stop.replace(" ", "") \
                    or msg == alias.replace(" ", "") \
                    or msg == bus_stop_name.replace(" ", ""):
                user_message = bus_stop
                valid = True
                break

        if valid:
            # Add the queried bus stop name
            data_dict[user_id] = ("QUERY", user_message)
            msg = update.message.reply_text("Getting arrival information...")
            reply = get_arrival(update, user_id)
            msg.edit_text(reply, parse_mode="HTML")
        else:
            for bus_stop in bus_stop_list:
                if user_message.replace(" ", "") in bus_stop.replace(" ", ""):
                    # Ignore spaces
                    keyboard.append([bus_stop])

            # Cannot find location likely due to typo, use difflib
            if len(keyboard) == 0:
                close_matches = difflib.get_close_matches(user_message,
                                                          bus_stop_list)
                for bus_stop in close_matches:
                    keyboard.append([bus_stop])

            if len(keyboard) != 0:
                update.message.reply_text(
                    "Sorry, I didn't manage to locate your bus stop. "
                    + "Did you mean:",
                    reply_markup=ReplyKeyboardMarkup(keyboard,
                                                     one_time_keyboard=True))
            else:
                update.message.reply_text(
                    "Sorry, I didn't manage to locate your bus stop.")

    # Not query, should be source and destination
    else:
        valid = False
        # Check for valid input
        for venue in venue_list:
            alias = ""  # To store alias like KRMRT
            # To store venue names with brackets like KENT RIDGE MRT
            venue_name = ""
            match = re.search(r"(.+) \((.+)\)", venue)
            if match:
                venue_name = match.group(1)
                alias = match.group(2)
            msg = user_message.replace(" ", "")
            if user_message == venue \
                    or msg == venue.replace(" ", "") \
                    or msg == alias.replace(" ", "") \
                    or msg == venue_name.replace(" ", ""):

                user_message = venue
                valid = True
                break

        if valid:  # Location exists in venue_list
            if user_id in data_dict:  # User has already entered source
                # Add destination
                data_dict[user_id] = (data_dict[user_id][0], user_message)
                msg = update.message.reply_text("Calculating route...")
                reply = get_route(update, user_id)
                msg.edit_text(reply, parse_mode="HTML")

            else:  # User has just entered source
                data_dict[user_id] = (user_message, "")
                update.message.reply_text("Got it! You're at "
                                          + user_message + ". "
                                          + "Now type in your destination. "
                                          + "Enter /cancel "
                                          + "to re-enter your source.")

        else:  # user_message is not a valid location
            # Do a substring search
            for venue in venue_list:
                if user_message.replace(" ", "") in venue.replace(" ", ""):
                    # Ignore spaces
                    keyboard.append([venue])

            # Cannot find location likely due to typo, use difflib
            if len(keyboard) == 0:
                close_matches = difflib.get_close_matches(user_message,
                                                          venue_list)
                for venue in close_matches:
                    keyboard.append([venue])

            if user_id in data_dict:  # User has already entered source
                if len(keyboard) != 0:  # There are predictions
                    update.message.reply_text(
                        "Sorry, I didn't manage to locate your destination. "
                        + "Did you mean:",
                        reply_markup=ReplyKeyboardMarkup(
                            keyboard, one_time_keyboard=True))
                else:
                    update.message.reply_text(
                        "Sorry, I didn't manage to locate your destination.")

            else:  # User has just entered source
                if len(keyboard) != 0:  # There are predictions
                    update.message.reply_text(
                        "I didn't manage to locate your source. Did you mean:",
                        reply_markup=ReplyKeyboardMarkup(
                            keyboard,
                            one_time_keyboard=True))
                else:
                    update.message.reply_text(
                        "Sorry, I didn't manage to locate your source.")


def error(update, context):
    """Log errors caused by updates."""
    logger.warning('Update "%s" caused error "%s"', update, context.error)
    print("Type: ", type(context.error))


def location(update, context):
    """Provides updates to users on their location."""
    user = update.message.from_user
    user_id = user["id"]
    user_location = update.message.location

    if user_id in data_dict:  # User has already entered source
        if isinstance(data_dict[user_id][0], tuple):
            # If source is tuple, indicates user sent GPS location.
            update.message.reply_text(
                "You have already sent me your location earlier. "
                + "Enter /cancel to re-enter your source.")
        else:
            update.message.reply_text(
                "You have already entered your source as "
                + data_dict[user_id][0] + ". "
                + "Enter /cancel to re-enter your source.")
    else:
        data_dict[user_id] = ((user_location.latitude,
                               user_location.longitude),
                              "")  # Store (latitide, longitude) as tuple
        update.message.reply_text(
            "Got it! Now send me your destination. "
            + "Enter /cancel to re-enter your source.")


def query(update, context):
    """Get user query from reply keyboard input."""
    user = update.message.from_user
    user_id = user["id"]
    users.add(user)
    data_dict[user_id] = ("QUERY", "")  # Flag to know if user asked for query

    keyboard = []
    for bus_stop in bus_stop_list:
        keyboard.append([bus_stop])

    update.message.reply_text(
        "Got it! Select the bus stop. Enter /cancel to cancel your request.",
        reply_markup=ReplyKeyboardMarkup(keyboard, one_time_keyboard=True))


def get_users(update, context):
    """Gets list of all bot users (by user_id) and outputs to server."""
    user_ids_list = []
    user_names_list = []
    # Populate lists of user ids and user names
    for user in list(users):
        # Get user id and append to user_ids_list
        user_id = str(user["id"]) + "\n"
        user_ids_list.append(user_id)
        # Initialise names
        first_name = "" if user["first_name"] is None else user["first_name"]
        last_name = "" if user["last_name"] is None else user["last_name"]
        username = "" if user["username"] is None else user["username"]
        # Concatenate user's name to be displayed
        user_name = first_name + " " + last_name + " (@" + username + ")"
        user_names_list.append(user_name)
    # Sort lists of user ids and user names
    user_ids_list = sorted(user_ids_list)
    user_names_list = sorted(user_names_list)
    # Output user_ids to server
    users_count = len(user_ids_list)
    now = datetime.datetime.now()
    time_string = now.strftime("%Y_%m_%d_%H:%M:%S")
    outfile_name = "NavUSBot Users_" + time_string
    outfile = open(outfile_name, "w")
    outfile.writelines(user_ids_list)
    outfile.close()
    # Update user(admin) with user names
    reply = "NavUS has " + str(users_count) + " Telegram bot users:\n"
    for name in user_names_list:
        reply += name + "\n"
    update.message.reply_text(reply)


# Enable logging
logging.basicConfig(
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
    level=logging.INFO)

logger = logging.getLogger(__name__)

# Read server url
f = open("server_url.txt", "r")
server_url = f.read()
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

# Import Venues from database
ref = db.reference("/Venues")  # access /VenuesNew
json_array = ref.get()  # returns array of json
for venue in json_array:  # loop through each item
    if venue is not None:  # ensure venue exists
        venue_list.append(venue["Name"])  # add to venue list
        if venue["IsBusStop"] == "true":
            bus_stop_list.append(venue["Name"])  # add bus stop to list

# Read Telegram Bot API key and update it
f = open("telegramapikey.txt", "r")
telegram_api_key = f.read()
f.close()
updater = Updater(telegram_api_key, use_context=True)

# Read admin user_ids
f = open("NavUSadmins.txt", "r")
content = f.read()
admin_ids = [int(x) for x in content.split(",")]
f.close()

# Get the dispatcher to register handlers
dp = updater.dispatcher

# On different commands - answer in Telegram
dp.add_handler(CommandHandler("cancel", cancel, run_async=True))
dp.add_handler(CommandHandler("more", more, run_async=True))
dp.add_handler(CommandHandler("start", start, run_async=True))
dp.add_handler(CommandHandler("help", help, run_async=True))
dp.add_handler(CommandHandler("query", query, run_async=True))
dp.add_handler(CommandHandler("getusers", get_users, Filters.user(
    user_id=admin_ids), run_async=True))

# Handle GPS location
dp.add_handler(MessageHandler(Filters.location, location, run_async=True))

# Handle all other messages
dp.add_handler(MessageHandler(Filters.text,
                              get_source_and_destination,
                              run_async=True))

# Log all errors
dp.add_error_handler(error)

# Start the Bot
updater.start_polling()

updater.idle()
