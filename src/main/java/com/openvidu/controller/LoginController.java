package com.openvidu.controller;

import com.openvidu.dto.LoginReq;
import com.openvidu.entity.MyUser;
import com.openvidu.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpSession;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api-login")
public class LoginController {

    private final MemberRepository memberRepository;

    @PostMapping("/login")
    public ResponseEntity login(@RequestBody LoginReq req, HttpSession httpSession) {

        String user = req.getUser();
        String pass = req.getPass();

        log.info("Logging in | {user, pass} = {}, {}", user, pass);

        if (login(user, pass)) {
            // Validate session and return OK
            // Value stored in HttpSession allows us to identify the user in future requests
            httpSession.setAttribute("loggedUser", user);
            return ResponseEntity.ok().build();
        } else { // Wrong user-pass
            // Invalidate session and return error
            httpSession.invalidate();
            return new ResponseEntity("User/Pass incorrect", HttpStatus.UNAUTHORIZED);
        }
    }

    @PostMapping("/logout")
    public ResponseEntity logout(HttpSession session) {
        log.info("' {} has logged out", session.getAttribute("loggedUser"));
        session.invalidate();;
        return ResponseEntity.ok().build();
    }

    private boolean login(String user, String pass) {
        MyUser myUser = memberRepository.find(user);
        return (myUser != null && myUser.getPass().equals(pass));
    }
}
