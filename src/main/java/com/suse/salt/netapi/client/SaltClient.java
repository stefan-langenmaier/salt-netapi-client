package com.suse.salt.netapi.client;

import com.suse.salt.netapi.AuthModule;
import com.suse.salt.netapi.calls.Call;
import com.suse.salt.netapi.calls.Client;
import com.suse.salt.netapi.client.impl.HttpClientConnectionFactory;
import com.suse.salt.netapi.config.ClientConfig;
import com.suse.salt.netapi.config.ProxySettings;
import com.suse.salt.netapi.datatypes.Token;
import com.suse.salt.netapi.datatypes.cherrypy.Stats;
import com.suse.salt.netapi.event.EventListener;
import com.suse.salt.netapi.event.EventStream;
import com.suse.salt.netapi.exception.SaltException;
import com.suse.salt.netapi.parser.JsonParser;
import com.suse.salt.netapi.results.Return;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Salt API client.
 */
public class SaltClient {

    /** The configuration object */
    private final ClientConfig config = new ClientConfig();

    /** The connection factory object */
    private final ConnectionFactory connectionFactory;

    private final Gson gson = new GsonBuilder().create();

    /**
     * Constructor for connecting to a given URL.
     *
     * @param url the Salt API URL
     */
    public SaltClient(URI url) {
        this(url, new HttpClientConnectionFactory());
    }

    /**
     * Constructor for connecting to a given URL using a specific connection factory.
     *
     * @param url the Salt API URL
     * @param connectionFactory ConnectionFactory implementation
     */
    public SaltClient(URI url, ConnectionFactory connectionFactory) {
        // Put the URL in the config
        config.put(ClientConfig.URL, url);
        this.connectionFactory = connectionFactory;
    }

    /**
     * Directly access the configuration.
     *
     * @return the configuration object
     */
    public ClientConfig getConfig() {
        return config;
    }

    /**
     * Configure to use a proxy when connecting to the Salt API.
     *
     * @param settings the proxy settings
     */
    public void setProxy(ProxySettings settings) {
        if (settings.getHostname() != null) {
            config.put(ClientConfig.PROXY_HOSTNAME, settings.getHostname());
            config.put(ClientConfig.PROXY_PORT, settings.getPort());
        }
        if (settings.getUsername() != null) {
            config.put(ClientConfig.PROXY_USERNAME, settings.getUsername());
            if (settings.getPassword() != null) {
                config.put(ClientConfig.PROXY_PASSWORD, settings.getPassword());
            }
        }
    }

    /**
     * Perform login and return the token.
     * <p>
     * {@code POST /login}
     *
     * @param username the username
     * @param password the password
     * @param eauth the eauth type
     * @return the authentication token
     * @throws SaltException if anything goes wrong
     */
    public Token login(final String username, final String password, final AuthModule eauth)
            throws SaltException {
        Map<String, String> props = new LinkedHashMap<>();
        props.put("username", username);
        props.put("password", password);
        props.put("eauth", eauth.getValue());

        String payload = gson.toJson(props);

        Return<List<Token>> result = connectionFactory
                .create("/login", JsonParser.TOKEN, config)
                .getResult(payload);

        // For whatever reason they return a list of tokens here, take the first
        Token token = result.getResult().get(0);
        config.put(ClientConfig.TOKEN, token.getToken());
        return token;
    }

    /**
     * Perform logout and clear the session token from the config.
     * <p>
     * {@code POST /logout}
     *
     * @return true if the logout was successful, otherwise false
     * @throws SaltException if anything goes wrong
     */
    public boolean logout() throws SaltException {
        Return<String> stringResult = connectionFactory
                .create("/logout", JsonParser.STRING, config).getResult("");
        String logoutMessage = "Your token has been cleared";
        boolean result = logoutMessage.equals((stringResult.getResult()));
        if (result) {
            config.remove(ClientConfig.TOKEN);
        }
        return result;
    }

    /**
     * Query statistics from the CherryPy Server.
     * <p>
     * {@code GET /stats}
     *
     * @return the stats
     * @throws SaltException if anything goes wrong
     */
    public Stats stats() throws SaltException {
        return connectionFactory.create("/stats", JsonParser.STATS, config).getResult();
    }

    /**
     * Returns a WebSocket @ClientEndpoint annotated object connected
     * to the /ws ServerEndpoint.
     * <p>
     * The stream object supports the {@link EventStream} interface which allows the caller
     * to register/unregister for stream event notifications as well as close the event
     * stream.
     * <p>
     * Note: {@link SaltClient#login(String, String, AuthModule)} must be called prior
     * to calling this method.
     * <p>
     * {@code GET /events}
     *
     * @param listeners event listeners to be added before the stream is initialized
     * @return the event stream
     * @throws SaltException in case of an error during websocket stream initialization
     */
    public EventStream events(EventListener... listeners) throws SaltException {
        return new EventStream(config, listeners);
    }

    /**
     * Trigger an event in Salt with the specified tag and data.
     * <p>
     * {@code POST /hook}
     *
     * @param eventTag the event tag
     * @param eventData the event data. Must be valid JSON.
     * @return the boolean value returned by Salt. If true the event was triggered
     * successfully.
     * A value of false is returned only if Salt itself returns false; it does not mean a
     * communication failure.
     * @throws SaltException if anything goes wrong
     */
    public boolean sendEvent(String eventTag, String eventData) throws SaltException {
        String tag = eventTag != null ? eventTag : "";
        Map<String, Object> result = connectionFactory
                .create("/hook/" + tag, JsonParser.MAP, config)
                .getResult(eventData);
        return Boolean.TRUE.equals(result.get("success"));
    }

    /**
     * Generic interface to make a {@link Call} to an endpoint using a given {@link Client}.
     *
     * @param <R> the object type that will be returned
     * @param call the call
     * @param client the client to use
     * @param endpoint the endpoint
     * @param custom map of arguments
     * @param type return type as a TypeToken
     * @return the result of the call
     * @throws SaltException if anything goes wrong
     */
    public <R> R call(Call<?> call, Client client, String endpoint, Optional<Map<String,
            Object>> custom, TypeToken<R> type) throws SaltException {
        Map<String, Object> props = new HashMap<>();
        props.putAll(call.getPayload());
        props.put("client", client.getValue());
        custom.ifPresent(props::putAll);

        List<Map<String, Object>> list = Collections.singletonList(props);
        String payload = gson.toJson(list);

        return connectionFactory
                .create(endpoint, new JsonParser<>(type), config)
                .getResult(payload);
    }

    /**
     * Convenience method to make a call without arguments.
     *
     * @param <R> the object type that will be returned
     * @param call the call
     * @param client the client to use
     * @param endpoint the endpoint
     * @param type return type as a TypeToken
     * @return the result of the call
     * @throws SaltException if anything goes wrong
     */
    public <R> R call(Call<?> call, Client client, String endpoint, TypeToken<R> type)
            throws SaltException {
        return call(call, client, endpoint, Optional.empty(), type);
    }
}
