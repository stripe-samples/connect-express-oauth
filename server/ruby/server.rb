# frozen_string_literal: true

require 'stripe'
require 'sinatra'
require 'dotenv'

# Replace if using a different env file or config
Dotenv.load
Stripe.api_key = ENV['STRIPE_SECRET_KEY']

enable :sessions
set :static, true
set :public_folder, File.join(File.dirname(__FILE__), ENV['STATIC_DIR'])
set :port, 4242

helpers do
  def request_headers
    env.each_with_object({}) { |(k, v), acc| acc[Regexp.last_match(1).downcase] = v if k =~ /^http_(.*)/i; }
  end
end

get '/' do
  content_type 'text/html'
  send_file File.join(settings.public_folder, 'index.html')
end

get '/get-oauth-link' do
  state = SecureRandom.alphanumeric
  session[:state] = state
  args = {
    client_id: ENV['STRIPE_CLIENT_ID'],
    state: state
  }
  url = URI::HTTPS.build(
    host: 'connect.stripe.com',
    path: '/express/oauth/authorize',
    query: URI.encode_www_form(args)
  )
  return {'url': url}.to_json
end

get '/authorize-oauth' do
  content_type 'application/json'

  # Assert the state matches the state we provided in the OAuth link.
  if params[:state] != session[:state]
    status 403
    return {error: 'Incorrect state parameter: ' + params[:state]}.to_json
  end

  # Send the authorization code to Stripe's API.
  code = params[:code]
  begin
    response = Stripe::OAuth.token({
      grant_type: 'authorization_code',
      code: code,
    })
  rescue Stripe::OAuth::InvalidGrantError
    status 400
    return {error: 'Invalid authorization code: ' + code}.to_json
  rescue Stripe::StripeError
    status 500
    return {error: 'An unknown error occurred.'}.to_json
  end

  connected_account_id = response.stripe_user_id
  save_account_id(connected_account_id)

  # Render some HTML or redirect to a different page.
  redirect '/success.html'
end

def save_account_id(id)
  # Save the connected account ID from the response to the database.
  puts 'Connected account ID: ' + id
end
