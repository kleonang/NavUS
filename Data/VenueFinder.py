# INSTALL DEPENDENCIES
# Refer to: https://pypi.org/project/selenium/
# pip install -U selenium
# Download web driver, e.g. chromedriver for chrome
# Rename path to your chromedriver file on line 29

import json
import requests
from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
from selenium.common.exceptions import TimeoutException
import pandas as pd

ay = input("Enter Academic Year (e.g. 2021-2022): ")
sem = input("Enter Semester (1/2/3[Special Term 1]/4[Special Term 2]): ")
print()

response = requests.get("https://api.nusmods.com/v2/" + ay + "/semesters/" + sem + "/venues.json")
venues = json.loads(response.text)
venues = sorted(venues)

column_names = ["NAME", "LATITUDE", "LONGITUDE"]
df = pd.DataFrame(columns = column_names)

# REPLACE parameter below with path to your web driver
driver = webdriver.Chrome("/Users/kleonang/Downloads/chromedriver/chromedriver")
for venue in venues:
	if "/" in venue:
		sp = venue.split("/")
		venue = sp[0] + "%2F" + sp[1]
	try:
		url = "https://nusmods.com/venues/" + venue
		driver.get(url)
		open_in_gmaps_xpath = "//*[@id='app']/div/div[1]/main/div/div[2]/div[1]/div/div/a"
		# Waits for 2 seconds before concluding there's no Google Maps link
		open_in_gmaps_button = WebDriverWait(driver, 2).until(EC.visibility_of_element_located((By.XPATH, open_in_gmaps_xpath))) 
		gmaps_url = open_in_gmaps_button.get_attribute("href")
		latlong = gmaps_url.split("query=")[1]
		latlongsplit = latlong.split("%2C")
		lat = latlongsplit[0]
		long = latlongsplit[1]
		new_row = {"NAME":venue, "LATITUDE":lat, "LONGITUDE":long}
		df = df.append(new_row, ignore_index=True)
	except TimeoutException:
		print("No Google Maps link for " + venue)
		continue
driver.close()
# Export to Excel file
df.to_excel("Venues_updated.xlsx")