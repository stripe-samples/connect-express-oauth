<?php
use Slim\Http\Request;
use Slim\Http\Response;
use Stripe\Stripe;

require 'vendor/autoload.php';

$dotenv = Dotenv\Dotenv::create(__DIR__);
$dotenv->load();

require './config.php';

$app = new \Slim\App;

// Instantiate the logger as a dependency
$container = $app->getContainer();
$container['logger'] = function ($c) {
  $settings = $c->get('settings')['logger'];
  $logger = new Monolog\Logger($settings['name']);
  $logger->pushProcessor(new Monolog\Processor\UidProcessor());
  $logger->pushHandler(new Monolog\Handler\StreamHandler(__DIR__ . '/logs/app.log', \Monolog\Logger::DEBUG));
  return $logger;
};

$app->add(function ($request, $response, $next) {
    Stripe::setApiKey(getenv('STRIPE_SECRET_KEY'));
    return $next($request, $response);
});

$app->get('/get-oauth-link', function ($request, $response, $next) {
  session_start();
  $state = bin2hex(random_bytes('16')); 
  $_SESSION['state'] = $state; 

  $params = array(
    'state' => $state,
    'client_id' => getenv('STRIPE_CLIENT_ID'),
  );

  $url = 'https://connect.stripe.com/express/oauth/authorize?' . http_build_query($params);
  return $response->withJson(array('url' => $url));
});

$app->get('/authorize-oauth', function ($request, $response, $next) use ($app) {
  session_start();

  $state = $request->getQueryParam('state');
  $code = $request->getQueryParam('code');

  // Assert the state matches the state you provided in the OAuth link (optional).
  if ($_SESSION['state'] != $state)
    return $response->withStatus(403)->withJson(array('error' => 'Incorrect state parameter: ' . $state . '   ' . $_SESSION['state']));

  // Send the authorization code to Stripe's API.
  try {
    $stripeResponse = \Stripe\OAuth::token([
      'grant_type' => 'authorization_code',
      'code' => $code,
    ]);
  } catch (\Stripe\Error\OAuth\InvalidGrant $e) {
    return $response->withStatus(400)->withJson(array('error' => 'Invalid authorization code: ' . $code));
  } catch (Exception $e) {
    return $response->withStatus(500)->withJson(array('error' => 'An unknown error occurred.'));
  }

  $connectedAccountId = $stripeResponse->stripe_user_id;
  saveAccountId($connectedAccountId);

  // Render some HTML or redirect to a different page.
  return $response->withRedirect('/success.html');
});

function saveAccountId($id) {
  // Save the connected account ID from the response to your database.
  echo 'Connected account ID: ' . $id;
};

$app->get('/', function ($request, $response, $next) {   
  return $response->write(file_get_contents(getenv('STATIC_DIR') . '/index.html'));
});

$app->run();
