package com.openvidu.entity;

import io.openvidu.java.client.OpenViduRole;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MyUser {

    private String name;
    private String pass;
    private OpenViduRole role;
}
