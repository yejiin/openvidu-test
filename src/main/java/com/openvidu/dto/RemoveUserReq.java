package com.openvidu.dto;

import lombok.Data;

@Data
public class RemoveUserReq {
    private String sessionName;
    private String token;
}
