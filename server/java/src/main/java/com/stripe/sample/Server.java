package com.stripe.sample;

import com.google.gson.Gson;
import com.stripe.Stripe;
import com.stripe.net.OAuth;
import com.stripe.model.oauth.TokenResponse;
import com.stripe.exception.StripeException;
import com.stripe.exception.oauth.InvalidGrantException;
import spark.Response;

import org.apache.http.client.utils.URIBuilder;

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.security.SecureRandom;

// Using Spark.
import static spark.Spark.*;

import io.github.cdimascio.dotenv.Dotenv;

public class Server {
    private static Gson gson = new Gson();
    private static RandomString randomString = new RandomString(16);

    static class LinkResponse {
        private String url;

        public LinkResponse(String url) {
            this.url = url;
        }
    }

    static class RandomString
    {
        private static final String symbols = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"; 

        private final SecureRandom random = new SecureRandom();

        private final char[] buf;

        public RandomString(int length)
        {
            if (length < 1)
            throw new IllegalArgumentException("length < 1: " + length);
            buf = new char[length];
        }

        public String nextString()
        {
            for (int idx = 0; idx < buf.length; ++idx) 
            buf[idx] = symbols.charAt(random.nextInt(symbols.length()));
            return new String(buf);
        }
    }

    public static void main(String[] args) {
        port(4242);
        Dotenv dotenv = Dotenv.load();
        Stripe.apiKey = dotenv.get("STRIPE_SECRET_KEY");
        staticFiles.externalLocation(
                Paths.get(Paths.get("").toAbsolutePath().toString(), dotenv.get("STATIC_DIR")).normalize().toString());

        get("/get-oauth-link", (request, response) -> {
            String state = randomString.nextString();
            request.session().attribute("state", state);
            URIBuilder builder = new URIBuilder("https://connect.stripe.com/express/oauth/authorize");
            builder.addParameter("client_id", dotenv.get("STRIPE_CLIENT_ID"));
            builder.addParameter("state", state);
            return gson.toJson(new LinkResponse(builder.build().toString()));
        });

        get("/authorize-oauth", (request, response) -> {
            // Assert the state matches the state you provided in the OAuth link (optional).
            if (!request.queryParams("state").equals(request.session().attribute("state"))) {
                return buildErrorResponse(
                    response, 403, "error", "Incorrect state parameter: " + request.queryParams("state")
                );
            }

            // Send the authorization code to Stripe's API.
            String code = request.queryParams("code");
            Map<String, Object> params = new HashMap<>();
            params.put("grant_type", "authorization_code");
            params.put("code", code);

            try {
                TokenResponse stripeResponse = OAuth.token(params, null);
                // Save the connected account ID from the response to your database.
                String connectedAccountId = stripeResponse.getStripeUserId();
                saveAccountId(connectedAccountId);

                // Render some HTML or redirect to a different page.
                response.redirect("success.html");
                return "";
            } catch (InvalidGrantException e) {
                // There's a problem with the authorization code.
                return buildErrorResponse(
                response, 400, "error", "Invalid authorization code: " + code
                );
            } catch (StripeException e) {
                // All other errors.
                return buildErrorResponse(
                response, 500, "error", "An unknown error occurred."
                );
            }
        });
    }

    private static void saveAccountId(String id) {
        System.out.println("Connected account ID: " + id);
    }

    private static String buildErrorResponse(
        Response response, int statusCode, String type, String message
    ) {
        response.status(statusCode);
        Map<String, String> errorResponse = new HashMap<>();
        errorResponse.put(type, message);
        return gson.toJson(errorResponse);
    }
}

