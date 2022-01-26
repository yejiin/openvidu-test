package com.openvidu.repository;

import com.openvidu.entity.MyUser;
import io.openvidu.java.client.OpenViduRole;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class MemberRepository {

    private static Map<String, MyUser> users = new ConcurrentHashMap<>();

    public MemberRepository() {
        users.put("publisher1", new MyUser("publisher1", "pass", OpenViduRole.PUBLISHER));
        users.put("publisher2", new MyUser("publisher2", "pass", OpenViduRole.PUBLISHER));
        users.put("subscriber", new MyUser("subscriber", "pass", OpenViduRole.SUBSCRIBER));
    }

    public MyUser find(String user) {
        return users.get(user);
    }
}
