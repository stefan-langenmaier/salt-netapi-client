package com.suse.salt.netapi.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.suse.salt.netapi.AuthModule.AUTO;
import static com.suse.salt.netapi.config.ClientConfig.SOCKET_TIMEOUT;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.suse.salt.netapi.client.impl.JDKConnectionFactory;
import com.suse.salt.netapi.datatypes.Token;
import com.suse.salt.netapi.datatypes.cherrypy.Stats;
import com.suse.salt.netapi.exception.SaltException;
import com.suse.salt.netapi.exception.SaltUserUnauthorizedException;
import com.suse.salt.netapi.utils.ClientUtils;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Arrays;

/**
 * Salt API client unit tests.
 */
public class SaltClientTest {

    private static final int MOCK_HTTP_PORT = 8888;

    static final String JSON_LOGIN_REQUEST = ClientUtils.streamToString(
            SaltClientTest.class.getResourceAsStream("/login_request.json"));
    static final String JSON_LOGIN_RESPONSE = ClientUtils.streamToString(
            SaltClientTest.class.getResourceAsStream("/login_response.json"));
    static final String JSON_STATS_RESPONSE = ClientUtils.streamToString(
            SaltClientTest.class.getResourceAsStream("/stats_response.json"));
    static final String JSON_HOOK_RESPONSE = ClientUtils.streamToString(
            SaltClientTest.class.getResourceAsStream("/hook_response.json"));
    static final String JSON_LOGOUT_RESPONSE = ClientUtils.streamToString(
            SaltClientTest.class.getResourceAsStream("/logout_response.json"));

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(MOCK_HTTP_PORT);

    private SaltClient client;

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Before
    public void init() {
        URI uri = URI.create("http://localhost:" + Integer.toString(MOCK_HTTP_PORT));
        client = new SaltClient(uri);
    }

    @Test
    public void testLoginOk() throws Exception {
        stubFor(any(urlMatching(".*"))
                .willReturn(aResponse()
                .withStatus(HttpURLConnection.HTTP_OK)
                .withHeader("Content-Type", "application/json")
                .withBody(JSON_LOGIN_RESPONSE)));

        Token token = client.login("user", "pass", AUTO);
        assertEquals("Token mismatch",
                "f248284b655724ca8a86bcab4b8df608ebf5b08b", token.getToken());
        assertEquals("EAuth mismatch", "auto", token.getEauth());
        assertEquals("User mismatch", "user", token.getUser());
        assertEquals("Perms mismatch", Arrays.asList(".*", "@wheel"), token.getPerms());

        verify(1, postRequestedFor(urlEqualTo("/login"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Content-Type", equalTo("application/json"))
                .withRequestBody(equalToJson(JSON_LOGIN_REQUEST)));
    }

    @Test(expected = SaltUserUnauthorizedException.class)
    public void testLoginFailure() throws Exception {
        stubFor(any(urlMatching(".*"))
                .willReturn(aResponse()
                .withStatus(HttpURLConnection.HTTP_UNAUTHORIZED)));
        client.login("user", "pass", AUTO);
    }

    @Test
    public void testRunRequestWithSocketTimeout() throws Exception {
        exception.expect(SaltException.class);
        exception.expectMessage(containsString("Read timed out"));

        // create a local SaltClient with a fast timeout configuration
        // to do not lock tests more than 2s
        URI uri = URI.create("http://localhost:" + Integer.toString(MOCK_HTTP_PORT));
        SaltClient clientWithFastTimeout = new SaltClient(uri);
        clientWithFastTimeout.getConfig().put(SOCKET_TIMEOUT, 1000);

        stubFor(any(urlMatching(".*"))
                .willReturn(aResponse()
                .withFixedDelay(2000)));

        clientWithFastTimeout.login("user", "pass", AUTO);
    }

    @Test
    public void testRunRequestWithSocketTimeoutThroughJDKConnection() throws Exception {
        exception.expect(SaltException.class);
        exception.expectMessage(containsString("Read timed out"));

        // create a local SaltClient with a fast timeout configuration
        // to do not lock tests more than 2s
        URI uri = URI.create("http://localhost:" + Integer.toString(MOCK_HTTP_PORT));
        SaltClient clientWithFastTimeout = new SaltClient(uri,
                new JDKConnectionFactory());
        clientWithFastTimeout.getConfig().put(SOCKET_TIMEOUT, 1000);

        stubFor(post(urlEqualTo("/login"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Content-Type", equalTo("application/json"))
                .willReturn(aResponse()
                .withFixedDelay(2000)));

        clientWithFastTimeout.login("user", "pass", AUTO);
    }

    @Test
    public void testStats() throws Exception {
        stubFor(any(urlMatching(".*"))
                .willReturn(aResponse()
                .withStatus(HttpURLConnection.HTTP_OK)
                .withHeader("Content-Type", "application/json")
                .withBody(JSON_STATS_RESPONSE)));

        Stats stats = client.stats();

        assertNotNull(stats);
        verify(1, getRequestedFor(urlEqualTo("/stats"))
                .withHeader("Accept", equalTo("application/json"))
                .withRequestBody(equalTo("")));
    }

    @Test
    public void testSendEvent() throws Exception {
        stubFor(any(urlMatching(".*"))
                .willReturn(aResponse()
                .withStatus(HttpURLConnection.HTTP_OK)
                .withHeader("Content-Type", "application/json")
                .withBody(JSON_HOOK_RESPONSE)));

        JsonObject json = new JsonObject();
        json.addProperty("foo", "bar");
        JsonArray array = new JsonArray();
        array.add(new JsonPrimitive("one"));
        array.add(new JsonPrimitive("two"));
        array.add(new JsonPrimitive("three"));
        json.add("list", array);
        String data = json.toString();

        assertTrue(client.sendEvent("my/tag", data));
        verify(1, postRequestedFor(urlEqualTo("/hook/my/tag"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Content-Type", equalTo("application/json"))
                .withRequestBody(equalTo(data)));
    }

    @Test
    public void testLogout() throws Exception {
        stubFor(any(urlMatching(".*"))
                .willReturn(aResponse()
                .withStatus(HttpURLConnection.HTTP_OK)
                .withHeader("Content-Type", "application/json")
                .withBody(JSON_LOGOUT_RESPONSE)));

        assertTrue(client.logout());
        verify(1, postRequestedFor(urlEqualTo("/logout"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Content-Type", equalTo("application/json"))
                .withRequestBody(equalTo("")));
    }
}
