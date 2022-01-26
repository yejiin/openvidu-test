package com.openvidu.controller;

import com.openvidu.dto.GetTokenReq;
import com.openvidu.dto.GetTokenRes;
import com.openvidu.dto.RemoveUserReq;
import com.openvidu.repository.MemberRepository;
import io.openvidu.java.client.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.simple.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpSession;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping("/api-sessions")
public class SessionController {

    @Autowired
    private MemberRepository memberRepository;

    // OpenVidu object as entrypoint of the SDK
    private OpenVidu openVidu;

    // Collection to pair session names and OpenVidu Session objects
    private Map<String, Session> mapSessions = new ConcurrentHashMap<>();
    // Collection to pair session names and tokens (the inner Map pairs tokens and role associated)
    private Map<String, Map<String, OpenViduRole>> mapSessionNamesTokens = new ConcurrentHashMap<>();

    // URL where our OpenVidu server is listening
    private String OPENVIDU_URL;
    // Secret shared with our OpenVidu server
    private String SECRET;

    public SessionController(@Value("${openvidu.secret") String secret, @Value("${openvidu.url}") String openviduUrl) {
        this.SECRET = secret;
        this.OPENVIDU_URL = openviduUrl;
        this.openVidu = new OpenVidu(OPENVIDU_URL, SECRET);
    }

    @PostMapping("/get-token")
    public ResponseEntity getToken(@RequestBody GetTokenReq req, HttpSession httpSession) {

        try {
            checkUserLogged(httpSession);
        } catch (Exception e) {
            e.printStackTrace();
        }

        log.info("Getting a token from OpenVidu Server | {sessionName}={}", req.getSessionName());

        // The video-call to connect
        String sessionName = req.getSessionName();

        // Role associated to this user
        OpenViduRole role = memberRepository.find((String) httpSession.getAttribute("loggedUser")).getRole();


        // Optional data to be passed to other users then this user connects to the video-call. In this case, a JSON with the value we stored in the HttpSession object on Login
        String serverData = "{\"serverData\": \"" + httpSession.getAttribute("loggedUser") + "\"}";

        // Build connectionProperties object with the serverData and the role
        ConnectionProperties connectionProperties = new ConnectionProperties.Builder()
                .type(ConnectionType.WEBRTC)
                .data(serverData)
                .role(role)
                .build();

        if (this.mapSessions.get(sessionName) != null) {
            // Session already exists
            log.info("Existing session {}", sessionName);

            try {
                // Generated a new Connection with the recently created connectionProperties
                String token = this.mapSessions.get(sessionName).createConnection(connectionProperties).getToken();

                // Update our collection storing the new token
                this.mapSessionNamesTokens.get(sessionName).put(token, role);

                // Return the response to the client
                return ResponseEntity.ok(new GetTokenRes(token));
            } catch (OpenViduJavaClientException e) {
                // If internal error generate an error message and return it to client
                return getErrorResponse(e);
            } catch (OpenViduHttpException e) {
                if (404 == e.getStatus()) {
                    // Invalid sessionId (user left unexpectedly). Session object is not valid
                    // anymore. Clean collections and continue as new session
                    this.mapSessions.remove(sessionName);
                    this.mapSessionNamesTokens.remove(sessionName);
                }
            }
        }
        // New Session
        log.info("New Session {}", sessionName);

        try {
            // Create a new OpenVidu Session
            Session session = this.openVidu.createSession();
            // Generate a new Connection with the recently created connectionProperties
            String token = session.createConnection(connectionProperties).getToken();

            // Store the session and the token in our collections
            this.mapSessions.put(sessionName, session);
            this.mapSessionNamesTokens.put(sessionName, new ConcurrentHashMap<>());
            this.mapSessionNamesTokens.get(sessionName).put(token, role);

            // Return the response to the client
            return ResponseEntity.ok(new GetTokenRes(token));
        } catch (Exception e) {
            // If error generate an error message and return it to client
            return getErrorResponse(e);
        }
    }

    @PostMapping("/remove-user")
    public ResponseEntity removeUser(@RequestBody RemoveUserReq req, HttpSession httpSession) {

        try {
            checkUserLogged(httpSession);
        } catch (Exception e) {
            return getErrorResponse(e);
        }

        log.info("Removing user | {sessionName, token}={}, {}", req.getSessionName(), req.getToken());

        String sessionName = req.getSessionName();
        String token = req.getToken();

        // If the session exists
        if (this.mapSessions.get(sessionName) != null && this.mapSessionNamesTokens.get(sessionName) != null) {

            // If the token exists
            if (this.mapSessionNamesTokens.get(sessionName).remove(token) != null) {
                // User left the session
                if (this.mapSessionNamesTokens.get(sessionName).isEmpty()) {
                    // LAST user left: session must be removed
                    this.mapSessions.remove(sessionName);
                }
                return ResponseEntity.ok().build();
            } else {
                // The TOKEN wasn't valid
                log.info("Problems in the app server: the TOKEN wasn't valid");
                return ResponseEntity.internalServerError().build();
            }
        } else {
            // The SESSION does not exist
            log.info("Problems in the app server: the SESSION does not exist");
            return ResponseEntity.internalServerError().build();
        }
    }

    private ResponseEntity getErrorResponse(Exception e) {
        JSONObject json = new JSONObject();
        json.put("cause", e.getCause());
        json.put("error", e.getMessage());
        json.put("exception", e.getClass());
        return ResponseEntity.internalServerError().body(json);
    }

    private void checkUserLogged(HttpSession httpSession) throws Exception {
        if (httpSession == null || httpSession.getAttribute("loggedUser") == null) {
            throw new Exception("User not logged");
        }
    }
}
