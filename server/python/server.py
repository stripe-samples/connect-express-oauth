#! /usr/bin/env python3.6

"""
server.py
Stripe Sample.
Python 3.6 or newer required.
"""

import json
import os
import secrets
import string

import stripe
from dotenv import load_dotenv, find_dotenv
from flask import Flask, jsonify, render_template, redirect, request, session, send_from_directory
import urllib

# Setup Stripe python client library
load_dotenv(find_dotenv())
stripe.api_key = os.getenv('STRIPE_SECRET_KEY')
stripe.api_version = os.getenv('STRIPE_API_VERSION', '2019-12-03')

static_dir = str(os.path.abspath(os.path.join(__file__ , "..", os.getenv("STATIC_DIR"))))
app = Flask(__name__, static_folder=static_dir,
            static_url_path="", template_folder=static_dir)

# Set the secret key to some random bytes. Keep this really secret!
# This enables Flask sessions.
app.secret_key = b'_5#y2L"F4Q8z\n\xec]/'

@app.route('/', methods=['GET'])
def get_example():
    return render_template('index.html')


@app.route("/get-oauth-link", methods=["GET"])
def construct_oauth_link():
    state = ''.join([secrets.choice(string.ascii_letters + string.digits) for n in range(16)])
    session['state'] = state
    args = {
        "client_id": os.getenv('STRIPE_CLIENT_ID'),
        "state": state,
        "response_type": "code",
        "scope": "read_write",
    }
    url = "https://connect.stripe.com/express/oauth/authorize?{}".format(urllib.parse.urlencode(args))
    return jsonify({'url': url})


@app.route("/authorize-oauth", methods=["GET"])
def handle_oauth_redirect():
  if request.args.get("state") != session['state']:
    return json.dumps({"error": "Incorrect state parameter: " + request.args.get("state")}), 403

  # Send the authorization code to Stripe's API.
  code = request.args.get("code")
  try:
    response = stripe.OAuth.token(grant_type="authorization_code", code=code,)
  except stripe.oauth_error.OAuthError as e:
    return json.dumps({"error": "Invalid authorization code: " + code}), 400
  except Exception as e:
    return json.dumps({"error": "An unknown error occurred."}), 500

  connected_account_id = response["stripe_user_id"]
  save_account_id(connected_account_id)

  # Render some HTML or redirect to a different page.
  return redirect("/success.html")


def save_account_id(id):
  # Save the connected account ID from the response to your database.
  print("Connected account ID: ", id)
